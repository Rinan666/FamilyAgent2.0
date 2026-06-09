/** @type {import('next').NextConfig} */
const nextConfig = {
  poweredByHeader: false,
  allowedDevOrigins: [
    'age-shadow-vitamin-ingredients.trycloudflare.com',
    'words-lawyers-his-domain.trycloudflare.com',
    'familyagent.cn',
    'www.familyagent.cn',
    'app.familyagent.cn',
    'app.familyagentai.top',
  ],
  images: {
    remotePatterns: [
      {
        protocol: 'http',
        hostname: 'localhost',
        port: '9000',
      },
    ],
  },
  // 代理 /api 请求到 Java 后端 (可通过 BACKEND_URL 环境变量覆盖)
  // 注：普通 AI 服务走前端直连；FamilyAgent 聊天流统一走 Java 透明流式代理，避免中间层缓冲。
  async headers() {
    const securityHeaders = [
      {
        key: 'X-Content-Type-Options',
        value: 'nosniff',
      },
    ];
    const noStoreHeaders = [
      {
        key: 'Cache-Control',
        value: 'no-store, max-age=0, must-revalidate',
      },
    ];

    return [
      {
        source: '/:path*',
        headers: securityHeaders,
      },
      {
        source: '/((?!_next/static|_next/image).*)',
        headers: noStoreHeaders,
      },
    ];
  },
  async rewrites() {
    const backendUrl = process.env.BACKEND_URL || 'http://localhost:8180';
    const aiServiceUrl = process.env.AI_SERVICE_URL || process.env.NEXT_PUBLIC_AI_SERVICE_URL || 'http://localhost:8090';
    return [
      {
        source: '/ai-proxy/agent/chat/stream',
        destination: `${backendUrl}/api/agent/chat/stream`,
      },
      {
        source: '/ai-proxy/tutor/explain',
        destination: `${backendUrl}/api/tutor/explain`,
      },
      {
        source: '/ai-proxy/:path*',
        destination: `${aiServiceUrl}/ai/:path*`,
      },
      {
        source: '/api/:path*',
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
