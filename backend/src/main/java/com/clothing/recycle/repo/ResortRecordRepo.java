package com.clothing.recycle.repo;

import com.clothing.recycle.model.Batch;
import com.clothing.recycle.model.ResortRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResortRecordRepo extends JpaRepository<ResortRecord, Long> {
    List<ResortRecord> findByRejectedBatchOrderByCreatedAtAsc(Batch batch);
}
