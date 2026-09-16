package com.clothing.recycle.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.clothing.recycle.model.PointsLedger;
import com.clothing.recycle.model.User;

import java.util.List;
import java.util.Optional;

public interface PointsLedgerRepo extends JpaRepository<PointsLedger, Long> {
    List<PointsLedger> findByResidentOrderByCreatedAtDesc(User resident);
    Optional<PointsLedger> findFirstByResidentOrderByCreatedAtDesc(User resident);
}
