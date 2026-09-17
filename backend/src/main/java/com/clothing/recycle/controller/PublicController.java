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
    private final AidDistributionRepo distRepo;
    private final AidDistributionItemRepo distItemRepo;
    private final ViewMapper vm;

    public PublicController(BatchRepo batchRepo, OrderRepo orderRepo, SortReviewRepo sortRepo,
                            UserRepo userRepo, PartnerRepo partnerRepo, AidDistributionRepo distRepo,
                            AidDistributionItemRepo distItemRepo, ViewMapper vm) {
        this.batchRepo = batchRepo;
        this.orderRepo = orderRepo;
        this.sortRepo = sortRepo;
        this.userRepo = userRepo;
        this.partnerRepo = partnerRepo;
        this.distRepo = distRepo;
        this.distItemRepo = distItemRepo;
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
    /** 定向发放公示：仅批次/公益项目/机构签收/发放数量与完成状态，
     *  不含姓名、住址、联系方式、困难细节与领取人照片；匿名家庭统一显示“定向发放完毕”。 */
    @GetMapping("/aid-summary")
    public List<Map<String, Object>> aidSummary() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Batch b : batchRepo.findAllByOrderByCreatedAtDesc()) {
            List<AidDistribution> ds = distRepo.findAllByOrderByCreatedAtDesc().stream()
                    .filter(d -> d.getBatch().getId().equals(b.getId())).toList();
            if (ds.isEmpty()) continue;
            // 公示只统计真实已发放数量：按发放明细的已签收件数汇总（取消/退回/换货旧任务不计）
            int handed = ds.stream().mapToInt(this::distributedQuantity).sum();
            boolean allHidden = ds.stream().allMatch(d -> d.getFamily().isPublicHidden());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("batchCode", b.getCode());
            m.put("batchType", b.getBatchType());
            m.put("projectName", b.getProjectName());
            m.put("designatedTarget", b.getDesignatedTarget());
            m.put("organizationName", b.getOrganization() == null ? null : b.getOrganization().getOrganizationName());
            m.put("signPhotoUrl", com.clothing.recycle.config.ViewMapper.photoUrl(b.getSignPhotoPath()));
            m.put("receiverName", b.getReceiverName());
            m.put("signedAt", b.getSignedAt());
            m.put("distributionCount", ds.size());
            m.put("handedQuantity", handed);
            m.put("completed", b.getStatus() == BatchStatus.AID_GIVEN);
            m.put("statusLabel", allHidden ? "定向发放完毕（领取人信息依其意愿不予公示）"
                    : "定向发放完毕");
            m.put("handedAt", b.getAidGivenAt());
            out.add(m);
        }
        return out;
    }

    /** 任务真实已发放件数：优先按来源单发放明细汇总；
     *  无明细的历史数据回退到已完成任务的实领数（取消/退回不计）。 */
    private int distributedQuantity(AidDistribution d) {
        List<AidDistributionItem> items = distItemRepo.findByDistributionIdOrderByIdAsc(d.getId());
        if (items.isEmpty()) {
            boolean done = d.getStatus() == AidDistributionStatus.HANDED_OUT
                    || d.getStatus() == AidDistributionStatus.EXCHANGED;
            return done && d.getActualQuantity() != null ? d.getActualQuantity() : 0;
        }
        return items.stream().mapToInt(AidDistributionItem::getDistributedQuantity).sum();
    }

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
