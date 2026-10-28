import type { NextConfig } from 'next';

const API = process.env.API_URL ?? 'http://localhost:8280';

/** /api/** goes to the platform API on the same origin, so the refresh cookie stays first-party. */
const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${API}/api/:path*` }];
  },
};

export default nextConfig;
