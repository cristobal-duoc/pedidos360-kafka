import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // strictPort: si el 5173 está ocupado, Vite FALLA en vez de saltar al 5174
    // y romper el redirect_uri. El 5173 es el que quedó autorizado en Cognito.
    port: 5173,
    strictPort: true,
  },
})
