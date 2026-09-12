// Trigger Pre Token Generation (V2_0). Se ejecuta ANTES de que Cognito firme el
// access token: traduce los grupos del usuario a scopes, que es lo que el API
// Gateway compara contra authorization_scopes de cada ruta.
//
// El solicitante crea y edita sus solicitudes; el aprobador solo resuelve. Por
// eso ninguno de los dos tiene todos los scopes.
const SCOPES_POR_GRUPO = {
  solicitantes: ['solicitudes/read', 'solicitudes/write'],
  aprobadores: ['solicitudes/read', 'solicitudes/approve'],
};

export const handler = async (event) => {
  const grupos = event.request.groupConfiguration?.groupsToOverride ?? [];
  const scopes = [...new Set(grupos.flatMap((g) => SCOPES_POR_GRUPO[g] ?? []))];

  // El email no viaja en el access token por defecto: lo agregamos para que el
  // backend pueda registrar quien pidio y quien resolvio cada solicitud.
  const email = event.request.userAttributes?.email;

  // Cognito protege el claim "scope": obliga a usar scopesToAdd (no claimsToAdd).
  event.response = {
    claimsAndScopeOverrideDetails: {
      accessTokenGeneration: {
        scopesToAdd: scopes,
        ...(email ? { claimsToAddOrOverride: { email } } : {}),
      },
    },
  };

  console.log(JSON.stringify({ usuario: event.userName, grupos, scopes }));
  return event;
};
