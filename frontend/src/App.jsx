import { useEffect, useState } from 'react'
import {
  config, configuracionIncompleta, login, logout, procesarRetorno,
  getTokens, decodificarJwt, rolesDelUsuario, tieneRol,
} from './auth.js'
import { listarProductos, crearProducto } from './api.js'

// Guard a nivel de módulo: en dev, React 18 (StrictMode) monta dos veces y el
// efecto correría dos veces; el code es de un solo uso y el 2º canje daría error.
let callbackProcesado = false

// Un guard de UI: muestra el contenido solo si el usuario tiene el rol.
function Guard({ tokens, rol, children }) {
  if (!tokens) return <p>Debes iniciar sesión.</p>
  if (rol && !tieneRol(tokens, rol)) return <p className="muted">Requiere rol: {rol}</p>
  return children
}

export default function App() {
  const [tokens, setTokens] = useState(getTokens())
  const [error, setError] = useState(null)
  const [resultado, setResultado] = useState(null)

  const faltantes = configuracionIncompleta()

  useEffect(() => {
    if (callbackProcesado) return
    callbackProcesado = true
    procesarRetorno().then((t) => t && setTokens(t)).catch((e) => setError(e.message))
  }, [])

  async function llamar(fn) {
    setError(null)
    try {
      setResultado(await fn())
    } catch (e) {
      setResultado(null)
      setError(e.message)
    }
  }

  if (faltantes.length > 0) {
    return (
      <main>
        <h1>Pedidos360</h1>
        <p className="error">Falta configurar: {faltantes.join(', ')}</p>
        <pre>terraform output -raw env_frontend {'>'} frontend/.env.local</pre>
        <p className="muted">Y reinicia <code>npm run dev</code> (Vite lee el .env al arrancar).</p>
      </main>
    )
  }

  const idClaims = decodificarJwt(tokens?.id_token)
  const roles = rolesDelUsuario(tokens)

  return (
    <main>
      <h1>Pedidos360 · Identidad con Cognito</h1>
      <p className="muted">{new URL(config.dominio).host}</p>

      {error && <p className="error">{error}</p>}

      {!tokens ? (
        <button onClick={() => login().catch((e) => setError(e.message))}>
          Iniciar sesión
        </button>
      ) : (
        <>
          <section>
            <p><strong>Usuario:</strong> {idClaims?.email ?? '—'}</p>
            <p><strong>Roles (cognito:groups):</strong> {roles.join(', ') || '(ninguno)'}</p>
          </section>

          <div className="botones">
            <button onClick={() => llamar(listarProductos)}>GET /productos</button>
            {/* El guard oculta el botón de crear si no eres editor (UX). La
                seguridad real la impone el API Gateway con el 403. */}
            <Guard tokens={tokens} rol="editores">
              <button onClick={() => llamar(() => crearProducto({ nombre: 'Café', precio: 2500 }))}>
                POST /productos (crear)
              </button>
            </Guard>
            <button onClick={logout}>Cerrar sesión</button>
          </div>

          {resultado && (
            <pre className="ok">{JSON.stringify(resultado, null, 2)}</pre>
          )}

          <details>
            <summary>Ver los claims del token</summary>
            <pre>{JSON.stringify(decodificarJwt(tokens.access_token), null, 2)}</pre>
          </details>
        </>
      )}
    </main>
  )
}
