/** @type {import('next').NextConfig} */
const nextConfig = {
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
  // 注：AI 服务通过前端直连 NEXT_PUBLIC_AI_SERVICE_URL，不经过此代理
  async rewrites() {
    const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';
    return [
      {
        source: '/api/:path*',
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
