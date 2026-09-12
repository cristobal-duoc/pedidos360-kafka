package com.dsy1107.pedidos360;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

// La ENTIDAD: una solicitud de vacaciones. JPA crea la tabla a partir de esta
// clase (ddl-auto=update).
//
// El ciclo de vida lo define el estado: nace PENDIENTE cuando la crea un
// solicitante, y solo un aprobador puede llevarla a APROBADA o RECHAZADA,
// siempre dejando un comentario.
@Entity
public class Solicitud {

    public enum Estado { PENDIENTE, APROBADA, RECHAZADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quien la pidió. NO llega del formulario: se toma del token, para que
    // nadie pueda crear solicitudes a nombre de otro.
    private String solicitante;

    private String motivo;

    private Integer dias;

    @Enumerated(EnumType.STRING)
    private Estado estado = Estado.PENDIENTE;

    // Lo escribe el aprobador al resolver.
    private String comentario;

    private String resueltaPor;

    private Instant creada = Instant.now();

    private Instant resuelta;

    public Solicitud() {
    }

    public Solicitud(String solicitante, String motivo, Integer dias) {
        this.solicitante = solicitante;
        this.motivo = motivo;
        this.dias = dias;
    }

    public Long getId() {
        return id;
    }

    public String getSolicitante() {
        return solicitante;
    }

    public void setSolicitante(String solicitante) {
        this.solicitante = solicitante;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Integer getDias() {
        return dias;
    }

    public void setDias(Integer dias) {
        this.dias = dias;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public String getResueltaPor() {
        return resueltaPor;
    }

    public void setResueltaPor(String resueltaPor) {
        this.resueltaPor = resueltaPor;
    }

    public Instant getCreada() {
        return creada;
    }

    public void setCreada(Instant creada) {
        this.creada = creada;
    }

    public Instant getResuelta() {
        return resuelta;
    }

    public void setResuelta(Instant resuelta) {
        this.resuelta = resuelta;
    }
}
