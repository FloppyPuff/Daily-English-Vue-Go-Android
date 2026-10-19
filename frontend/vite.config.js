import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  base: './',
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: false,
      manifest: false,
      includeAssets: ['favicon.svg', 'icons/*.png', 'manifest.webmanifest'],
      workbox: {
        globPatterns: [
          '**/*.{js,css,html,svg,png,webmanifest}',
          'data/words.json',
          'data/sentences.json',
          'data/papers/index.json',
        ],
        globIgnores: ['data/papers/19*.json', 'data/papers/20*.json'],
        maximumFileSizeToCacheInBytes: 6 * 1024 * 1024,
        navigateFallback: 'index.html',
        cleanupOutdatedCaches: true,
        runtimeCaching: [
          {
            urlPattern: /\/data\/papers\/.+\.json$/,
            handler: 'StaleWhileRevalidate',
            options: { cacheName: 'daily-english-papers' },
          },
        ],
      },
      devOptions: { enabled: false },
    }),
  ],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8787',
    },
  },
  preview: {
    proxy: {
      '/api': 'http://127.0.0.1:8787',
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  test: {
    environment: 'node',
    include: ['tests/**/*.test.js'],
  },
});
