package com.dsy1107.pedidos360;

import com.dsy1107.pedidos360.mensajeria.PublicadorEventos;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

// La autorizacion PRIMARIA la hace el API Gateway con authorization_scopes: una
// peticion sin el scope correcto nunca llega hasta aqui.
//
// Los @PreAuthorize son una SEGUNDA compuerta, por si alguien llamara al
// contenedor directo por su IP saltandose el gateway. Separan los dos papeles:
// el solicitante crea y edita sus solicitudes; el aprobador solo resuelve.
// Ademas, un solicitante solo ve y toca las SUYAS: el rol no basta, se compara
// el dueño de la solicitud con quien viene en el token.
@RestController
@RequestMapping("/solicitudes")
public class SolicitudController {

    private final SolicitudRepository repo;
    private final PublicadorEventos eventos;

    public SolicitudController(SolicitudRepository repo, PublicadorEventos eventos) {
        this.repo = repo;
        this.eventos = eventos;
    }

    // El email lo agrega la Lambda user-token-ms al access token; si faltara,
    // se cae al identificador interno del usuario.
    private static String quienEs(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return email != null ? email : jwt.getClaimAsString("username");
    }

    private static boolean esAprobador(Authentication auth) {
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_aprobadores"));
    }

    // El aprobador ve todas, para resolverlas; el solicitante, solo las suyas.
    @GetMapping
    public List<Solicitud> listar(Authentication auth, @AuthenticationPrincipal Jwt jwt) {
        return esAprobador(auth) ? repo.findAll() : repo.findBySolicitante(quienEs(jwt));
    }

    // --- lo que hace el SOLICITANTE ---

    @PostMapping
    @PreAuthorize("hasRole('solicitantes')")
    public Solicitud crear(@RequestBody Solicitud entrada, @AuthenticationPrincipal Jwt jwt) {
        // El solicitante y el estado no se aceptan del cuerpo: nadie crea
        // solicitudes a nombre de otro, ni nace aprobada.
        Solicitud s = repo.save(new Solicitud(quienEs(jwt), entrada.getMotivo(), entrada.getDias()));
        // Primero se guarda; despues se avisa. Si RabbitMQ falla, la solicitud ya existe.
        eventos.solicitudCreada(s);
        return s;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('solicitantes')")
    public ResponseEntity<Solicitud> editar(@PathVariable Long id, @RequestBody Solicitud entrada,
                                            @AuthenticationPrincipal Jwt jwt) {
        return repo.findById(id).map(s -> {
            // Nadie edita la solicitud de otro.
            if (!s.getSolicitante().equals(quienEs(jwt))) {
                return ResponseEntity.status(403).<Solicitud>build();
            }
            // Una solicitud ya resuelta no se toca.
            if (s.getEstado() != Solicitud.Estado.PENDIENTE) {
                return ResponseEntity.status(409).<Solicitud>build();
            }
            s.setMotivo(entrada.getMotivo());
            s.setDias(entrada.getDias());
            return ResponseEntity.ok(repo.save(s));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('solicitantes')")
    public ResponseEntity<Void> borrar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return repo.findById(id).map(s -> {
            if (!s.getSolicitante().equals(quienEs(jwt))) {
                return ResponseEntity.status(403).<Void>build();
            }
            // Borrar una resuelta haria desaparecer la decision del aprobador.
            if (s.getEstado() != Solicitud.Estado.PENDIENTE) {
                return ResponseEntity.status(409).<Void>build();
            }
            repo.delete(s);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- lo que hace el APROBADOR ---

    public record Decision(boolean aprobar, String comentario) {
    }

    @PutMapping("/{id}/decision")
    @PreAuthorize("hasRole('aprobadores')")
    public ResponseEntity<Solicitud> resolver(@PathVariable Long id,
                                              @RequestBody Decision decision,
                                              @AuthenticationPrincipal Jwt jwt) {
        return repo.findById(id).map(s -> {
            if (s.getEstado() != Solicitud.Estado.PENDIENTE) {
                return ResponseEntity.status(409).<Solicitud>build();
            }
            s.setEstado(decision.aprobar() ? Solicitud.Estado.APROBADA : Solicitud.Estado.RECHAZADA);
            s.setComentario(decision.comentario());
            s.setResueltaPor(quienEs(jwt));
            s.setResuelta(Instant.now());
            Solicitud resuelta = repo.save(s);
            eventos.solicitudResuelta(resuelta);
            return ResponseEntity.ok(resuelta);
        }).orElse(ResponseEntity.notFound().build());
    }
}
