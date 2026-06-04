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
  // 代理 API 请求到后端
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://localhost:8080/api/:path*',
      },
      {
        source: '/ai/:path*',
        destination: 'http://localhost:8000/ai/:path*',
      },
    ];
  },
};

module.exports = nextConfig;
