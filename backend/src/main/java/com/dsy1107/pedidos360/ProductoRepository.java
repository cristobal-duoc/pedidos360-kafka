package com.dsy1107.pedidos360;

import org.springframework.data.jpa.repository.JpaRepository;

// El REPOSITORIO: Spring Data implementa solo el CRUD (findAll, save, deleteById...).
public interface ProductoRepository extends JpaRepository<Producto, Long> {
}
