import path from 'path'

import react from '@vitejs/plugin-react'
import { visualizer } from 'rollup-plugin-visualizer'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [
    react(),
    // Run `ANALYZE=true pnpm build` to generate bundle-stats.html
    process.env.ANALYZE === 'true' && visualizer({
      open: false,
      filename: 'bundle-stats.html',
      gzipSize: true,
      brotliSize: true,
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8081',
        ws: true,
        changeOrigin: true,
      },
      '/chat': {
        target: 'ws://localhost:8081',
        ws: true,
        changeOrigin: true,
        bypass(req) {
          // Only proxy WebSocket upgrades; regular HTTP navigation stays in the SPA
          if (req.headers.upgrade?.toLowerCase() !== 'websocket') return req.url
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    alias: { '@': path.resolve(__dirname, './src') },
  },
  build: {
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          if (id.includes('node_modules')) {
            if (id.includes('framer-motion')) return 'vendor-motion'
            if (id.includes('react-router')) return 'vendor-router'
            if (id.includes('react-markdown') || id.includes('remark') || id.includes('rehype') || id.includes('unified') || id.includes('micromark') || id.includes('mdast') || id.includes('unist')) return 'vendor-markdown'
            if (id.includes('react-syntax-highlighter') || id.includes('highlight.js') || id.includes('refractor') || id.includes('prismjs')) return 'vendor-highlight'
            if (id.includes('react-dom') || id.includes('react/')) return 'vendor-react'
            if (id.includes('antd') || id.includes('@ant-design') || id.includes('rc-')) return 'vendor-antd'
            if (id.includes('@xyflow')) return 'vendor-flow'
            if (id.includes('@tanstack')) return 'vendor-query'
            if (id.includes('zustand')) return 'vendor-zustand'
            if (id.includes('axios')) return 'vendor-axios'
          }
        },
      },
    },
  },
})
