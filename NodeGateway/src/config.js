import dotenv from "dotenv";
import { fileURLToPath } from "node:url";
import { z } from "zod";
dotenv.config({
  path:
    process.env.ENV_FILE ||
    fileURLToPath(new URL("../../env/.env.local", import.meta.url)),
  quiet: true,
});
const schema = z.object({
  NODE_ENV: z
    .enum(["development", "production", "test"])
    .default("development"),
  GATEWAY_HOST: z.string().default("127.0.0.1"),
  GATEWAY_PORT: z.coerce.number().int().min(1).max(65535).default(3000),
  API_ORIGIN: z.url().default("http://127.0.0.1:8088"),
  GATEWAY_SECRET: z.string().min(32),
  TRUSTED_PROXY: z.string().default(""),
  APP_ORIGINS: z.string().min(1),
});
export const config = schema.parse(process.env);
if (config.NODE_ENV === "production" && process.env.COOKIE_SECURE !== "true")
  throw new Error("COOKIE_SECURE must be true in production");
