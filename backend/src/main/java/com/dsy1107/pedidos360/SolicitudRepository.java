package com.dsy1107.pedidos360;

import org.springframework.data.jpa.repository.JpaRepository;

// El REPOSITORIO: Spring Data genera la implementacion. No hay SQL escrito a mano.
public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
}
