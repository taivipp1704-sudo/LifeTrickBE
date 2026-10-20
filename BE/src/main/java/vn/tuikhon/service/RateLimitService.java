package vn.tuikhon.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import vn.tuikhon.model.ApiError;
import vn.tuikhon.repository.Store;

@Service
public class RateLimitService {
  private final Store db;

  public RateLimitService(Store db) {
    this.db = db;
  }

  public void check(String key, int max, int seconds) {
    try {
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(key.getBytes(StandardCharsets.UTF_8)));
      var now = Timestamp.from(Instant.now());
      int changed =
          db.jdbc()
              .update(
                  "UPDATE rate_buckets SET hits=CASE WHEN expires_at < ? THEN 1 ELSE hits+1"
                      + " END,expires_at=CASE WHEN expires_at < ? THEN ? ELSE expires_at END WHERE"
                      + " bucket_key=?",
                  now,
                  now,
                  Timestamp.from(Instant.now().plusSeconds(seconds)),
                  hash);
      if (changed == 0) {
        try {
          db.jdbc()
              .update(
                  "INSERT INTO rate_buckets(bucket_key,hits,expires_at) VALUES(?,1,?)",
                  hash,
                  Timestamp.from(Instant.now().plusSeconds(seconds)));
        } catch (org.springframework.dao.DuplicateKeyException e) {
          db.jdbc().update("UPDATE rate_buckets SET hits=hits+1 WHERE bucket_key=?", hash);
        }
      }
      int count =
          db.jdbc()
              .queryForObject(
                  "SELECT hits FROM rate_buckets WHERE bucket_key=?", Integer.class, hash);
      if (count > max) throw new ApiError(429, "Bạn thao tác quá nhanh. Hãy thử lại sau.");
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
