import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, loadEnv } from 'vite'

// The dev server proxies /api to the Java backend, so the browser only ever talks to one origin.
// Point it elsewhere with VITE_API_TARGET (e.g. VITE_API_TARGET=http://127.0.0.1:8090 npm run dev).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_API_TARGET || 'http://127.0.0.1:8080'
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target, changeOrigin: true },
      },
    },
    preview: {
      port: 4173,
      proxy: {
        '/api': { target, changeOrigin: true },
      },
    },
    build: {
      target: 'es2022',
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          // keep the heavy 3D and chart libraries out of the first paint
          manualChunks(id: string) {
            if (id.includes('node_modules')) {
              if (/three|@react-three|postprocessing|maath|troika/.test(id)) return 'three'
              if (/recharts|d3-/.test(id)) return 'charts'
              if (/gsap|motion|lenis|ogl/.test(id)) return 'motion'
            }
            return undefined
          },
        },
      },
    },
  }
})
