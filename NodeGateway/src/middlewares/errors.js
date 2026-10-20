export function notFound(req, res) {
  res.status(404).json({ message: "Không tìm thấy đường dẫn." });
}
export function errors(err, req, res, next) {
  if (res.headersSent) return next(err);
  req.log?.error({ errorType: err.name, requestId: req.id }, "Request failed");
  res
    .status(err.status || 500)
    .json({
      message:
        err.status === 403
          ? "Nguồn truy cập không được cho phép."
          : "Máy chủ chưa hoàn tất yêu cầu.",
      requestId: req.id,
    });
}
