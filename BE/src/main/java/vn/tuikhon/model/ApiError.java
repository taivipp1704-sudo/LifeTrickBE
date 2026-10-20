package vn.tuikhon.model;

public class ApiError extends RuntimeException {
  public final int status;

  public ApiError(int status, String message) {
    super(message);
    this.status = status;
  }

  public static ApiError missing() {
    return new ApiError(404, "Không tìm thấy nội dung hoặc bạn không có quyền truy cập.");
  }

  public static ApiError conflict() {
    return new ApiError(409, "Nội dung đã thay đổi. Hãy tải lại trước khi tiếp tục.");
  }
}
