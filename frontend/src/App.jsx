import { useEffect, useState } from 'react'
import {
  config, configuracionIncompleta, login, logout, procesarRetorno,
  getTokens, decodificarJwt, rolesDelUsuario, scopesDelUsuario, tieneScope,
  renovarTokens, segundosRestantes,
} from './auth.js'
import {
  listarSolicitudes, crearSolicitud, editarSolicitud, borrarSolicitud,
  resolverSolicitud,
} from './api.js'

// Guard a nivel de módulo: en dev, React 18 (StrictMode) monta dos veces y el
// efecto correría dos veces; el code es de un solo uso y el 2º canje daría error.
let callbackProcesado = false

// Un guard de UI: muestra el contenido solo si el token trae el scope. Usa el mismo
// scope que exige la ruta en el API Gateway, así la interfaz ofrece justo lo que la
// API va a aceptar. Es comodidad, no seguridad: el 403 lo imponen gateway y backend.
function Guard({ tokens, scope, children }) {
  if (!tokens) return <p>Debes iniciar sesión.</p>
  if (scope && !tieneScope(tokens, scope)) return <p className="muted">Requiere el scope {scope}</p>
  return children
}

function Estado({ valor }) {
  const clase = { PENDIENTE: 'pendiente', APROBADA: 'aprobada', RECHAZADA: 'rechazada' }[valor]
  return <span className={`estado ${clase}`}>{valor}</span>
}

const VACIA = { motivo: '', dias: '' }

