package com.clothing.recycle.repo;

import com.clothing.recycle.model.AidDistribution;
import com.clothing.recycle.model.AidFamily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AidDistributionRepo extends JpaRepository<AidDistribution, Long> {
    List<AidDistribution> findByFamilyOrderByCreatedAtDesc(AidFamily family);
    List<AidDistribution> findAllByOrderByCreatedAtDesc();
    long countByFamilyAndStatusIn(AidFamily family,
                                  List<com.clothing.recycle.model.AidDistributionStatus> statuses);
}
