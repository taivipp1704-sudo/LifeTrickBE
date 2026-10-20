package vn.tuikhon.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.tuikhon.model.ApiError;

@Repository
public class Store {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcTemplate jdbc() {
    return jdbc;
  }

  public ObjectMapper json() {
    return json;
  }

  public Store(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public Map<String, Object> one(String sql, Object... args) {
    var list = rows(sql, args);
    if (list.isEmpty()) throw ApiError.missing();
    return list.getFirst();
  }

  public Optional<Map<String, Object>> optional(String sql, Object... args) {
    return rows(sql, args).stream().findFirst();
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.query(
        sql,
        (rs, n) -> {
          var m = new LinkedHashMap<String, Object>();
          var md = rs.getMetaData();
          for (int i = 1; i <= md.getColumnCount(); i++) {
            Object v = rs.getObject(i);
            if (v instanceof Timestamp t) v = t.toInstant().toString();
            m.put(md.getColumnLabel(i), v);
          }
          return m;
        },
        args);
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public Object decode(String value) {
    try {
      return json.readValue(value, Object.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public List<Map<String, Object>> expand(List<Map<String, Object>> list) {
    for (var m : list) if (m.get("payload") instanceof String s) m.put("content", decode(s));
    list.forEach(m -> m.remove("payload"));
    return list;
  }

  public Map<String, Object> expand(Map<String, Object> row) {
    return expand(new ArrayList<>(List.of(row))).getFirst();
  }

  public void audit(String actor, String action, String target) {
    jdbc.update(
        "INSERT INTO audit_events(id,actor_id,action,target_id,request_id) VALUES(?,?,?,?,?)",
        id(),
        actor,
        action,
        target,
        org.slf4j.MDC.get("requestId"));
  }

  public Map<String, Object> publicUser(Map<String, Object> u) {
    var result = new LinkedHashMap<String, Object>();
    for (String k : List.of("id", "email", "display_name", "role", "active", "created_at"))
      result.put(k, u.get(k));
    return result;
  }

  public void revokeSessions(String email) {
    jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", email);
  }
}
