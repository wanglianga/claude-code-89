package com.clothing.recycle.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.clothing.recycle.model.PickupRecord;
import com.clothing.recycle.model.RecycleOrder;

import java.util.Optional;

public interface PickupRepo extends JpaRepository<PickupRecord, Long> {
    Optional<PickupRecord> findByOrder(RecycleOrder order);
}
