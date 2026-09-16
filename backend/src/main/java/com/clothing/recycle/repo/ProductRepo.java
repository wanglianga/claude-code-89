package com.clothing.recycle.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.clothing.recycle.model.Product;

import java.util.List;

public interface ProductRepo extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderByPointsCostAsc();
}
