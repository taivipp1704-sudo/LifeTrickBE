import { createApp } from "./app.js";
import { config } from "./config.js";
const server = createApp().listen(
  config.GATEWAY_PORT,
  config.GATEWAY_HOST,
  () =>
    console.log(
      `Túi Khôn: http://${config.GATEWAY_HOST}:${config.GATEWAY_PORT}`,
    ),
);
server.headersTimeout = 20000;
server.requestTimeout = 160000;
let stopping = false;
function stop(fatal = false) {
  if (stopping) return;
  stopping = true;
  server.close(() => process.exit(fatal ? 1 : 0));
  setTimeout(() => process.exit(fatal ? 1 : 0), 10000).unref();
}
process.on("SIGTERM", () => stop());
process.on("SIGINT", () => stop());
process.on("unhandledRejection", () => {
  console.error("Fatal unhandled rejection; restarting required.");
  stop(true);
});
process.on("uncaughtException", () => {
  console.error("Fatal exception; restarting required.");
  stop(true);
});
