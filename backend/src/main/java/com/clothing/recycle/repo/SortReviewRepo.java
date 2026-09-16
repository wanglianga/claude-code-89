package com.clothing.recycle.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.clothing.recycle.model.RecycleOrder;
import com.clothing.recycle.model.SortReview;

import java.util.Optional;

public interface SortReviewRepo extends JpaRepository<SortReview, Long> {
    Optional<SortReview> findByOrder(RecycleOrder order);
}
