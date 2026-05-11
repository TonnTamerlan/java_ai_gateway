import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// `VITE_API_PROXY_TARGET` lets the dev compose override the target to the
// Docker DNS name `api-gateway`; running `pnpm dev` natively keeps the default.
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.ts'],
    css: false,
  },
});
