/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const api = process.env.SULU_API_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': api, '/media': api } },
  preview: { port: 5173, proxy: { '/api': api, '/media': api } },
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'], globals: true, css: false },
})
