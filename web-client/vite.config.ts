/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Dev server config.
 *
 * The browser talks to `/api/**` on the SAME origin as the app and Vite
 * proxies it to the gateway - the same shape as nginx locally and CloudFront
 * on AWS, so no environment ever needs CORS.
 */
export default defineConfig(() => {
  // Where the API gateway lives (the `make up` stack by default).
  const target = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: { '/api': { target, changeOrigin: true } },
    },
    preview: {
      port: 4173,
      proxy: { '/api': { target, changeOrigin: true } },
    },
    test: {
      // Unit tests only; e2e/ is Playwright's (npm run e2e).
      include: ['src/**/*.test.ts'],
    },
    build: {
      outDir: 'dist',
      // Off for production builds: nginx would serve the .map files publicly,
      // handing out the full original source. Opt in for local debugging with
      // VITE_SOURCEMAP=true npm run build.
      sourcemap: process.env.VITE_SOURCEMAP === 'true',
      rollupOptions: {
        output: {
          manualChunks: {
            react: ['react', 'react-dom', 'react-router-dom'],
            charts: ['recharts'],
          },
        },
      },
    },
  };
});
