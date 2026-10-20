import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
const root = fileURLToPath(new URL("..", import.meta.url)),
  env = Object.fromEntries(
    fs
      .readFileSync(path.join(root, "env/.env.local"), "utf8")
      .split(/\r?\n/)
      .filter((x) => x.includes("=") && !x.startsWith("#"))
      .map((x) => [x.slice(0, x.indexOf("=")), x.slice(x.indexOf("=") + 1)]),
  );
const base = "http://127.0.0.1:3000",
  results = [];
function check(ok, label) {
  assert.ok(ok, label);
  results.push(label);
  console.log("PASS " + label);
}
class Client {
  constructor() {
    this.cookies = new Map();
    this.csrf = null;
  }
  async req(
    url,
    { method = "GET", body, csrf = true, headers = {}, raw = false } = {},
  ) {
    if (method !== "GET" && csrf && !this.csrf) await this.token();
    const h = { ...headers };
    if (this.cookies.size)
      h.Cookie = [...this.cookies].map(([k, v]) => k + "=" + v).join("; ");
    if (method !== "GET" && csrf) h[this.csrf.headerName] = this.csrf.token;
    if (body && !(body instanceof FormData)) {
      h["Content-Type"] = "application/json";
      body = JSON.stringify(body);
    }
    const r = await fetch(base + "/api" + url, { method, body, headers: h });
    for (const c of r.headers.getSetCookie()) {
      const [pair] = c.split(";"),
        i = pair.indexOf("=");
      this.cookies.set(pair.slice(0, i), pair.slice(i + 1));
    }
    const data = raw
      ? Buffer.from(await r.arrayBuffer())
      : await r.json().catch(() => ({}));
    return { status: r.status, data, headers: r.headers };
  }
  async token() {
    const r = await this.req("/auth/csrf");
    assert.equal(r.status, 200);
    this.csrf = r.data;
  }
  async login(email, password) {
    await this.token();
    const r = await this.req("/auth/login", {
      method: "POST",
      body: { email, password },
    });
    assert.equal(r.status, 200, JSON.stringify(r.data));
    await this.token();
    return r.data;
  }
}
const admin = new Client(),
  author = new Client(),
  reviewer = new Client(),
  stranger = new Client(),
  anon = new Client();
const stamp = Date.now(),
  pw = randomBytes(20).toString("hex") + "aA9!",
  authorEmail = `author-${stamp}@example.test`,
  reviewEmail = `review-${stamp}@example.test`,
  strangerEmail = `reader-${stamp}@example.test`;
let publishedId = null,
  reviewId = null;
