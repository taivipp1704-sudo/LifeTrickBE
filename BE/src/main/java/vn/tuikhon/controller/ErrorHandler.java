package vn.tuikhon.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tuikhon.model.ApiError;

@RestControllerAdvice
public class ErrorHandler {
  @ExceptionHandler(ApiError.class)
  ResponseEntity<?> api(ApiError e) {
    return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler({
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                "Dữ liệu chưa hợp lệ. Kiểm tra độ dài, URL HTTPS và đủ ba bước hướng dẫn."));
  }

  @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
  ResponseEntity<?> duplicate(Exception e) {
    return ResponseEntity.status(409).body(Map.of("message", "Dữ liệu bị trùng hoặc đã được lưu."));
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> large(Exception e) {
    return ResponseEntity.status(413).body(Map.of("message", "Tệp vượt giới hạn 30 MB."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unknown(Exception e) {
    org.slf4j.LoggerFactory.getLogger(ErrorHandler.class)
        .error(
            "Unexpected error requestId={} type={}",
            org.slf4j.MDC.get("requestId"),
            e.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .body(Map.of("message", "Không thể hoàn tất yêu cầu. Dùng mã X-Request-ID để tra cứu."));
  }
}
