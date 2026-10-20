package vn.tuikhon.config;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.tuikhon.repository.Store;
import vn.tuikhon.service.Identity;

@Component
public class Bootstrap implements ApplicationRunner {
  private final Store db;
  private final PasswordEncoder p;
  private final String email, password;
  private final boolean seed;

  public Bootstrap(
      Store db,
      PasswordEncoder p,
      @Value("${app.bootstrap-email}") String email,
      @Value("${app.bootstrap-password}") String password,
      @Value("${app.seed}") boolean seed) {
    this.db = db;
    this.p = p;
    this.email = email;
    this.password = password;
    this.seed = seed;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (db.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE role='ADMIN' AND active=1", Integer.class)
        == 0) {
      if (email.isBlank() || password.length() < 16)
        throw new IllegalStateException(
            "Configure bootstrap admin credentials (16+ characters) for the first start");
      Identity.validatePassword(password);
      if (db.optional("SELECT id FROM app_users WHERE email=?", email.toLowerCase(Locale.ROOT))
          .isPresent())
        throw new IllegalStateException(
            "Bootstrap email already belongs to an account; manual admin recovery required");
      String id = Store.id();
      db.jdbc()
          .update(
              "INSERT INTO app_users(id,email,display_name,password_hash,role,active)"
                  + " VALUES(?,?,?,?, 'ADMIN',1)",
              id,
              email.toLowerCase(Locale.ROOT),
              "Quản trị Túi Khôn",
              p.encode(password));
      db.audit(id, "ADMIN_BOOTSTRAPPED", id);
    }
    if (seed) {
      try (var input = getClass().getResourceAsStream("/seed-tips.json")) {
        if (input != null) {
          var list = db.json().readTree(input);
          for (var t : list)
            if (db.optional("SELECT id FROM published_tips WHERE id=?", t.path("id").asText())
                .isEmpty())
              db.jdbc()
                  .update(
                      "INSERT INTO published_tips(id,payload,verification)"
                          + " VALUES(?,?,'REFERENCE_GUIDE')",
                      t.path("id").asText(),
                      t.path("content").toString());
        }
      }
    }
  }
}
