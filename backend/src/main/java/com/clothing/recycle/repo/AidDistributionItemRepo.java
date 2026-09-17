package com.clothing.recycle.repo;

import com.clothing.recycle.model.AidDistributionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AidDistributionItemRepo extends JpaRepository<AidDistributionItem, Long> {
    List<AidDistributionItem> findByDistributionIdOrderByIdAsc(Long distributionId);

    List<AidDistributionItem> findByOrderId(Long orderId);
}
