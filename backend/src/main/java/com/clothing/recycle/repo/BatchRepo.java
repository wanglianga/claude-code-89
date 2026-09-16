package com.clothing.recycle.repo;

import com.clothing.recycle.model.Batch;
import com.clothing.recycle.model.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchRepo extends JpaRepository<Batch, Long> {
    List<Batch> findByStatusNotOrderByCreatedAtDesc(BatchStatus status);
    List<Batch> findAllByOrderByCreatedAtDesc();
    List<Batch> findByStatusOrderByCreatedAtDesc(BatchStatus status);
    List<Batch> findBySourceBatchOrderByCreatedAtAsc(Batch source);
}
