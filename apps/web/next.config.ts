import type { NextConfig } from 'next';

// Read at build time (rewrites are compiled in): the Docker build passes API_URL as a build argument.
const API = process.env.API_URL ?? 'http://localhost:8280';

/** /api/** goes to the platform API on the same origin, so the refresh cookie stays first-party. */
const nextConfig: NextConfig = {
  // A self-contained server for the Docker image (.next/standalone).
  output: 'standalone',
  // Purchasing moved from Retail to General Business; old links keep working.
  async redirects() {
    return ['purchasing', 'purchase-orders', 'suppliers'].map((p) => ({
      source: `/:org/retail/${p}/:rest*`, destination: `/:org/business/${p}/:rest*`, permanent: false,
    }));
  },
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${API}/api/:path*` }];
  },
};

export default nextConfig;
