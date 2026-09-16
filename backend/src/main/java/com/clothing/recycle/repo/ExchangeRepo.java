package com.clothing.recycle.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.clothing.recycle.model.ExchangeOrder;
import com.clothing.recycle.model.User;

import java.util.List;

public interface ExchangeRepo extends JpaRepository<ExchangeOrder, Long> {
    List<ExchangeOrder> findByResidentOrderByCreatedAtDesc(User resident);
    List<ExchangeOrder> findAllByOrderByCreatedAtDesc();
}
