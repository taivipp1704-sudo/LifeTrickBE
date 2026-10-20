package vn.tuikhon.controller;

import java.io.IOException;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.tuikhon.model.ApiError;
import vn.tuikhon.repository.Store;
import vn.tuikhon.service.*;

@RestController
@RequestMapping("/api/media")
public class MediaController {
  private final Store db;
  private final Identity identity;
  private final MediaService media;
  private final RateLimitService rate;

  public MediaController(Store db, Identity i, MediaService m, RateLimitService r) {
    this.db = db;
    identity = i;
    media = m;
    rate = r;
  }

  @PostMapping
  Object upload(Authentication a, @RequestParam("file") MultipartFile file) throws IOException {
    String uid = identity.id(a);
    rate.check("upload:" + uid, 15, 3600);
    return media.upload(uid, file);
  }

  @GetMapping("/{id}")
  ResponseEntity<Resource> stream(Authentication a, @PathVariable String id) {
    var m = db.one("SELECT * FROM media_files WHERE id=?", id);
    boolean published =
        db.optional("SELECT id FROM published_tips WHERE media_id=? AND visible=1", id).isPresent();
    if (!published) {
      var u = identity.user(a);
      boolean owner = u.get("id").equals(m.get("owner_id"));
      boolean reviewer = false;
      if (!owner && java.util.Set.of("ADMIN", "REVIEWER").contains(u.get("role")))
        reviewer =
            db.optional(
                    "SELECT id FROM contributions WHERE media_id=? AND (status IN"
                        + " ('SUBMITTED','IN_REVIEW','APPROVED','REJECTED') OR reviewer_id=?)",
                    id,
                    u.get("id"))
                .isPresent();
      if (!owner && !reviewer) throw ApiError.missing();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) m.get("mime_type")))
        .header(
            "Content-Disposition",
            "inline; filename=\""
                + id
                + (((String) m.get("mime_type")).startsWith("video") ? ".mp4" : ".jpg")
                + "\"")
        .header("X-Content-Type-Options", "nosniff")
        .body(new FileSystemResource(media.path(m)));
  }
}
