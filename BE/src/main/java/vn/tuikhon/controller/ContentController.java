package vn.tuikhon.controller;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.tuikhon.model.*;
import vn.tuikhon.repository.Store;
import vn.tuikhon.service.*;

@RestController
@RequestMapping("/api")
public class ContentController {
  private final Store db;
  private final Identity identity;
  private final ContributionService service;
  private final RateLimitService rate;

  public ContentController(Store db, Identity i, ContributionService s, RateLimitService r) {
    this.db = db;
    identity = i;
    service = s;
    rate = r;
  }

  @GetMapping("/tips")
  Object tips(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
    if (page < 0 || page > 10000 || size < 1 || size > 100)
      throw new ApiError(400, "Phân trang không hợp lệ.");
    return Map.of(
        "items",
        db.expand(
            db.rows(
                "SELECT p.*,m.mime_type AS media_type,u.display_name AS reviewer_name FROM"
                    + " published_tips p LEFT JOIN media_files m ON m.id=p.media_id LEFT JOIN"
                    + " app_users u ON u.id=p.reviewer_id WHERE p.visible=1 ORDER BY published_at"
                    + " DESC,p.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                page * size,
                size)),
        "total",
        db.jdbc()
            .queryForObject("SELECT COUNT(*) FROM published_tips WHERE visible=1", Integer.class));
  }

  @GetMapping("/tips/{id}")
  Object tip(@PathVariable String id) {
    return db.expand(
        db.one(
            "SELECT p.*,m.mime_type AS media_type,u.display_name AS reviewer_name FROM"
                + " published_tips p LEFT JOIN media_files m ON m.id=p.media_id LEFT JOIN app_users"
                + " u ON u.id=p.reviewer_id WHERE p.id=? AND p.visible=1",
            id));
  }

  @GetMapping("/contributions")
  Object mine(Authentication a) {
    return db.expand(
        db.rows(
            "SELECT * FROM contributions WHERE author_id=? ORDER BY updated_at DESC",
            identity.id(a)));
  }

  @PostMapping("/contributions")
  Object create(Authentication a, @Valid @RequestBody Contracts.Draft d) {
    String uid = identity.id(a);
    rate.check("draft:" + uid, 30, 3600);
    return service.create(uid, d);
  }

  @PutMapping("/contributions/{id}")
  Object update(Authentication a, @PathVariable String id, @Valid @RequestBody Contracts.Draft d) {
    return service.update(identity.id(a), id, d);
  }

  @PostMapping("/contributions/{id}/submit")
  Object submit(
      Authentication a, @PathVariable String id, @Valid @RequestBody Contracts.Revision r) {
    return service.submit(identity.id(a), id, r.version());
  }

  @GetMapping("/contributions/{id}")
  Object detail(Authentication a, @PathVariable String id) {
    var u = identity.user(a);
    var c = service.get(id);
    if (!u.get("id").equals(c.get("author_id"))) {
      if (!java.util.Set.of("ADMIN", "REVIEWER").contains(u.get("role"))) throw ApiError.missing();
      identity.reviewer(a);
      if (List.of("DRAFT", "CHANGES_REQUESTED").contains(c.get("status"))
          && !u.get("id").equals(c.get("reviewer_id"))) throw ApiError.missing();
    }
    var out = db.expand(c);
    out.put(
        "reviews",
        db.rows(
            "SELECT"
                + " d.id,d.decision,d.notes,d.evidence,d.science_checked,d.safety_checked,d.materials_checked,d.created_at,u.display_name"
                + " AS reviewer_name FROM review_decisions d JOIN app_users u ON u.id=d.reviewer_id"
                + " WHERE contribution_id=? ORDER BY d.created_at",
            id));
    return out;
  }

