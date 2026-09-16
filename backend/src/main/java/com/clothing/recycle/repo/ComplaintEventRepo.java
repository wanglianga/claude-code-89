package com.clothing.recycle.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.clothing.recycle.model.ComplaintEvent;

public interface ComplaintEventRepo extends JpaRepository<ComplaintEvent, Long> {
}
