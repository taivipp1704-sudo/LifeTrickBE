package vn.tuikhon.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tuikhon.model.*;
import vn.tuikhon.repository.Store;

@Service
public class ContributionService {
  private final Store db;

  public ContributionService(Store db) {
    this.db = db;
  }

  public Map<String, Object> get(String id) {
    return db.one(
        "SELECT c.*,u.display_name AS author_name,r.display_name AS reviewer_name,m.mime_type AS"
            + " media_type FROM contributions c LEFT JOIN media_files m ON m.id=c.media_id JOIN"
            + " app_users u ON u.id=c.author_id LEFT JOIN app_users r ON r.id=c.reviewer_id WHERE"
            + " c.id=?",
        id);
  }

  private void validateSubmission(String payload) {
    try {
      var c = db.json().readValue(payload, Contracts.Content.class);
      if (c.title().trim().length() < 5
          || c.description().isBlank()
          || c.tools().isEmpty()
          || c.warning().trim().length() < 10
          || c.sourceTitle().isBlank()
          || !c.sourceUrl().startsWith("https://")
          || c.steps().stream().anyMatch(s -> s.title().isBlank() || s.body().isBlank()))
        throw new ApiError(
            400, "Cần đủ tên, mô tả, dụng cụ, ba bước, cảnh báo và nguồn HTTPS trước khi gửi.");
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new ApiError(400, "Bản nháp không hợp lệ.");
    }
  }

  public void own(Map<String, Object> c, String uid) {
    if (!uid.equals(c.get("author_id"))) throw ApiError.missing();
  }

  public void editable(Map<String, Object> c) {
    if (!List.of("DRAFT", "CHANGES_REQUESTED").contains(c.get("status")))
      throw new ApiError(
          409, "Bài đã gửi không thể sửa. Hãy chờ phản hồi hoặc tạo bản đóng góp mới.");
  }

  private void media(String id, String owner) {
    if (id != null
        && !db.optional("SELECT id FROM media_files WHERE id=? AND owner_id=?", id, owner)
            .isPresent()) throw new ApiError(400, "Tệp minh họa không thuộc tài khoản của bạn.");
  }

  @Transactional
  public Object create(String uid, Contracts.Draft d) {
    media(d.mediaId(), uid);
    String id = Store.id();
    db.jdbc()
        .update(
            "INSERT INTO contributions(id,author_id,status,payload,media_id)"
                + " VALUES(?,?,'DRAFT',?,?)",
            id,
            uid,
            db.encode(d.content()),
            d.mediaId());
    db.audit(uid, "DRAFT_CREATED", id);
    return db.expand(get(id));
  }

  @Transactional
  public Object update(String uid, String id, Contracts.Draft d) {
    var c = get(id);
    own(c, uid);
    editable(c);
    media(d.mediaId(), uid);
    int n =
        db.jdbc()
            .update(
                "UPDATE contributions SET"
                    + " payload=?,media_id=?,version=version+1,updated_at=SYSUTCDATETIME() WHERE"
                    + " id=? AND version=? AND status IN ('DRAFT','CHANGES_REQUESTED')",
                db.encode(d.content()),
                d.mediaId(),
                id,
                d.version());
    if (n != 1) throw ApiError.conflict();
    db.audit(uid, "DRAFT_UPDATED", id);
    return db.expand(get(id));
  }

  @Transactional
  public Object submit(String uid, String id, int version) {
    var c = get(id);
    own(c, uid);
    editable(c);
    validateSubmission((String) c.get("payload"));
    if (c.get("media_id") == null)
      throw new ApiError(400, "Cần tải ảnh hoặc video minh họa trước khi gửi thẩm định.");
    int n =
        db.jdbc()
            .update(
                "UPDATE contributions SET"
                    + " status='SUBMITTED',reviewer_id=NULL,version=version+1,updated_at=SYSUTCDATETIME()"
                    + " WHERE id=? AND version=? AND status IN ('DRAFT','CHANGES_REQUESTED')",
                id,
                version);
    if (n != 1) throw ApiError.conflict();
    db.audit(uid, "CONTRIBUTION_SUBMITTED", id);
    return db.expand(get(id));
  }

  @Transactional
  public Object claim(String uid, String id, int version) {
    var c = get(id);
    if (uid.equals(c.get("author_id")))
      throw new ApiError(403, "Bạn không thể thẩm định bài của chính mình.");
    int n =
        db.jdbc()
            .update(
                "UPDATE contributions SET"
                    + " status='IN_REVIEW',reviewer_id=?,version=version+1,updated_at=SYSUTCDATETIME()"
                    + " WHERE id=? AND status='SUBMITTED' AND version=?",
                uid,
                id,
                version);
    if (n != 1) throw ApiError.conflict();
    db.audit(uid, "REVIEW_CLAIMED", id);
    return db.expand(get(id));
  }

  @Transactional
  public Object decide(String uid, String id, Contracts.Decision d) {
    var c = get(id);
    if (uid.equals(c.get("author_id")) || !uid.equals(c.get("reviewer_id")))
      throw new ApiError(
          403, "Chỉ người nhận thẩm định được ghi quyết định, không được tự duyệt bài.");
    if (!"IN_REVIEW".equals(c.get("status"))) throw ApiError.conflict();
    if ("APPROVE".equals(d.decision())
        && (!d.scienceChecked() || !d.safetyChecked() || !d.materialsChecked()))
      throw new ApiError(
          400, "Cần xác nhận đủ kiểm chứng khoa học, an toàn và dụng cụ trước khi phê duyệt.");
    String status =
        switch (d.decision()) {
          case "APPROVE" -> "APPROVED";
          case "REQUEST_CHANGES" -> "CHANGES_REQUESTED";
          default -> "REJECTED";
        };
    int n =
        db.jdbc()
            .update(
                "UPDATE contributions SET status=?,version=version+1,updated_at=SYSUTCDATETIME()"
                    + " WHERE id=? AND version=? AND status='IN_REVIEW' AND reviewer_id=?",
                status,
                id,
                d.version(),
                uid);
    if (n != 1) throw ApiError.conflict();
    db.jdbc()
        .update(
            "INSERT INTO"
                + " review_decisions(id,contribution_id,reviewer_id,decision,notes,evidence,science_checked,safety_checked,materials_checked,payload_snapshot)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?)",
            Store.id(),
            id,
            uid,
            d.decision(),
            d.notes(),
            d.evidence(),
            d.scienceChecked(),
            d.safetyChecked(),
            d.materialsChecked(),
            c.get("payload"));
    if ("APPROVED".equals(status)) {
      db.jdbc()
          .update(
              "INSERT INTO"
                  + " published_tips(id,payload,media_id,contribution_id,reviewer_id,verification)"
                  + " VALUES(?,?,?,?,?,'HUMAN_REVIEWED')",
              id,
              c.get("payload"),
              c.get("media_id"),
              id,
              uid);
    }
    db.audit(uid, "REVIEW_" + d.decision(), id);
    return db.expand(get(id));
  }
}
