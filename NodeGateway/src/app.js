import express from "express";
import helmet from "helmet";
import cors from "cors";
import { rateLimit } from "express-rate-limit";
import pinoHttp from "pino-http";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import { existsSync } from "node:fs";
import { config } from "./config.js";
import { apiProxy } from "./services/apiProxy.js";
import routes from "./routes/health.js";
import { errors, notFound } from "./middlewares/errors.js";
export function createApp() {
  const app = express();
  app.disable("x-powered-by");
  app.set(
    "trust proxy",
    config.TRUSTED_PROXY
      ? config.TRUSTED_PROXY.split(",").map((x) => x.trim())
      : false,
  );
  const allowed = config.APP_ORIGINS.split(",").map((x) => x.trim());
  app.use(
    pinoHttp({
      genReqId: () => randomUUID(),
      redact: [
        "req.headers.cookie",
        "req.headers.authorization",
        "req.headers.x-csrf-token",
        "req.headers.x-gateway-key",
        "res.headers.set-cookie",
      ],
      serializers: {
        req: (req) => ({
          id: req.id,
          method: req.method,
          path: req.url?.split("?")[0],
        }),
        res: (res) => ({ statusCode: res.statusCode }),
      },
    }),
  );
  app.use(
    helmet({
      hsts:
        config.NODE_ENV === "production"
          ? { maxAge: 31536000, includeSubDomains: true }
          : false,
      contentSecurityPolicy: {
        directives: {
          defaultSrc: ["'self'"],
          scriptSrc: ["'self'"],
          styleSrc: ["'self'", "'unsafe-inline'"],
          imgSrc: ["'self'", "blob:", "data:"],
          fontSrc: ["'self'"],
          mediaSrc: ["'self'", "blob:"],
          connectSrc: ["'self'"],
          objectSrc: ["'none'"],
          frameAncestors: ["'none'"],
          baseUri: ["'self'"],
          formAction: ["'self'"],
          upgradeInsecureRequests: null,
        },
      },
    }),
  );
  app.use((req, res, next) => {
    res.setHeader(
      "Permissions-Policy",
      "camera=(), microphone=(), geolocation=(self), payment=(), usb=()",
    );
    res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    res.setHeader("X-Request-ID", req.id);
    next();
  });
  app.use(
    cors({
      credentials: true,
      origin(origin, cb) {
        if (!origin || allowed.includes(origin)) cb(null, true);
        else cb(Object.assign(new Error("Origin denied"), { status: 403 }));
      },
      allowedHeaders: ["Content-Type", "X-CSRF-TOKEN"],
      methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    }),
  );
  app.use(
    "/api",
    rateLimit({
      windowMs: 60000,
      limit: 240,
      standardHeaders: "draft-8",
      legacyHeaders: false,
      message: { message: "Bạn gửi quá nhiều yêu cầu. Hãy thử lại sau." },
    }),
  );
  app.use(
    "/api/auth/login",
    rateLimit({
      windowMs: 900000,
      limit: 40,
      standardHeaders: "draft-8",
      legacyHeaders: false,
      message: { message: "Tạm giới hạn đăng nhập. Hãy thử lại sau." },
    }),
  );
  app.use((req, res, next) => {
    const n = Number(req.headers["content-length"]);
    if (n > 31 * 1024 * 1024)
      return res.status(413).json({ message: "Yêu cầu vượt giới hạn 31 MB." });
    next();
  });
  app.use(routes);
  app.use(apiProxy());
  const build = fileURLToPath(new URL("../../FE/build", import.meta.url));
  app.use(
    "/legacy",
    express.static(fileURLToPath(new URL("../../dist", import.meta.url)), {
      dotfiles: "deny",
    }),
  );
  app.use(
    express.static(build, {
      dotfiles: "deny",
      index: false,
      maxAge: config.NODE_ENV === "production" ? "1h" : 0,
    }),
  );
  app.get("/{*path}", (req, res, next) => {
    if (req.path.startsWith("/api/")) return next();
    if (!existsSync(build + "/index.html")) {
      if(req.path === "/")return res.json({service:"LifeTrick API",health:"/healthz",frontendRepository:"https://github.com/taivipp1704-sudo/LifeTrickFE"});
      return next();
    }
    res.setHeader("Cache-Control", "no-store");
    res.sendFile(build + "/index.html");
  });
  app.use(notFound);
  app.use(errors);
  return app;
}
