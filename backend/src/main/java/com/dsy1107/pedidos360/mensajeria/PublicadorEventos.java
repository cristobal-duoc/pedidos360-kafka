package com.dsy1107.pedidos360.mensajeria;

import com.dsy1107.pedidos360.Solicitud;

// Lo unico que el negocio ve de la mensajeria. El controlador no sabe que existe
// RabbitMQ: solo avisa que algo paso.
public interface PublicadorEventos {

    void solicitudCreada(Solicitud solicitud);

    void solicitudResuelta(Solicitud solicitud);
}
