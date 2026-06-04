/** @type {import('next').NextConfig} */
const nextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: 'http',
        hostname: 'localhost',
        port: '9000',
      },
    ],
  },
  // 代理 API 请求到后端 (可通过 BACKEND_URL 环境变量覆盖)
  async rewrites() {
    const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';
    const aiServiceUrl = process.env.AI_SERVICE_URL || 'http://localhost:8000';
    return [
      {
        source: '/api/:path*',
        destination: `${backendUrl}/api/:path*`,
      },
      {
        source: '/ai/:path*',
        destination: `${aiServiceUrl}/ai/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
