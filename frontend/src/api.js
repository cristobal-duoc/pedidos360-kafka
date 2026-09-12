// Pasos 10-11: la app llama a la API con el access token; la API responde.
import { getAccessToken, getTokens, renovarTokens, segundosRestantes } from './auth.js'

const API = import.meta.env.VITE_API_URL

function llamar(metodo, ruta, cuerpo) {
  return fetch(`${API}${ruta}`, {
    method: metodo,
    headers: {
      Authorization: `Bearer ${getAccessToken()}`,
      ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
    },
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
  })
}

async function pedir(metodo, ruta, cuerpo) {
  // Si al token le queda menos de un minuto, se renueva ANTES de llamar.
  if (segundosRestantes(getTokens()) < 60) await renovarTokens()

  let r = await llamar(metodo, ruta, cuerpo)
  // Un 401 puede ser un token que venció justo en camino: se renueva una vez y se
  // reintenta. Si vuelve a fallar, el problema no es la vigencia.
  if (r.status === 401 && (await renovarTokens())) {
    r = await llamar(metodo, ruta, cuerpo)
  }

  if (r.status === 401) throw new Error('401: token inválido, ausente o expirado')
  if (r.status === 403) throw new Error('403: no tienes el scope para esta operación')
  if (r.status === 409) throw new Error('409: la solicitud ya estaba resuelta')
  if (!r.ok) throw new Error(`Error ${r.status}`)
  // El DELETE responde 204, sin cuerpo.
  return r.status === 204 ? null : r.json()
}

// Lo que puede hacer el SOLICITANTE (scope solicitudes/write)
export const listarSolicitudes = () => pedir('GET', '/solicitudes')
export const crearSolicitud = (s) => pedir('POST', '/solicitudes', s)
export const editarSolicitud = (id, s) => pedir('PUT', `/solicitudes/${id}`, s)
export const borrarSolicitud = (id) => pedir('DELETE', `/solicitudes/${id}`)

// Lo que puede hacer el APROBADOR (scope solicitudes/approve)
export const resolverSolicitud = (id, aprobar, comentario) =>
  pedir('PUT', `/solicitudes/${id}/decision`, { aprobar, comentario })
