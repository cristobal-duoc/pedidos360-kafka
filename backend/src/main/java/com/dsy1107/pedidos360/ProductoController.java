package com.dsy1107.pedidos360;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// La autorizacion PRIMARIA la hace el API Gateway con authorization_scopes: una
// peticion sin el scope correcto nunca llega hasta aqui.
//
// Los @PreAuthorize de abajo son una SEGUNDA compuerta, por si alguien llamara al
// contenedor directo por su IP saltandose el gateway. Leer esta permitido a
// cualquier usuario autenticado; escribir exige el rol "editores", que sale del
// claim cognito:groups (ver SecurityConfig.convertidorDeRoles).
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
    @PreAuthorize("hasRole('editores')")
    public Producto crear(@RequestBody Producto p) {
        return repo.save(p);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('editores')")
    public Producto actualizar(@PathVariable Long id, @RequestBody Producto p) {
        return repo.findById(id).map(existente -> {
            existente.setNombre(p.getNombre());
            existente.setPrecio(p.getPrecio());
            return repo.save(existente);
        }).orElseGet(() -> repo.save(p));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('editores')")
    public void borrar(@PathVariable Long id) {
        repo.deleteById(id);
    }
}
