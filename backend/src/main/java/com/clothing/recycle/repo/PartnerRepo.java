package com.clothing.recycle.repo;

import com.clothing.recycle.model.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerRepo extends JpaRepository<Partner, Long> {
    List<Partner> findAllByOrderByCreatedAtDesc();
}
