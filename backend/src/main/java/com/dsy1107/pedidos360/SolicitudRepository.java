package com.dsy1107.pedidos360;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// El REPOSITORIO: Spring Data genera la implementacion. No hay SQL escrito a mano.
public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    // Spring Data deduce la consulta del nombre: WHERE solicitante = ?
    List<Solicitud> findBySolicitante(String solicitante);
}
