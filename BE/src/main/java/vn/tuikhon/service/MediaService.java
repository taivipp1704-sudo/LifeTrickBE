package vn.tuikhon.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.tuikhon.model.ApiError;
import vn.tuikhon.repository.Store;

@Service
public class MediaService {
  private final java.util.concurrent.Semaphore processing =
      new java.util.concurrent.Semaphore(2, true);
  private final Store db;
  private final Path root;
  private final String ffmpeg, ffprobe;

  public MediaService(
      Store db,
      @Value("${app.media-root}") String root,
      @Value("${app.ffmpeg}") String ffmpeg,
      @Value("${app.ffprobe}") String ffprobe)
      throws IOException {
    this.db = db;
    this.root = Path.of(root).toAbsolutePath().normalize();
    this.ffmpeg = ffmpeg;
    this.ffprobe = ffprobe;
    Files.createDirectories(this.root);
    if (Files.isSymbolicLink(this.root))
      throw new IllegalStateException("Media root cannot be a symlink");
  }

  public Map<String, Object> upload(String uid, MultipartFile file) throws IOException {
    if (file.isEmpty() || file.getSize() > 30L * 1024 * 1024)
      throw new ApiError(400, "Tệp phải có dữ liệu và không vượt 30 MB.");
    if (!processing.tryAcquire())
      throw new ApiError(429, "Máy chủ đang xử lý tệp khác. Hãy thử lại sau.");
    String id = Store.id();
    Path incoming = root.resolve(id + ".upload"), output = null;
    boolean persisted = false;
    try {
      file.transferTo(incoming);
      byte[] head = new byte[16];
      try (var in = Files.newInputStream(incoming)) {
        in.read(head);
      }
      String mime;
      Double duration = null;
      if ((head[0] & 255) == 0xff && (head[1] & 255) == 0xd8
          || head[0] == (byte) 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
        output = root.resolve(id + ".jpg");
        try (var input = ImageIO.createImageInputStream(incoming.toFile())) {
          Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
          if (!readers.hasNext()) throw new ApiError(400, "Ảnh không đọc được.");
          var reader = readers.next();
          try {
            reader.setInput(input);
            int w = reader.getWidth(0), h = reader.getHeight(0);
            if (w < 1 || h < 1 || (long) w * h > 20_000_000)
              throw new ApiError(400, "Ảnh vượt giới hạn 20 megapixel.");
            BufferedImage source = reader.read(0),
                clean = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            var g = clean.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(source, 0, 0, null);
            g.dispose();
            if (!ImageIO.write(clean, "jpg", output.toFile()))
              throw new IOException("Image encoder unavailable");
          } finally {
            reader.dispose();
          }
        }
        mime = "image/jpeg";
      } else if (new String(head, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp")
          || (head[0] & 255) == 0x1a
              && (head[1] & 255) == 0x45
              && (head[2] & 255) == 0xdf
              && (head[3] & 255) == 0xa3) {
        var probe = probe(incoming);
        duration = probe.path("format").path("duration").asDouble(-1);
        if (duration <= 0 || duration > 60.05)
          throw new ApiError(400, "Video phải có thời lượng từ hơn 0 đến tối đa 60 giây.");
        boolean hasVideo = false;
        for (var stream : probe.path("streams")) {
          String type = stream.path("codec_type").asText();
          if (!Set.of("video", "audio").contains(type))
            throw new ApiError(400, "Video chứa luồng dữ liệu không được hỗ trợ.");
          if (type.equals("video")) {
            hasVideo = true;
            long w = stream.path("width").asLong(), h = stream.path("height").asLong();
            if (w * h > 8_294_400 || w < 1 || h < 1)
              throw new ApiError(400, "Video vượt giới hạn độ phân giải 4K.");
          }
        }
        if (!hasVideo) throw new ApiError(400, "Tệp không có hình ảnh video.");
        output = root.resolve(id + ".mp4");
        run(
            List.of(
                ffmpeg,
                "-nostdin",
                "-v",
                "error",
                "-i",
                incoming.toString(),
                "-map",
                "0:v:0",
                "-map",
                "0:a:0?",
                "-sn",
                "-dn",
                "-map_metadata",
                "-1",
                "-vf",
                "scale='min(1280,iw)':-2",
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-crf",
                "25",
                "-maxrate",
                "2500k",
                "-bufsize",
                "5000k",
                "-pix_fmt",
                "yuv420p",
                "-c:a",
                "aac",
                "-b:a",
                "96k",
                "-movflags",
                "+faststart",
                output.toString()),
            120);
        mime = "video/mp4";
      } else
        throw new ApiError(
            400,
            "Chỉ nhận ảnh JPEG/PNG hoặc video MP4/WebM hợp lệ. Không nhận SVG hoặc tệp thực thi.");
      if (Files.size(output) > 30L * 1024 * 1024)
        throw new ApiError(400, "Tệp sau xử lý vượt giới hạn 30 MB.");
      String sha;
      try (var in = Files.newInputStream(output)) {
        var digest = MessageDigest.getInstance("SHA-256");
        in.transferTo(
            new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest));
        sha = HexFormat.of().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
      db.jdbc()
          .update(
              "INSERT INTO"
                  + " media_files(id,owner_id,storage_name,mime_type,size_bytes,sha256,duration_seconds)"
                  + " VALUES(?,?,?,?,?,?,?)",
              id,
              uid,
              output.getFileName().toString(),
              mime,
              Files.size(output),
              sha,
              duration);
      persisted = true;
      db.audit(uid, "MEDIA_UPLOADED", id);
      return Map.of(
          "id",
          id,
          "mime_type",
          mime,
          "size_bytes",
          Files.size(output),
          "duration_seconds",
          duration == null ? 0 : duration);
    } finally {
      try {
        Files.deleteIfExists(incoming);
        if (!persisted && output != null) Files.deleteIfExists(output);
      } finally {
        processing.release();
      }
    }
  }

  private com.fasterxml.jackson.databind.JsonNode probe(Path file) throws IOException {
    String output =
        run(
            List.of(
                ffprobe,
                "-v",
                "error",
                "-show_format",
                "-show_streams",
                "-of",
                "json",
                file.toString()),
            20);
    try {
      return db.json().readTree(output);
    } catch (Exception e) {
      throw new ApiError(400, "Không đọc được cấu trúc video.");
    }
  }

  private String run(List<String> command, int seconds) throws IOException {
    Path log = Files.createTempFile(root, "process-", ".log");
    try {
      Process p;
      try {
        p =
            new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
      } catch (IOException e) {
        throw new ApiError(503, "Máy chủ chưa sẵn sàng xử lý video (FFmpeg/FFprobe).");
      }
      boolean finished;
      try {
        finished = p.waitFor(seconds, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        p.destroyForcibly();
        Thread.currentThread().interrupt();
        throw new ApiError(503, "Tác vụ xử lý bị gián đoạn.");
      }
      if (!finished) {
        p.destroyForcibly();
        throw new ApiError(400, "Video xử lý quá thời gian cho phép.");
      }
      if (p.exitValue() != 0) throw new ApiError(400, "Video lỗi hoặc định dạng không hỗ trợ.");
      if (Files.size(log) > 2_000_000)
        throw new ApiError(400, "Video chứa quá nhiều thông tin luồng.");
      return Files.readString(log);
    } finally {
      Files.deleteIfExists(log);
    }
  }

  public Path path(Map<String, Object> media) {
    String name = (String) media.get("storage_name");
    if (!name.matches("[a-f0-9-]{36}\\.(mp4|jpg)")) throw ApiError.missing();
    Path p = root.resolve(name).normalize();
    if (!p.getParent().equals(root) || Files.isSymbolicLink(p) || !Files.isRegularFile(p))
      throw ApiError.missing();
    return p;
  }
}
