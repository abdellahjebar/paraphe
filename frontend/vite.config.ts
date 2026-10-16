/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { defineConfig } from 'vite'

// The backend serves the built interface from the same origin, so there is no CORS to configure.
// In development the Vite server forwards the two API prefixes to the backend instead.
const backend = process.env.PARAPHE_BACKEND ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
      // The reviewer API. The reviewer *page* is /relecture/{token} and is handled by the router.
      '/r/': { target: backend, changeOrigin: false },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // Nothing may be inlined as a data: URL or script: the content security policy allows 'self' only.
    assetsInlineLimit: 0,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
})
