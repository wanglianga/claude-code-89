package com.clothing.recycle.repo;

import com.clothing.recycle.model.Complaint;
import com.clothing.recycle.model.ComplaintStatus;
import com.clothing.recycle.model.RecycleOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepo extends JpaRepository<Complaint, Long> {
    List<Complaint> findAllByOrderByCreatedAtDesc();
    List<Complaint> findByResidentIdOrderByCreatedAtDesc(Long residentId);
    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);
    List<Complaint> findByOrderOrderByCreatedAtAsc(RecycleOrder order);
    long countByStatus(ComplaintStatus status);
}
