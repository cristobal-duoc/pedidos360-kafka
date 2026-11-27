import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Demo 100% local (npm run dev:local): login con Keycloak (local/docker-compose.yml)
// y backend en el PC. No hay secretos: el cliente es publico (PKCE).
const demoLocal = {
  VITE_COGNITO_DOMAIN: 'http://localhost:8180/realms/pedidos360/protocol/openid-connect',
  VITE_COGNITO_CLIENT_ID: 'pedidos360-front',
  VITE_REDIRECT_URI: 'http://localhost:5173/',
  VITE_OIDC_AUTHORIZE_PATH: '/auth',
  VITE_OIDC_TOKEN_PATH: '/token',
  VITE_OIDC_LOGOUT_PATH: '/logout',
  VITE_OIDC_LOGOUT_PARAM: 'post_logout_redirect_uri',
  // /api lo reenvia Vite al backend en localhost:8080 (proxy de abajo): sin CORS.
  VITE_API_URL: '/api',
}

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  define: mode === 'demo'
    ? Object.fromEntries(Object.entries(demoLocal)
        .map(([k, v]) => [`import.meta.env.${k}`, JSON.stringify(v)]))
    : {},
  server: {
    // strictPort: si el 5173 está ocupado, Vite FALLA en vez de saltar al 5174
    // y romper el redirect_uri. El 5173 es el que quedó autorizado en Cognito.
    port: 5173,
    strictPort: true,
    // En la nube no se usa: alli VITE_API_URL apunta directo al API Gateway.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        rewrite: (ruta) => ruta.replace(/^\/api/, ''),
      },
    },
  },
}))
