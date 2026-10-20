package vn.tuikhon.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.tuikhon.model.ApiError;
import vn.tuikhon.repository.Store;

@Service
public class Identity {
  private final Store db;

  public Identity(Store db) {
    this.db = db;
  }

  public Map<String, Object> user(Authentication auth) {
    if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser"))
      throw new ApiError(401, "Bạn cần đăng nhập.");
    return db.optional("SELECT * FROM app_users WHERE email=? AND active=1", auth.getName())
        .orElseThrow(() -> new ApiError(401, "Phiên đăng nhập không còn hợp lệ."));
  }

  public String id(Authentication a) {
    return (String) user(a).get("id");
  }

  public Map<String, Object> reviewer(Authentication a) {
    var u = user(a);
    if (!ListRoles.REVIEW.contains(u.get("role")))
      throw new ApiError(403, "Cần quyền thẩm định nội dung.");
    return u;
  }

  public Map<String, Object> admin(Authentication a) {
    var u = user(a);
    if (!"ADMIN".equals(u.get("role"))) throw new ApiError(403, "Cần quyền quản trị.");
    return u;
  }

  public static void validatePassword(String p) {
    if (p.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ApiError(400, "Mật khẩu vượt giới hạn 72 byte UTF-8.");
  }

  private static final class ListRoles {
    static final java.util.Set<String> REVIEW = java.util.Set.of("REVIEWER", "ADMIN");
  }
}