try {
  check(
    (await fetch(base + "/healthz")).status === 200,
    "Gateway and SQL-backed API healthy",
  );
  check(
    (await anon.req("/tips?size=100")).data.total >= 10,
    "Public reference guides seeded in SQL Server",
  );
  check(
    (
      await anon.req("/auth/login", {
        method: "POST",
        body: { email: authorEmail, password: pw },
        csrf: false,
      })
    ).status === 403,
    "Login rejects missing CSRF",
  );
  check(
    (await anon.req("/auth/me")).status === 401,
    "Anonymous account access denied",
  );
  check(
    (await fetch("http://127.0.0.1:8088/api/tips")).status === 403,
    "Direct API access without gateway secret denied",
  );
  check(
    (
      await anon.req("/tips", {
        headers: { Origin: "https://untrusted.example" },
      })
    ).status === 403,
    "Unapproved CORS origin denied",
  );
  const adm = await admin.login(
    env.BOOTSTRAP_ADMIN_EMAIL,
    env.BOOTSTRAP_ADMIN_PASSWORD,
  );
  check(adm.role === "ADMIN", "Bootstrap admin authenticates");
  for (const [c, email, name] of [
    [author, authorEmail, "Tác giả kiểm thử"],
    [reviewer, reviewEmail, "Người duyệt kiểm thử"],
    [stranger, strangerEmail, "Thành viên kiểm thử"],
  ]) {
    await c.token();
    const r = await c.req("/auth/register", {
      method: "POST",
      body: { email, password: pw, name, role: "ADMIN" },
    });
    assert.equal(r.status, 201, JSON.stringify(r.data));
    await c.login(email, pw);
  }
  const who = (await author.req("/auth/me")).data;
  check(who.role === "MEMBER", "Registration cannot grant privileged role");
  check(
    !JSON.stringify(who).includes("password"),
    "Password hash excluded from user response",
  );
  check(
    (await author.req("/admin/users")).status === 403,
    "Member cannot read admin accounts",
  );
  check(
    (await author.req("/reviews")).status === 403,
    "Member cannot access review queue",
  );
  const users = (await admin.req("/admin/users")).data;
  reviewId = users.find((u) => u.email === reviewEmail).id;
  await admin.req("/admin/users/" + reviewId, {
    method: "PATCH",
    body: { role: "REVIEWER", active: true },
  });
  check(
    (await reviewer.req("/auth/me")).status === 401,
    "Role change revokes old sessions",
  );
  await reviewer.login(reviewEmail, pw);
  const seed = (await anon.req("/tips/water")).data.content;
  const content = {
    ...seed,
    title: `Kiểm thử tích hợp ${stamp}`,
    description:
      "Hồ sơ kiểm tra quy trình trên máy, không dùng làm hướng dẫn công khai.",
  };
  let d = await author.req("/contributions", {
    method: "POST",
    body: {
      content: { ...content, title: "", description: "" },
      mediaId: null,
      version: 0,
    },
  });
  assert.equal(d.status, 200, JSON.stringify(d.data));
  d = d.data;
  publishedId = d.id;
  check(d.status === "DRAFT", "Incomplete draft can be saved on server");
  check(
    (await stranger.req("/contributions/" + d.id)).status === 404,
    "Another member cannot read private draft",
  );
  check(
    (
      await author.req("/contributions/" + d.id + "/submit", {
        method: "POST",
        body: { version: d.version },
      })
    ).status === 400,
    "Incomplete draft cannot be submitted",
  );
  const bad = new FormData();
  bad.set(
    "file",
    new Blob(["<svg><script>alert(1)</script></svg>"], { type: "image/jpeg" }),
    "fake.jpg",
  );
  check(
    (await author.req("/media", { method: "POST", body: bad })).status === 400,
    "Forged image extension and MIME rejected",
  );
  const image = new FormData();
  image.set(
    "file",
    new Blob(
      [fs.readFileSync(path.join(root, "tests/fixtures/kitchen.jpg"))],
      { type: "image/jpeg" },
    ),
    "kitchen.jpg",
  );
  const uploaded = await author.req("/media", { method: "POST", body: image });
  assert.equal(uploaded.status, 200, JSON.stringify(uploaded.data));
  check(
    uploaded.data.mime_type === "image/jpeg",
    "Real image decoded, normalized and stored",
  );
  check(
    (await anon.req("/media/" + uploaded.data.id, { raw: true })).status ===
      401,
    "Unpublished image requires authentication",
  );
  check(
    (await stranger.req("/media/" + uploaded.data.id, { raw: true })).status ===
      404,
    "Another member cannot download private media",
  );
  const dir = path.join(root, "artifacts/integration");
  fs.mkdirSync(dir, { recursive: true });
  const ffmpeg = path.resolve(root, "BE", env.FFMPEG_PATH);
  for (const seconds of [1, 61]) {
    const out = path.join(dir, `clip-${seconds}.mp4`);
    const r = spawnSync(
      ffmpeg,
      [
        "-y",
        "-v",
        "error",
        "-f",
        "lavfi",
        "-i",
        "color=c=green:s=320x240:r=12",
        "-t",
        String(seconds),
        "-c:v",
        "libx264",
        "-pix_fmt",
        "yuv420p",
        out,
      ],
      { timeout: 30000 },
    );
    assert.equal(r.status, 0, "Generate video fixture");
  }
  const longVideo = new FormData();
  longVideo.set(
    "file",
    new Blob([fs.readFileSync(path.join(dir, "clip-61.mp4"))], {
      type: "video/mp4",
    }),
    "long.mp4",
  );
  check(
    (await author.req("/media", { method: "POST", body: longVideo })).status ===
      400,
    "Server rejects video longer than 60 seconds",
  );
  const video = new FormData();
  video.set(
    "file",
    new Blob([fs.readFileSync(path.join(dir, "clip-1.mp4"))], {
      type: "video/mp4",
    }),
    "evidence.mp4",
  );
  const clip = await author.req("/media", { method: "POST", body: video });
  assert.equal(clip.status, 200, JSON.stringify(clip.data));
  check(
    clip.data.mime_type === "video/mp4" && clip.data.duration_seconds <= 60,
    "Real video probed, transcoded and stored",
  );
  check(
    (
      await author.req("/contributions/" + d.id, {
        method: "PUT",
        body: { content, mediaId: clip.data.id, version: d.version + 1 },
      })
    ).status === 409,
    "Stale update rejected by optimistic concurrency",
  );
  d = (
    await author.req("/contributions/" + d.id, {
      method: "PUT",
      body: { content, mediaId: clip.data.id, version: d.version },
    })
  ).data;
  d = (
    await author.req("/contributions/" + d.id + "/submit", {
      method: "POST",
      body: { version: d.version },
    })
  ).data;
  check(
    d.status === "SUBMITTED",
    "Valid contribution enters real review queue",
  );
  check(
    (
      await author.req("/contributions/" + d.id, {
        method: "PUT",
        body: { content, mediaId: clip.data.id, version: d.version },
      })
    ).status === 409,
    "Submitted contribution cannot be edited",
  );
  const authorId = who.id;
  await admin.req("/admin/users/" + authorId, {
    method: "PATCH",
    body: { role: "REVIEWER", active: true },
  });
  await author.login(authorEmail, pw);
  check(
    (
      await author.req("/reviews/" + d.id + "/claim", {
        method: "POST",
        body: { version: d.version },
      })
    ).status === 403,
    "Reviewer cannot claim own contribution",
  );
  await admin.req("/admin/users/" + authorId, {
    method: "PATCH",
    body: { role: "MEMBER", active: true },
  });
  await author.login(authorEmail, pw);
  check(
    (await reviewer.req("/reviews")).data.some((c) => c.id === d.id),
    "Submitted draft visible to independent reviewer",
  );
  d = (
    await reviewer.req("/reviews/" + d.id + "/claim", {
      method: "POST",
      body: { version: d.version },
    })
  ).data;
  check(d.status === "IN_REVIEW", "Reviewer claims item atomically");
  check(
    (
      await admin.req("/reviews/" + d.id + "/claim", {
        method: "POST",
        body: { version: d.version },
      })
    ).status === 409,
    "Second reviewer cannot claim item already assigned",
  );
  const decision = {
    decision: "APPROVE",
    notes: "Đã đối chiếu nguồn và kiểm tra video minh họa trong kiểm thử.",
    evidence: "https://www.cdc.gov/water-emergency/about/",
    scienceChecked: false,
    safetyChecked: true,
    materialsChecked: true,
    version: d.version,
  };
  check(
    (
      await reviewer.req("/reviews/" + d.id + "/decision", {
        method: "POST",
        body: decision,
      })
    ).status === 400,
    "Approval requires all safety checks",
  );
  d = (
    await reviewer.req("/reviews/" + d.id + "/decision", {
      method: "POST",
      body: { ...decision, decision: "REQUEST_CHANGES" },
    })
  ).data;
  check(
    d.status === "CHANGES_REQUESTED",
    "Reviewer can request corrections with evidence",
  );
  const history = (await author.req("/contributions/" + d.id)).data;
  check(
    history.reviews.length === 1 && history.reviews[0].evidence,
    "Author receives persistent review feedback",
  );
  d = (
    await author.req("/contributions/" + d.id, {
      method: "PUT",
      body: {
        content: {
          ...content,
          description: content.description + " Đã bổ sung.",
        },
        mediaId: clip.data.id,
        version: d.version,
      },
    })
  ).data;
  d = (
    await author.req("/contributions/" + d.id + "/submit", {
      method: "POST",
      body: { version: d.version },
    })
  ).data;
  d = (
    await reviewer.req("/reviews/" + d.id + "/claim", {
      method: "POST",
      body: { version: d.version },
    })
  ).data;
  const approved = await reviewer.req("/reviews/" + d.id + "/decision", {
    method: "POST",
    body: { ...decision, scienceChecked: true, version: d.version },
  });
  assert.equal(approved.status, 200, JSON.stringify(approved.data));
  check(
    approved.data.status === "APPROVED",
    "Independent human decision publishes contribution",
  );
  const pub = await anon.req("/tips/" + d.id);
  check(
    pub.status === 200 &&
      pub.data.verification === "HUMAN_REVIEWED" &&
      pub.data.media_type === "video/mp4",
    "Published API includes real reviewer and video metadata",
  );
  check(
    (
      await anon.req("/media/" + clip.data.id, {
        headers: { Range: "bytes=0-127" },
        raw: true,
      })
    ).status === 206,
    "Published video supports HTTP byte range",
  );
  await author.req("/notebook/" + d.id + "/bookmark", { method: "PUT" });
  await author.req("/notebook/" + d.id + "/progress", {
    method: "PUT",
    body: { completed: true },
  });
  const nb = (await author.req("/notebook")).data;
  check(
    nb.saved.includes(d.id) && nb.completed.includes(d.id),
    "Bookmarks and learning progress stored on server",
  );
  await author.req("/tips/" + d.id + "/feedback", {
    method: "POST",
    body: { text: "Kiểm tra tiếp nhận phản hồi nội dung." },
  });
  check(
    (await admin.req("/admin/feedback")).data.some((f) => f.tip_id === d.id),
    "Feedback reaches admin queue",
  );
  check(
    (await admin.req("/admin/audit")).data.some(
      (e) => e.action === "REVIEW_APPROVE" && e.target_id === d.id,
    ),
    "Review decision creates audit event",
  );
  const withdrawn = await admin.req("/admin/tips/" + d.id + "/unpublish", {
    method: "POST",
    body: { text: "Gỡ dữ liệu kiểm thử sau khi xác nhận quy trình." },
  });
  check(
    withdrawn.status === 200 &&
      (await anon.req("/tips/" + d.id)).status === 404,
    "Admin can withdraw published content without erasing history",
  );
  await author.req("/auth/logout", { method: "POST" });
  check(
    (await author.req("/auth/me")).status === 401,
    "Logout invalidates server session",
  );
  const rootResponse = await fetch(base);
  check(
    rootResponse.headers.get("x-content-type-options") === "nosniff" &&
      rootResponse.headers
        .get("content-security-policy")
        ?.includes("frame-ancestors 'none'"),
    "Gateway security headers present",
  );
  fs.writeFileSync(
    path.join(dir, "results.json"),
    JSON.stringify({ passed: results.length, results }, null, 2),
  );
  console.log(
    `COMPLETED ${results.length} integration checks against SQL Server`,
  );
} finally {
  if (reviewId)
    await admin
      .req("/admin/users/" + reviewId, {
        method: "PATCH",
        body: { role: "MEMBER", active: false },
      })
      .catch(() => {});
}
