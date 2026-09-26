import { createServer } from "node:http";
import { timingSafeEqual } from "node:crypto";
import { refineRevision } from "./core.mjs";

const PORT = Number(process.env.PORT || 8787);
const REQUIRED_TOKEN = process.env.AI_GATEWAY_TOKEN || "";
const MAX_BODY_BYTES = 64 * 1024;

function authorized(req) {
  if (!REQUIRED_TOKEN) return true;
  const supplied = String(req.headers["x-ai-gateway-token"] || "");
  const expectedBuffer = Buffer.from(REQUIRED_TOKEN);
  const suppliedBuffer = Buffer.from(supplied);
  return (
    expectedBuffer.length === suppliedBuffer.length &&
    timingSafeEqual(expectedBuffer, suppliedBuffer)
  );
}

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
    "Cache-Control": "no-store"
  });
  res.end(payload);
}

async function readJson(req) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new Error("request_too_large");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

const server = createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    return sendJson(res, 200, { ok: true });
  }

  if (req.method !== "POST" || req.url !== "/v1/revision-chat") {
    return sendJson(res, 404, { error: "not_found" });
  }

  if (!authorized(req)) {
    return sendJson(res, 401, { error: "unauthorized" });
  }

  try {
    const body = await readJson(req);
    if (
      typeof body.current_goal !== "string" ||
      typeof body.user_message !== "string" ||
      !body.user_message.trim()
    ) {
      return sendJson(res, 400, { error: "invalid_request" });
    }

    const result = await refineRevision(body);
    return sendJson(res, 200, result);
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown_error";
    const status = message === "request_too_large" ? 413 : 502;
    return sendJson(res, status, { error: message });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  process.stdout.write(`AI electronics gateway listening on port ${PORT}\n`);
});
