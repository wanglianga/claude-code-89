package com.clothing.recycle.controller;

import com.clothing.recycle.config.ViewMapper;
import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 公示端（免登录）：批次去向、捐赠照片、公益机构签收、再生处理量 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final BatchRepo batchRepo;
    private final OrderRepo orderRepo;
    private final SortReviewRepo sortRepo;
    private final UserRepo userRepo;
    private final PartnerRepo partnerRepo;
    private final ViewMapper vm;

    public PublicController(BatchRepo batchRepo, OrderRepo orderRepo, SortReviewRepo sortRepo,
                            UserRepo userRepo, PartnerRepo partnerRepo, ViewMapper vm) {
        this.batchRepo = batchRepo;
        this.orderRepo = orderRepo;
        this.sortRepo = sortRepo;
        this.userRepo = userRepo;
        this.partnerRepo = partnerRepo;
        this.vm = vm;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }

    /** 已进入公开环节的批次（集货中不对外公示）；要求隐藏信息的住户仅以匿名条目出现 */
    @GetMapping("/batches")
    public List<Map<String, Object>> batches() {
        return batchRepo.findByStatusNotOrderByCreatedAtDesc(BatchStatus.STAGED).stream()
                .map(b -> vm.batch(b, true)).toList();
    }

    /** 公示大盘数据 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        long orderCount = orderRepo.count();
        m.put("orderCount", orderCount);

        BigDecimal donatedWeight = BigDecimal.ZERO;
        BigDecimal recycledWeight = BigDecimal.ZERO;
        BigDecimal aidWeight = BigDecimal.ZERO;
        for (Batch b : batchRepo.findAll()) {
            if (b.getStatus() == BatchStatus.REJECTED || b.getStatus() == BatchStatus.STAGED) continue;
            if ("RECYCLE".equals(b.getBatchType()) || b.getStatus() == BatchStatus.RECYCLED) {
                recycledWeight = recycledWeight.add(b.getRecycledWeightKg() != null
                        ? b.getRecycledWeightKg() : b.getTotalWeightKg());
            } else {
                donatedWeight = donatedWeight.add(b.getTotalWeightKg());
                if (b.getStatus() == BatchStatus.AID_GIVEN) {
                    aidWeight = aidWeight.add(b.getTotalWeightKg());
                }
            }
        }
        m.put("donatedWeightKg", donatedWeight);
        m.put("recycledWeightKg", recycledWeight);
        m.put("aidWeightKg", aidWeight);
        m.put("publicBatchCount", batchRepo.findByStatusNotOrderByCreatedAtDesc(BatchStatus.STAGED).size());
        m.put("orgCount", userRepo.findByRole(Role.ORG).size());
        m.put("enterpriseCount", partnerRepo.findAll().stream().filter(p -> p.getType() == PartnerType.ENTERPRISE).count());
        m.put("schoolCount", partnerRepo.findAll().stream().filter(p -> p.getType() == PartnerType.SCHOOL).count());

        // 五分类分布（分拣复核口径）
        Map<String, Integer> categoryWeight = new LinkedHashMap<>();
        for (SortCategory c : SortCategory.values()) categoryWeight.put(c.name(), 0);
        Map<String, Long> categoryCount = new LinkedHashMap<>();
        sortRepo.findAll().forEach(r -> {
            categoryWeight.merge(r.getCategory().name(),
                    r.getWeightKg() == null ? 0 : r.getWeightKg().intValue(), Integer::sum);
            categoryCount.merge(r.getCategory().name(), 1L, Long::sum);
        });
        m.put("categoryWeightKg", categoryWeight);
        m.put("categoryCount", categoryCount);
        return m;
    }
}
