import { useEffect, useState } from 'react'
import {
  config, configuracionIncompleta, login, logout, procesarRetorno,
  getTokens, decodificarJwt, rolesDelUsuario, tieneRol,
} from './auth.js'
import {
  listarProductos, crearProducto, actualizarProducto, borrarProducto,
} from './api.js'

// Guard a nivel de módulo: en dev, React 18 (StrictMode) monta dos veces y el
// efecto correría dos veces; el code es de un solo uso y el 2º canje daría error.
let callbackProcesado = false

// Un guard de UI: muestra el contenido solo si el usuario tiene el rol.
function Guard({ tokens, rol, children }) {
  if (!tokens) return <p>Debes iniciar sesión.</p>
  if (rol && !tieneRol(tokens, rol)) return <p className="muted">Requiere rol: {rol}</p>
  return children
}

const VACIO = { nombre: '', precio: '' }

export default function App() {
  const [tokens, setTokens] = useState(getTokens())
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [productos, setProductos] = useState(null)
  const [nuevo, setNuevo] = useState(VACIO)
  const [editando, setEditando] = useState(null) // { id, nombre, precio }

  const faltantes = configuracionIncompleta()

  useEffect(() => {
    if (callbackProcesado) return
    callbackProcesado = true
    procesarRetorno().then((t) => t && setTokens(t)).catch((e) => setError(e.message))
  }, [])

  // Envuelve cada llamada: limpia mensajes, ejecuta y recarga la lista.
  async function llamar(fn, mensajeOk) {
    setError(null)
    setAviso(null)
    try {
      await fn()
      setProductos(await listarProductos())
      if (mensajeOk) setAviso(mensajeOk)
    } catch (e) {
      setError(e.message)
    }
  }

  const esEditor = tieneRol(tokens, 'editores')

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
      {aviso && <p className="aviso">{aviso}</p>}

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
            <button onClick={() => llamar(async () => {}, null)}>
              GET /productos
            </button>
            <button onClick={logout}>Cerrar sesión</button>
          </div>

          {/* Crear. El guard esconde el formulario a quien no es editor; la
              seguridad real la imponen el API Gateway y el backend con el 403. */}
          <section>
            <h2>Crear producto</h2>
            <Guard tokens={tokens} rol="editores">
              <form
                className="fila"
                onSubmit={(e) => {
                  e.preventDefault()
                  llamar(
                    () => crearProducto({ nombre: nuevo.nombre, precio: Number(nuevo.precio) }),
                    `Creado «${nuevo.nombre}» · POST /productos`,
                  ).then(() => setNuevo(VACIO))
                }}
              >
                <input
                  required
                  placeholder="Nombre"
                  value={nuevo.nombre}
                  onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })}
                />
                <input
                  required
                  type="number"
                  min="0"
                  placeholder="Precio"
                  value={nuevo.precio}
                  onChange={(e) => setNuevo({ ...nuevo, precio: e.target.value })}
                />
                <button type="submit">POST /productos</button>
              </form>
            </Guard>
          </section>

          <section>
            <h2>Productos</h2>
            {productos === null ? (
              <p className="muted">Pulsa «GET /productos» para cargarlos.</p>
            ) : productos.length === 0 ? (
              <p className="muted">No hay productos.</p>
            ) : (
              <table className="tabla">
                <thead>
                  <tr><th>ID</th><th>Nombre</th><th>Precio</th><th></th></tr>
                </thead>
                <tbody>
                  {productos.map((p) =>
                    editando?.id === p.id ? (
                      <tr key={p.id}>
                        <td>{p.id}</td>
                        <td>
                          <input
                            value={editando.nombre}
                            onChange={(e) => setEditando({ ...editando, nombre: e.target.value })}
                          />
                        </td>
                        <td>
                          <input
                            type="number"
                            min="0"
                            value={editando.precio}
                            onChange={(e) => setEditando({ ...editando, precio: e.target.value })}
                          />
                        </td>
                        <td className="acciones">
                          <button
                            onClick={() =>
                              llamar(
                                () => actualizarProducto(p.id, {
                                  nombre: editando.nombre,
                                  precio: Number(editando.precio),
                                }),
                                `Actualizado el ${p.id} · PUT /productos/${p.id}`,
                              ).then(() => setEditando(null))
                            }
                          >
                            Guardar
                          </button>
                          <button className="gris" onClick={() => setEditando(null)}>
                            Cancelar
                          </button>
                        </td>
                      </tr>
                    ) : (
                      <tr key={p.id}>
                        <td>{p.id}</td>
                        <td>{p.nombre}</td>
                        <td>{p.precio}</td>
                        <td className="acciones">
                          {esEditor ? (
                            <>
                              <button
                                onClick={() =>
                                  setEditando({ id: p.id, nombre: p.nombre, precio: String(p.precio) })
                                }
                              >
                                Editar
                              </button>
                              <button
                                className="rojo"
                                onClick={() =>
                                  llamar(
                                    () => borrarProducto(p.id),
                                    `Borrado el ${p.id} · DELETE /productos/${p.id}`,
                                  )
                                }
                              >
                                Borrar
                              </button>
                            </>
                          ) : (
                            <span className="muted">solo lectura</span>
                          )}
                        </td>
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            )}
          </section>

          <details>
            <summary>Ver los claims del token</summary>
            <pre>{JSON.stringify(decodificarJwt(tokens.access_token), null, 2)}</pre>
          </details>
        </>
      )}
    </main>
  )
}
