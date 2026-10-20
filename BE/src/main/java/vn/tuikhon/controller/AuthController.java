package vn.tuikhon.controller;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.tuikhon.model.*;
import vn.tuikhon.repository.Store;
import vn.tuikhon.service.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final Store db;
  private final PasswordEncoder passwords;
  private final SecurityContextRepository contexts;
  private final Identity identity;
  private final RateLimitService limit;
  private final String dummy;

  public AuthController(
      Store db, PasswordEncoder p, SecurityContextRepository c, Identity i, RateLimitService l) {
    this.db = db;
    passwords = p;
    contexts = c;
    identity = i;
    limit = l;
    dummy = p.encode(Store.id());
  }

  private static String clientIp(HttpServletRequest req) {
    String ip = req.getHeader("X-Client-IP");
    return ip == null ? req.getRemoteAddr() : ip;
  }

  @GetMapping("/csrf")
  Object csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/register")
  @Transactional
  ResponseEntity<?> register(@Valid @RequestBody Contracts.Register r, HttpServletRequest req) {
    limit.check("register:" + clientIp(req), 15, 3600);
    Identity.validatePassword(r.password());
    String email = r.email().trim().toLowerCase(Locale.ROOT), id = Store.id();
    db.jdbc()
        .update(
            "INSERT INTO app_users(id,email,display_name,password_hash,role,active) VALUES(?,?,?,?,"
                + " 'MEMBER',1)",
            id,
            email,
            r.name().trim(),
            passwords.encode(r.password()));
    db.audit(id, "ACCOUNT_REGISTERED", id);
    return ResponseEntity.status(201)
        .body(Map.of("message", "Đã tạo tài khoản. Bạn có thể đăng nhập."));
  }

  @PostMapping("/login")
  Object login(
      @Valid @RequestBody Contracts.Login r, HttpServletRequest req, HttpServletResponse res) {
    String email = r.email().trim().toLowerCase(Locale.ROOT);
    limit.check("login-email:" + email, 10, 900);
    limit.check("login-ip:" + clientIp(req), 100, 900);
    Identity.validatePassword(r.password());
    var user = db.optional("SELECT * FROM app_users WHERE email=?", email);
    boolean valid =
        passwords.matches(
            r.password(), user.map(u -> (String) u.get("password_hash")).orElse(dummy));
    if (!valid || user.isEmpty() || !Boolean.TRUE.equals(user.get().get("active"))) {
      db.audit(null, "LOGIN_FAILED", null);
      throw new ApiError(401, "Email hoặc mật khẩu không đúng.");
    }
    var u = user.get();
    if (req.getSession(false) != null) req.getSession(false).invalidate();
    req.getSession(true);
    req.changeSessionId();
    var principal =
        org.springframework.security.core.userdetails.User.withUsername(email)
            .password("")
            .roles((String) u.get("role"))
            .build();
    var auth =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.get("role"))));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, req, res);
    db.audit((String) u.get("id"), "LOGIN_SUCCESS", (String) u.get("id"));
    return db.publicUser(u);
  }

  @GetMapping("/me")
  Object me(Authentication a) {
    return db.publicUser(identity.user(a));
  }

  @PostMapping("/logout")
  Object logout(Authentication a, HttpServletRequest req) {
    String id = identity.id(a);
    if (req.getSession(false) != null) req.getSession(false).invalidate();
    SecurityContextHolder.clearContext();
    db.audit(id, "LOGOUT", id);
    return Map.of("message", "Đã đăng xuất.");
  }

  @PatchMapping("/profile")
  Object profile(Authentication a, @Valid @RequestBody Contracts.Profile p) {
    var u = identity.user(a);
    db.jdbc()
        .update("UPDATE app_users SET display_name=? WHERE id=?", p.name().trim(), u.get("id"));
    db.audit((String) u.get("id"), "PROFILE_UPDATED", (String) u.get("id"));
    return db.publicUser(identity.user(a));
  }

  @PostMapping("/password")
  Object password(Authentication a, @Valid @RequestBody Contracts.PasswordChange p) {
    var u = identity.user(a);
    limit.check("password:" + u.get("id"), 5, 900);
    Identity.validatePassword(p.newPassword());
    if (!passwords.matches(p.oldPassword(), (String) u.get("password_hash")))
      throw new ApiError(400, "Mật khẩu hiện tại không đúng.");
    db.jdbc()
        .update(
            "UPDATE app_users SET password_hash=? WHERE id=?",
            passwords.encode(p.newPassword()),
            u.get("id"));
    db.revokeSessions((String) u.get("email"));
    db.audit((String) u.get("id"), "PASSWORD_CHANGED", (String) u.get("id"));
    return Map.of("message", "Đã đổi mật khẩu và thu hồi các phiên. Hãy đăng nhập lại.");
  }
}
