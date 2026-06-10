const fs = require("fs");
const http = require("http");
const https = require("https");

const pfxPath = process.env.EDGE_PROXY_PFX_PATH;
const pfxPassphrase = process.env.EDGE_PROXY_PFX_PASSPHRASE;

if (!pfxPath || !pfxPassphrase) {
  throw new Error("Missing EDGE_PROXY_PFX_PATH or EDGE_PROXY_PFX_PASSPHRASE");
}

const FRONTEND_TARGET = {
  protocol: "http:",
  hostname: "127.0.0.1",
  port: 3000,
};

const AI_TARGET = {
  protocol: "http:",
  hostname: "127.0.0.1",
  port: 8090,
};

function normalizeHost(hostHeader) {
  return (hostHeader || "").split(":")[0].trim().toLowerCase();
}

function resolveTarget(hostHeader) {
  const host = normalizeHost(hostHeader);
  if (host === "ai.familyagent.cn") {
    return AI_TARGET;
  }
  return FRONTEND_TARGET;
}

function buildProxyHeaders(req) {
  const headers = { ...req.headers };
  const forwardedFor = req.socket.remoteAddress || "";
  headers["x-forwarded-for"] = headers["x-forwarded-for"]
    ? `${headers["x-forwarded-for"]}, ${forwardedFor}`
    : forwardedFor;
  headers["x-forwarded-host"] = req.headers.host || "";
  headers["x-forwarded-proto"] = req.socket.encrypted ? "https" : "http";
  return headers;
}

function proxyRequest(req, res) {
  const target = resolveTarget(req.headers.host);
  const client = target.protocol === "https:" ? https : http;
  const upstream = client.request(
    {
      protocol: target.protocol,
      hostname: target.hostname,
      port: target.port,
      method: req.method,
      path: req.url,
      headers: buildProxyHeaders(req),
    },
    (upstreamRes) => {
      res.writeHead(upstreamRes.statusCode || 502, upstreamRes.headers);
      upstreamRes.pipe(res);
    }
  );

  upstream.on("error", (error) => {
    res.writeHead(502, { "content-type": "text/plain; charset=utf-8" });
    res.end(`Bad Gateway: ${error.message}`);
  });

  req.pipe(upstream);
}

function proxyUpgrade(req, socket, head) {
  const target = resolveTarget(req.headers.host);
  const client = target.protocol === "https:" ? https : http;
  const upstream = client.request(
    {
      protocol: target.protocol,
      hostname: target.hostname,
      port: target.port,
      method: req.method,
      path: req.url,
      headers: buildProxyHeaders(req),
    }
  );

  upstream.on("upgrade", (upstreamRes, upstreamSocket, upstreamHead) => {
    const statusLine = `HTTP/1.1 ${upstreamRes.statusCode || 101} ${
      upstreamRes.statusMessage || "Switching Protocols"
    }\r\n`;
    socket.write(statusLine);
    Object.entries(upstreamRes.headers).forEach(([key, value]) => {
      socket.write(`${key}: ${value}\r\n`);
    });
    socket.write("\r\n");

    if (upstreamHead?.length) {
      socket.write(upstreamHead);
    }
    if (head?.length) {
      upstreamSocket.write(head);
    }

    upstreamSocket.pipe(socket);
    socket.pipe(upstreamSocket);
  });

  upstream.on("error", () => {
    socket.destroy();
  });

  upstream.end();
}

const requestHandler = (req, res) => proxyRequest(req, res);

const httpServer = http.createServer(requestHandler);
httpServer.on("upgrade", proxyUpgrade);

const httpsServer = https.createServer(
  {
    pfx: fs.readFileSync(pfxPath),
    passphrase: pfxPassphrase,
  },
  requestHandler
);
httpsServer.on("upgrade", proxyUpgrade);

httpServer.listen(80, "0.0.0.0", () => {
  console.log("HTTP origin proxy listening on :80");
});

httpsServer.listen(443, "0.0.0.0", () => {
  console.log("HTTPS origin proxy listening on :443");
});
