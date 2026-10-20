import { config } from "../config.js";
export async function health(req, res) {
  try {
    const r = await fetch(config.API_ORIGIN + "/actuator/health", {
      signal: AbortSignal.timeout(3000),
    });
    const value = await r.json();
    res
      .status(r.ok && value.status === "UP" ? 200 : 503)
      .json({ status: r.ok && value.status === "UP" ? "UP" : "DOWN" });
  } catch {
    res.status(503).json({ status: "DOWN" });
  }
}
