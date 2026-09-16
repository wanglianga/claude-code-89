package com.clothing.recycle.repo;

import com.clothing.recycle.model.AidValueRecord;
import com.clothing.recycle.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AidValueRepo extends JpaRepository<AidValueRecord, Long> {
    Optional<AidValueRecord> findByBatch(Batch batch);
}
