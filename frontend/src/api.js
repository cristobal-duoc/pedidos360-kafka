// Pasos 10-11: la app llama a la API con el access token; la API responde.
import { getAccessToken } from './auth.js'

const API = import.meta.env.VITE_API_URL

async function pedir(metodo, ruta, cuerpo) {
  const r = await fetch(`${API}${ruta}`, {
    method: metodo,
    headers: {
      Authorization: `Bearer ${getAccessToken()}`,
      ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
    },
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
  })
  if (r.status === 401) throw new Error('401: token inválido, ausente o expirado')
  if (r.status === 403) throw new Error('403: no tienes el scope para esta operación')
  if (!r.ok) throw new Error(`Error ${r.status}`)
  return r.status === 204 ? null : r.json()
}

export const listarProductos = () => pedir('GET', '/productos')
export const crearProducto = (p) => pedir('POST', '/productos', p)
