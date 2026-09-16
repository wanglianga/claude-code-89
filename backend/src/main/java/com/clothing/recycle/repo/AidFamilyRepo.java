package com.clothing.recycle.repo;

import com.clothing.recycle.model.AidFamily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AidFamilyRepo extends JpaRepository<AidFamily, Long> {
    List<AidFamily> findAllByOrderByCreatedAtDesc();
}