export default function App() {
  const [tokens, setTokens] = useState(getTokens())
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [solicitudes, setSolicitudes] = useState(null)
  const [nueva, setNueva] = useState(VACIA)
  const [editando, setEditando] = useState(null)   // { id, motivo, dias }
  const [comentarios, setComentarios] = useState({}) // { [id]: texto }

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
      setSolicitudes(await listarSolicitudes())
      if (mensajeOk) setAviso(mensajeOk)
    } catch (e) {
      setError(e.message)
    } finally {
      // api.js pudo haber renovado el token por el camino: se refleja en pantalla.
      setTokens(getTokens())
    }
  }

  async function renovarAhora() {
    setError(null)
    const nuevos = await renovarTokens()
    setTokens(nuevos)
    if (nuevos) setAviso('Token renovado con el refresh_token, sin volver a iniciar sesión.')
    else setError('El refresh_token ya no es válido: inicia sesión de nuevo.')
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
  const scopes = scopesDelUsuario(tokens)
  // Qué ofrecer se decide con los scopes: son los que exige cada ruta del gateway.
  const esSolicitante = tieneScope(tokens, 'solicitudes/write')
  const esAprobador = tieneScope(tokens, 'solicitudes/approve')
  const minutos = Math.max(0, Math.floor(segundosRestantes(tokens) / 60))

  return (
    <main>
      <h1>Pedidos360 · Solicitudes de vacaciones</h1>
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
            <p>
              <strong>Scopes (scope):</strong> {scopes.join(', ') || '(ninguno)'}
              {esSolicitante && <span className="papel"> puede crear y editar</span>}
              {esAprobador && <span className="papel"> puede aprobar y rechazar</span>}
            </p>
            <p className="muted">El acceso vence en {minutos} min y se renueva solo.</p>
          </section>

          <div className="botones">
            <button onClick={() => llamar(async () => {}, null)}>
              GET /solicitudes
            </button>
            <button className="gris" onClick={renovarAhora}>Renovar token</button>
            <button onClick={logout}>Cerrar sesión</button>
          </div>

          <section>
            <h2>Nueva solicitud</h2>
            <Guard tokens={tokens} scope="solicitudes/write">
              <form
                className="fila"
                onSubmit={(e) => {
                  e.preventDefault()
                  llamar(
                    () => crearSolicitud({ motivo: nueva.motivo, dias: Number(nueva.dias) }),
                    `Solicitud enviada · POST /solicitudes`,
                  ).then(() => setNueva(VACIA))
                }}
              >
                <input
                  required
                  placeholder="Motivo (ej: vacaciones de invierno)"
                  value={nueva.motivo}
                  onChange={(e) => setNueva({ ...nueva, motivo: e.target.value })}
                />
                <input
                  required
                  type="number"
                  min="1"
                  placeholder="Días"
                  value={nueva.dias}
                  onChange={(e) => setNueva({ ...nueva, dias: e.target.value })}
                />
                <button type="submit">POST /solicitudes</button>
              </form>
            </Guard>
          </section>

          <section>
            <h2>Solicitudes</h2>
            {solicitudes === null ? (
              <p className="muted">Pulsa «GET /solicitudes» para cargarlas.</p>
            ) : solicitudes.length === 0 ? (
              <p className="muted">No hay solicitudes.</p>
            ) : (
              <table className="tabla">
                <thead>
                  <tr>
                    <th>ID</th><th>Solicitante</th><th>Motivo</th><th>Días</th>
                    <th>Estado</th><th>Resolución</th><th></th>
                  </tr>
                </thead>
                <tbody>
                  {solicitudes.map((s) =>
                    editando?.id === s.id ? (
                      <tr key={s.id}>
                        <td>{s.id}</td>
                        <td className="muted">{s.solicitante}</td>
                        <td>
                          <input
                            value={editando.motivo}
                            onChange={(e) => setEditando({ ...editando, motivo: e.target.value })}
                          />
                        </td>
                        <td>
                          <input
                            type="number"
                            min="1"
                            value={editando.dias}
                            onChange={(e) => setEditando({ ...editando, dias: e.target.value })}
                          />
                        </td>
                        <td><Estado valor={s.estado} /></td>
                        <td></td>
                        <td className="acciones">
                          <button
                            onClick={() =>
                              llamar(
                                () => editarSolicitud(s.id, {
                                  motivo: editando.motivo,
                                  dias: Number(editando.dias),
                                }),
                                `Editada la ${s.id} · PUT /solicitudes/${s.id}`,
                              ).then(() => setEditando(null))
                            }
                          >
                            Guardar
                          </button>
                          <button className="gris" onClick={() => setEditando(null)}>Cancelar</button>
                        </td>
                      </tr>
                    ) : (
                      <tr key={s.id}>
                        <td>{s.id}</td>
                        <td className="muted">{s.solicitante}</td>
                        <td>{s.motivo}</td>
                        <td>{s.dias}</td>
                        <td><Estado valor={s.estado} /></td>
                        <td className="muted">
                          {s.estado === 'PENDIENTE'
                            ? '—'
                            : <>«{s.comentario}»<br /><small>{s.resueltaPor}</small></>}
                        </td>
                        <td className="acciones">
                          {s.estado !== 'PENDIENTE' ? (
                            <span className="muted">resuelta</span>
                          ) : esAprobador ? (
                            <>
                              <input
                                className="comentario"
                                placeholder="Comentario"
                                value={comentarios[s.id] ?? ''}
                                onChange={(e) =>
                                  setComentarios({ ...comentarios, [s.id]: e.target.value })
                                }
                              />
                              <button
                                onClick={() =>
                                  llamar(
                                    () => resolverSolicitud(s.id, true, comentarios[s.id] ?? ''),
                                    `Aprobada la ${s.id} · PUT /solicitudes/${s.id}/decision`,
                                  )
                                }
                              >
                                Aprobar
                              </button>
                              <button
                                className="rojo"
                                onClick={() =>
                                  llamar(
                                    () => resolverSolicitud(s.id, false, comentarios[s.id] ?? ''),
                                    `Rechazada la ${s.id} · PUT /solicitudes/${s.id}/decision`,
                                  )
                                }
                              >
                                Rechazar
                              </button>
                            </>
                          ) : esSolicitante ? (
                            <>
                              <button
                                onClick={() =>
                                  setEditando({ id: s.id, motivo: s.motivo, dias: String(s.dias) })
                                }
                              >
                                Editar
                              </button>
                              <button
                                className="rojo"
                                onClick={() =>
                                  llamar(
                                    () => borrarSolicitud(s.id),
                                    `Eliminada la ${s.id} · DELETE /solicitudes/${s.id}`,
                                  )
                                }
                              >
                                Eliminar
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
