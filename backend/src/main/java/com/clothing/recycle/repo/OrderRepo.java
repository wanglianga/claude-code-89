package com.clothing.recycle.repo;

import com.clothing.recycle.model.RecycleOrder;
import com.clothing.recycle.model.OrderStatus;
import com.clothing.recycle.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepo extends JpaRepository<RecycleOrder, Long> {
    List<RecycleOrder> findByResidentOrderByCreatedAtDesc(User resident);
    List<RecycleOrder> findByCollectorOrderByCreatedAtDesc(User collector);
    List<RecycleOrder> findByStatusOrderByCreatedAtAsc(OrderStatus status);
    List<RecycleOrder> findAllByOrderByCreatedAtDesc();
    List<RecycleOrder> findByCommunityNameOrderByCreatedAtDesc(String communityName);
    long countByStatus(OrderStatus status);
}