  @GetMapping("/reviews")
  Object queue(Authentication a) {
    var u = identity.reviewer(a);
    return db.expand(
        db.rows(
            "SELECT c.*,u.display_name AS author_name,r.display_name AS reviewer_name FROM"
                + " contributions c JOIN app_users u ON u.id=c.author_id LEFT JOIN app_users r ON"
                + " r.id=c.reviewer_id WHERE c.status IN ('SUBMITTED','IN_REVIEW') AND"
                + " c.author_id<>? ORDER BY c.updated_at",
            u.get("id")));
  }

  @PostMapping("/reviews/{id}/claim")
  Object claim(
      Authentication a, @PathVariable String id, @Valid @RequestBody Contracts.Revision r) {
    return service.claim((String) identity.reviewer(a).get("id"), id, r.version());
  }

  @PostMapping("/reviews/{id}/decision")
  Object decide(
      Authentication a, @PathVariable String id, @Valid @RequestBody Contracts.Decision r) {
    return service.decide((String) identity.reviewer(a).get("id"), id, r);
  }

  @GetMapping("/notebook")
  Object notebook(Authentication a) {
    String uid = identity.id(a);
    return Map.of(
        "saved",
        db.rows("SELECT tip_id FROM bookmarks WHERE user_id=?", uid).stream()
            .map(x -> x.get("tip_id"))
            .toList(),
        "completed",
        db
            .rows("SELECT tip_id FROM learning_progress WHERE user_id=? AND completed=1", uid)
            .stream()
            .map(x -> x.get("tip_id"))
            .toList());
  }

  @PutMapping("/notebook/{id}/bookmark")
  Object bookmark(Authentication a, @PathVariable String id) {
    String uid = identity.id(a);
    db.one("SELECT id FROM published_tips WHERE id=?", id);
    if (db.optional("SELECT tip_id FROM bookmarks WHERE user_id=? AND tip_id=?", uid, id)
        .isEmpty()) {
      try {
        db.jdbc().update("INSERT INTO bookmarks(user_id,tip_id) VALUES(?,?)", uid, id);
      } catch (org.springframework.dao.DuplicateKeyException ignored) {
      }
    }
    return Map.of("saved", true);
  }

  @DeleteMapping("/notebook/{id}/bookmark")
  Object unsave(Authentication a, @PathVariable String id) {
    db.jdbc().update("DELETE FROM bookmarks WHERE user_id=? AND tip_id=?", identity.id(a), id);
    return Map.of("saved", false);
  }

  @PutMapping("/notebook/{id}/progress")
  Object progress(
      Authentication a, @PathVariable String id, @Valid @RequestBody Contracts.Progress p) {
    String uid = identity.id(a);
    db.one("SELECT id FROM published_tips WHERE id=?", id);
    int n =
        db.jdbc()
            .update(
                "UPDATE learning_progress SET completed=?,updated_at=SYSUTCDATETIME() WHERE"
                    + " user_id=? AND tip_id=?",
                p.completed(),
                uid,
                id);
    if (n == 0) {
      try {
        db.jdbc()
            .update(
                "INSERT INTO learning_progress(user_id,tip_id,completed) VALUES(?,?,?)",
                uid,
                id,
                p.completed());
      } catch (org.springframework.dao.DuplicateKeyException e) {
        db.jdbc()
            .update(
                "UPDATE learning_progress SET completed=? WHERE user_id=? AND tip_id=?",
                p.completed(),
                uid,
                id);
      }
    }
    return Map.of("completed", p.completed());
  }

  @PostMapping("/tips/{id}/feedback")
  Object feedback(
      Authentication a, @PathVariable String id, @Valid @RequestBody Contracts.Feedback f) {
    String uid = identity.id(a);
    rate.check("feedback:" + uid, 20, 3600);
    db.one("SELECT id FROM published_tips WHERE id=?", id);
    db.jdbc()
        .update(
            "INSERT INTO feedback(id,user_id,tip_id,body) VALUES(?,?,?,?)",
            Store.id(),
            uid,
            id,
            f.text());
    return Map.of("message", "Đã gửi góp ý đến máy chủ.");
  }
}
