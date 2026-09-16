package com.clothing.recycle.repo;

import com.clothing.recycle.model.AidIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AidIssueRepo extends JpaRepository<AidIssue, Long> {
    List<AidIssue> findAllByOrderByCreatedAtDesc();
    List<AidIssue> findByDistributionIdOrderByCreatedAtAsc(Long distributionId);
}
