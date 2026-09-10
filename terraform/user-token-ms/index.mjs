// user-token-ms · trigger Pre Token Generation V2_0 de Cognito.
// Se ejecuta despues de autenticar y ANTES de firmar los tokens. Traduce los
// GRUPOS del usuario a SCOPES y los inyecta en el access token. Esto es TODO
// el codigo de autorizacion del sistema: el backend no tiene ni un if de permisos.

const SCOPES_POR_GRUPO = {
  lectores: ['productos/read'],
  editores: ['productos/read', 'productos/write'],
};

export const handler = async (event) => {
  const grupos = event.request.groupConfiguration?.groupsToOverride ?? [];
  const scopes = [...new Set(grupos.flatMap((g) => SCOPES_POR_GRUPO[g] ?? []))];

  // Cognito protege el claim "scope": obliga a usar scopesToAdd (no claimsToAdd).
  event.response = {
    claimsAndScopeOverrideDetails: {
      accessTokenGeneration: { scopesToAdd: scopes },
    },
  };

  console.log(JSON.stringify({ usuario: event.userName, grupos, scopes }));
  return event;
};
