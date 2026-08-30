import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Minimal, self-contained runtime image for Docker: only the files
  // actually needed to run `node server.js` are copied into the final
  // stage (see frontend/Dockerfile) instead of the whole node_modules tree.
  output: "standalone",
};

export default nextConfig;
