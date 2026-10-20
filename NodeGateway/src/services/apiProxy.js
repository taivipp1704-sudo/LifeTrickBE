import http from "node:http";
import https from "node:https";
import { config } from "../config.js";
export function apiProxy() {
  const target = new URL(config.API_ORIGIN);
  if (!["http:", "https:"].includes(target.protocol))
    throw new Error("Invalid API protocol");
  return (req, res, next) => {
    if (!req.path.startsWith("/api/")) return next();
    const headers = {
      ...req.headers,
      host: target.host,
      "x-gateway-key": config.GATEWAY_SECRET,
      "x-client-ip": req.ip || req.socket.remoteAddress,
    };
    for (const key of [
      "connection",
      "proxy-authorization",
      "proxy-authenticate",
      "upgrade",
      "x-forwarded-for",
      "x-forwarded-host",
      "x-forwarded-proto",
    ])
      delete headers[key];
    const upstream = (target.protocol === "https:" ? https : http).request(
      {
        hostname: target.hostname,
        port: target.port,
        method: req.method,
        path: req.originalUrl,
        headers,
        timeout: 150000,
      },
      (response) => {
        res.writeHead(response.statusCode, response.headers);
        response.on("error", () => res.destroy());
        response.pipe(res);
      },
    );
    upstream.on("timeout", () => upstream.destroy(new Error("API timeout")));
    upstream.on("error", (error) => {
      req.log?.error(
        { errorType: error.code || "proxy-error" },
        "API unavailable",
      );
      if (!res.headersSent)
        res
          .status(502)
          .json({
            message: "Dịch vụ nghiệp vụ chưa sẵn sàng. Vui lòng thử lại.",
            requestId: req.id,
          });
      else res.destroy();
    });
    req.on("aborted", () => upstream.destroy());
    res.on("close", () => {
      if (!res.writableEnded) upstream.destroy();
    });
    req.pipe(upstream);
  };
}
