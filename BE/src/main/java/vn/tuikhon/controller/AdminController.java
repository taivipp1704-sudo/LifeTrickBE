package vn.tuikhon.controller;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.tuikhon.model.*;
import vn.tuikhon.repository.Store;
import vn.tuikhon.service.Identity;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final Store db;
  private final Identity identity;

  public AdminController(Store d, Identity i) {
    db = d;
    identity = i;
  }

  @GetMapping("/users")
  Object users(Authentication a) {
    identity.admin(a);
    return db.rows(
        "SELECT TOP(100) id,email,display_name,role,active,created_at FROM app_users ORDER BY"
            + " created_at DESC");
  }

  @PatchMapping("/users/{id}")
  @Transactional
  Object role(
      Authentication a, @PathVariable String id, @Valid @RequestBody Contracts.RoleChange r) {
    var actor = identity.admin(a);
    if (actor.get("id").equals(id))
      throw new ApiError(
          400, "Không thể tự thay đổi vai trò hoặc khóa tài khoản quản trị đang dùng.");
    var u = db.one("SELECT * FROM app_users WHERE id=?", id);
    db.jdbc().update("UPDATE app_users SET role=?,active=? WHERE id=?", r.role(), r.active(), id);
    db.revokeSessions((String) u.get("email"));
    db.audit((String) actor.get("id"), "ACCOUNT_ROLE_CHANGED", id);
    return db.publicUser(db.one("SELECT * FROM app_users WHERE id=?", id));
  }

  @GetMapping("/audit")
  Object audit(Authentication a) {
    identity.admin(a);
    return db.rows(
        "SELECT TOP(200) e.*,u.display_name AS actor_name FROM audit_events e LEFT JOIN app_users u"
            + " ON u.id=e.actor_id ORDER BY e.created_at DESC");
  }

  @GetMapping("/feedback")
  Object feedback(Authentication a) {
    identity.admin(a);
    return db.rows(
        "SELECT TOP(100) f.*,u.display_name AS author_name FROM feedback f JOIN app_users u ON"
            + " u.id=f.user_id ORDER BY f.created_at DESC");
  }

  @GetMapping("/tips")
  Object published(Authentication a) {
    identity.admin(a);
    return db.expand(db.rows("SELECT TOP(100) * FROM published_tips ORDER BY published_at DESC"));
  }

  @PostMapping("/tips/{id}/unpublish")
  @Transactional
  Object withdraw(
      Authentication a, @PathVariable String id, @Valid @RequestBody Contracts.Feedback reason) {
    String uid = (String) identity.admin(a).get("id");
    db.one("SELECT id FROM published_tips WHERE id=?", id);
    db.jdbc().update("UPDATE published_tips SET visible=0 WHERE id=?", id);
    db.jdbc()
        .update(
            "INSERT INTO moderation_actions(id,tip_id,actor_id,action,reason)"
                + " VALUES(?,?,?,'UNPUBLISH',?)",
            Store.id(),
            id,
            uid,
            reason.text());
    db.audit(uid, "TIP_UNPUBLISHED", id);
    return Map.of("message", "Đã gỡ bài khỏi thư viện công khai.");
  }
}
