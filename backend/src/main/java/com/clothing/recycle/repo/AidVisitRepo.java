package com.clothing.recycle.repo;

import com.clothing.recycle.model.AidVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AidVisitRepo extends JpaRepository<AidVisit, Long> {
    List<AidVisit> findByFamilyIdOrderByVisitedAtDesc(Long familyId);
    List<AidVisit> findAllByOrderByVisitedAtDesc();
}
