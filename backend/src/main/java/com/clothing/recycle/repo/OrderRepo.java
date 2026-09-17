package com.clothing.recycle.repo;

import com.clothing.recycle.model.RecycleOrder;
import com.clothing.recycle.model.OrderStatus;
import com.clothing.recycle.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderRepo extends JpaRepository<RecycleOrder, Long> {
    List<RecycleOrder> findByResidentOrderByCreatedAtDesc(User resident);
    List<RecycleOrder> findByCollectorOrderByCreatedAtDesc(User collector);
    List<RecycleOrder> findByStatusOrderByCreatedAtAsc(OrderStatus status);
    List<RecycleOrder> findAllByOrderByCreatedAtDesc();
    List<RecycleOrder> findByCommunityNameOrderByCreatedAtDesc(String communityName);
    long countByStatus(OrderStatus status);

    /** 定向匹配/签收时锁定来源回收单（SELECT ... FOR UPDATE），按 id 排序避免并发交叉锁死锁 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RecycleOrder o where o.id in :ids order by o.id asc")
    List<RecycleOrder> findByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
