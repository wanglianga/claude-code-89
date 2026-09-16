package com.clothing.recycle.controller;

import com.clothing.recycle.config.ViewMapper;
import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import com.clothing.recycle.security.CurrentUser;
import com.clothing.recycle.service.RecycleService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 企业公益合作、学校捐衣活动 */
@RestController
@RequestMapping("/api")
public class PartnerController {

    private final PartnerRepo partnerRepo;
    private final RecycleService svc;
    private final ViewMapper vm;
    private final CurrentUser cu;

    public PartnerController(PartnerRepo partnerRepo,
                             RecycleService svc, ViewMapper vm, CurrentUser cu) {
        this.partnerRepo = partnerRepo;
        this.svc = svc;
        this.vm = vm;
        this.cu = cu;
    }

    // ---------- 合作活动 ----------

    @GetMapping("/partners")
    public List<Map<String, Object>> partners(@RequestParam(required = false) String type) {
        var stream = partnerRepo.findAllByOrderByCreatedAtDesc().stream();
        if (type != null && !type.isBlank()) {
            stream = stream.filter(p -> p.getType().name().equals(type));
        }
        return stream.map(vm::partner).toList();
    }

    @PostMapping("/partners")
    public Map<String, Object> createPartner(@RequestBody Map<String, Object> dto) {
        cu.require(Role.ADMIN, Role.COMMUNITY);
        Partner p = new Partner();
        applyPartner(p, dto);
        return vm.partner(partnerRepo.save(p));
    }

    @PutMapping("/partners/{id}")
    public Map<String, Object> updatePartner(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        cu.require(Role.ADMIN, Role.COMMUNITY);
        Partner p = partnerRepo.findById(id).orElseThrow();
        applyPartner(p, dto);
        return vm.partner(partnerRepo.save(p));
    }

    private void applyPartner(Partner p, Map<String, Object> dto) {
        p.setName(dto.get("name").toString());
        p.setType(PartnerType.valueOf(dto.get("type").toString()));
        p.setProjectName((String) dto.get("projectName"));
        p.setContactName((String) dto.get("contactName"));
        p.setContactPhone((String) dto.get("contactPhone"));
        if (dto.get("startDate") != null && !dto.get("startDate").toString().isBlank()) {
            p.setStartDate(LocalDate.parse(dto.get("startDate").toString()));
        }
        if (dto.get("endDate") != null && !dto.get("endDate").toString().isBlank()) {
            p.setEndDate(LocalDate.parse(dto.get("endDate").toString()));
        }
        if (dto.get("targetKg") != null) p.setTargetKg(new java.math.BigDecimal(dto.get("targetKg").toString()));
        p.setStatus(dto.getOrDefault("status", "ACTIVE").toString());
        p.setDescription((String) dto.get("description"));
    }

}
