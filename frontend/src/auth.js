// Authorization Code Flow with PKCE contra Cognito, a mano (sin librerías de auth).
// El front es un CLIENTE PÚBLICO: no puede guardar un secreto, por eso PKCE.

const cfg = {
  dominio: import.meta.env.VITE_COGNITO_DOMAIN,
  clientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
  redirectUri: import.meta.env.VITE_REDIRECT_URI,
  // openid es obligatorio en OIDC. Los scopes de negocio (productos/*) NO se piden
  // aquí: los concede el Lambda según el grupo del usuario.
  scopes: 'openid email profile',
}
export const config = cfg

export function configuracionIncompleta() {
  return Object.entries(cfg).filter(([, v]) => !v).map(([k]) => k)
}

// ---------- PKCE ----------
function base64Url(buffer) {
  return btoa(String.fromCharCode(...new Uint8Array(buffer)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}
function aleatorio(bytes = 32) {
  const b = new Uint8Array(bytes)
  crypto.getRandomValues(b)
  return base64Url(b)
}
async function challenge(verifier) {
  const h = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  return base64Url(h)
}

const K_VERIFIER = 'dsy1107.pkce_verifier'
const K_STATE = 'dsy1107.state'
const K_TOKENS = 'dsy1107.tokens'

// ---------- Pasos 1-3: salir hacia /authorize ----------
export async function login() {
  const verifier = aleatorio()
  const chal = await challenge(verifier)
  const state = aleatorio(16)
  // el verifier y el state se quedan en ESTA pestaña.
  sessionStorage.setItem(K_VERIFIER, verifier)
  sessionStorage.setItem(K_STATE, state)

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: cfg.clientId,
    redirect_uri: cfg.redirectUri,
    scope: cfg.scopes,
    state,
    code_challenge: chal,
    code_challenge_method: 'S256',
  })
  // La contraseña se escribe en Cognito, nunca aquí.
  window.location.assign(`${cfg.dominio}/oauth2/authorize?${params}`)
}

// ---------- Pasos 6-9: volver y canjear el code en /token ----------
export function getTokens() {
  const c = sessionStorage.getItem(K_TOKENS)
  return c ? JSON.parse(c) : null
}
export const getAccessToken = () => getTokens()?.access_token ?? null
export const getIdToken = () => getTokens()?.id_token ?? null

function limpiarUrl() {
  window.history.replaceState({}, document.title, window.location.pathname)
}

export async function procesarRetorno() {
  const url = new URL(window.location.href)
  const code = url.searchParams.get('code')
  const state = url.searchParams.get('state')
  const error = url.searchParams.get('error')
  if (error) {
    limpiarUrl()
    throw new Error(`${error}: ${url.searchParams.get('error_description') ?? ''}`)
  }
  if (!code) return null
  // defensa CSRF: el state debe coincidir con el que enviamos.
  if (state !== sessionStorage.getItem(K_STATE)) {
    limpiarUrl()
    throw new Error('El parámetro state no coincide. Se descarta la respuesta.')
  }
  const verifier = sessionStorage.getItem(K_VERIFIER)
  const r = await fetch(`${cfg.dominio}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: cfg.clientId,
      code,
      redirect_uri: cfg.redirectUri,
      code_verifier: verifier, // aquí SÍ viaja el verifier
    }),
  })
  const datos = await r.json()
  limpiarUrl()
  sessionStorage.removeItem(K_VERIFIER)
  sessionStorage.removeItem(K_STATE)
  if (!r.ok) throw new Error(`/token ${r.status}: ${datos.error ?? 'error'}`)
  sessionStorage.setItem(K_TOKENS, JSON.stringify(datos))
  return datos
}

// ---------- Leer el token (NO valida la firma: eso lo hace el gateway) ----------
export function decodificarJwt(token) {
  if (!token) return null
  try {
    const p = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const bytes = Uint8Array.from(atob(p), (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

// Los roles del usuario: el claim cognito:groups del access token.
export function rolesDelUsuario(tokens) {
  const claims = decodificarJwt(tokens?.access_token)
  return claims?.['cognito:groups'] ?? []
}
export function tieneRol(tokens, rol) {
  return rolesDelUsuario(tokens).includes(rol)
}

// ---------- Logout (cierra también la sesión SSO en Cognito) ----------
export function logout() {
  sessionStorage.removeItem(K_TOKENS)
  const params = new URLSearchParams({
    client_id: cfg.clientId,
    logout_uri: cfg.redirectUri,
  })
  window.location.assign(`${cfg.dominio}/logout?${params}`)
}
