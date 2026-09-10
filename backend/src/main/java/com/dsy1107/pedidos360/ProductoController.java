package com.dsy1107.pedidos360;

import java.util.List;
import org.springframework.web.bind.annotation.*;

// El CRUD, SIN @PreAuthorize: el backend NO decide permisos.
// La autorizacion (el 403) la hace el API Gateway con authorization_scopes.
// El backend solo AUTENTICA (SecurityConfig) y ejecuta la logica de negocio.
@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository repo;

    public ProductoController(ProductoRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Producto> listar() {
        return repo.findAll();
    }

    @PostMapping
    public Producto crear(@RequestBody Producto p) {
        return repo.save(p);
    }

    @PutMapping("/{id}")
    public Producto actualizar(@PathVariable Long id, @RequestBody Producto p) {
        return repo.findById(id).map(existente -> {
            existente.setNombre(p.getNombre());
            existente.setPrecio(p.getPrecio());
            return repo.save(existente);
        }).orElseGet(() -> repo.save(p));
    }

    @DeleteMapping("/{id}")
    public void borrar(@PathVariable Long id) {
        repo.deleteById(id);
    }
}
