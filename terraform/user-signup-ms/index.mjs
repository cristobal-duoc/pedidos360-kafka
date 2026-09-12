// Trigger Post Confirmation. Se ejecuta cuando una persona que se registró sola
// confirma su cuenta con el código del correo.
//
// Sin esto, quien se registra queda sin grupo: su token sale sin scopes y la API le
// responde 403 a todo, hasta el GET. Aquí entra a "solicitantes", el papel de
// menor privilegio: cualquiera puede pedir vacaciones, pero el permiso de aprobar
// lo asigna a mano un administrador.
import {
  CognitoIdentityProviderClient,
  AdminAddUserToGroupCommand,
} from '@aws-sdk/client-cognito-identity-provider';

const cognito = new CognitoIdentityProviderClient({});
const GRUPO_INICIAL = 'solicitantes';

export const handler = async (event) => {
  // El mismo trigger se dispara al recuperar la contraseña: ahí no hay que tocar nada.
  if (event.triggerSource !== 'PostConfirmation_ConfirmSignUp') return event;

  await cognito.send(new AdminAddUserToGroupCommand({
    UserPoolId: event.userPoolId,
    Username: event.userName,
    GroupName: GRUPO_INICIAL,
  }));

  console.log(JSON.stringify({ usuario: event.userName, grupo: GRUPO_INICIAL }));
  return event;
};
