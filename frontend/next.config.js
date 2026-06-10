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
  // Proxy /api requests to the Java backend (override with BACKEND_URL).
  // Standard AI calls stay frontend-direct; FamilyAgent chat streaming uses the Java proxy to avoid buffering.
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
