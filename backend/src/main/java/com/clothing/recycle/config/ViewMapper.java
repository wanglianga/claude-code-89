package com.clothing.recycle.config;

import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 实体 -> 前端 JSON 视图（在事务内读取懒加载关联，避免 Hibernate 代理序列化） */
@Component
@Transactional(readOnly = true)
public class ViewMapper {

    private final PickupRepo pickupRepo;
    private final SortReviewRepo sortRepo;
    private final ComplaintRepo complaintRepo;
    private final PointsLedgerRepo ledgerRepo;
    private final BatchRepo batchRepo;
    private final ResortRecordRepo resortRecordRepo;

    public ViewMapper(PickupRepo pickupRepo, SortReviewRepo sortRepo,
                      ComplaintRepo complaintRepo, PointsLedgerRepo ledgerRepo,
                      BatchRepo batchRepo, ResortRecordRepo resortRecordRepo) {
        this.pickupRepo = pickupRepo;
        this.sortRepo = sortRepo;
        this.complaintRepo = complaintRepo;
        this.ledgerRepo = ledgerRepo;
        this.batchRepo = batchRepo;
        this.resortRecordRepo = resortRecordRepo;
    }

    public static String photoUrl(String path) {
        return path == null ? null : "/uploads/" + path;
    }

    public Map<String, Object> user(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("displayName", u.getDisplayName());
        m.put("role", u.getRole().name());
        m.put("roleLabel", roleLabel(u.getRole()));
        m.put("phone", u.getPhone());
        m.put("communityName", u.getCommunityName());
        m.put("organizationName", u.getOrganizationName());
        if (u.getRole() == Role.RESIDENT) {
            m.put("pointsBalance", ledgerRepo.findFirstByResidentOrderByCreatedAtDesc(u)
                    .map(PointsLedger::getBalanceAfter).orElse(0));
        }
        return m;
    }

    public static String roleLabel(Role role) {
        return switch (role) {
            case RESIDENT -> "居民";
            case COLLECTOR -> "回收员";
            case SORTER -> "分拣中心";
            case COMMUNITY -> "社区";
            case ORG -> "公益机构";
            case FINANCE -> "积分财务";
            case ADMIN -> "平台管理员";
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> order(RecycleOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("code", o.getCode());
        m.put("communityName", o.getCommunityName());
        m.put("itemCount", o.getItemCount());
        m.put("categories", o.getCategories());
        m.put("washed", o.isWashed());
        m.put("hasShoesBagsBedding", o.isHasShoesBagsBedding());
        m.put("address", o.getAddress());
        m.put("timeSlot", o.getTimeSlot());
        m.put("donateWanted", o.isDonateWanted());
        m.put("status", o.getStatus().name());
        m.put("createdAt", o.getCreatedAt());
        m.put("assignedAt", o.getAssignedAt());
        m.put("pickedAt", o.getPickedAt());
        m.put("sortedAt", o.getSortedAt());
        m.put("pickupWeight", o.getPickupWeight());
        m.put("publicHidden", o.isPublicHidden());
        m.put("resident", o.getResident() == null ? null : user(o.getResident()));
        m.put("collector", o.getCollector() == null ? null : user(o.getCollector()));
        m.put("partner", o.getPartner() == null ? null : partner(o.getPartner()));

        pickupRepo.findByOrder(o).ifPresent(p -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("weightKg", p.getWeightKg());
            pm.put("photoUrl", photoUrl(p.getPhotoPath()));
            pm.put("preCategories", p.getPreCategories());
            pm.put("arrivedLate", p.isArrivedLate());
            pm.put("arrivedAt", p.getArrivedAt());
            pm.put("confirmOption", p.getConfirmOption());
            pm.put("residentConfirmed", p.isResidentConfirmed());
            pm.put("note", p.getNote());
            m.put("pickup", pm);
        });
        sortRepo.findByOrder(o).ifPresent(r -> m.put("sort", sort(r)));
        if (o.getBatch() != null) {
            Batch ob = o.getBatch();
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", ob.getId());
            bm.put("code", ob.getCode());
            bm.put("batchType", ob.getBatchType());
            bm.put("projectName", ob.getProjectName());
            bm.put("recyclerName", ob.getRecyclerName());
            bm.put("status", ob.getStatus().name());
            bm.put("totalWeightKg", ob.getTotalWeightKg());
            bm.put("signedAt", ob.getSignedAt());
            bm.put("rejectReasonTypeLabel", rejectTypeLabel(ob.getRejectReasonType()));
            bm.put("rejectReason", ob.getRejectReason());
            bm.put("rejectPhotoUrl", photoUrl(ob.getRejectPhotoPath()));
            bm.put("resortSummary", ob.getResortSummary());
            bm.put("resortReason", ob.getResortReason());
            bm.put("resortWeightKg", ob.getResortWeightKg());
            bm.put("resortPhotoUrl", photoUrl(ob.getResortPhotoPath()));
            bm.put("redistributions", batchRepo.findBySourceBatchOrderByCreatedAtAsc(ob).stream()
                    .map(cb -> {
                        Map<String, Object> cm = new LinkedHashMap<>();
                        cm.put("id", cb.getId());
                        cm.put("code", cb.getCode());
                        cm.put("batchType", cb.getBatchType());
                        cm.put("status", cb.getStatus().name());
                        cm.put("projectName", cb.getProjectName());
                        cm.put("recyclerName", cb.getRecyclerName());
                        cm.put("recycledWeightKg", cb.getRecycledWeightKg());
                        cm.put("publicNote", cb.getPublicNote());
                        return cm;
                    }).toList());
            bm.put("organizationName", ob.getOrganization() == null
                    ? null : ob.getOrganization().getOrganizationName());
            m.put("batch", bm);

            // 拒收再分配后：当前实际所在批次（最终去向）
            if (o.getCurrentBatch() != null && (ob.getId() == null
                    || !ob.getId().equals(o.getCurrentBatch().getId()))) {
                Batch cb = o.getCurrentBatch();
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("id", cb.getId());
                cm.put("code", cb.getCode());
                cm.put("batchType", cb.getBatchType());
                cm.put("projectName", cb.getProjectName());
                cm.put("recyclerName", cb.getRecyclerName());
                cm.put("status", cb.getStatus().name());
                cm.put("recycledWeightKg", cb.getRecycledWeightKg());
                m.put("currentBatch", cm);
            }
        }
        List<Map<String, Object>> cs = complaintRepo.findByOrderOrderByCreatedAtAsc(o).stream()
                .map(c -> Map.<String, Object>of("id", c.getId(), "type", c.getType().name(),
                        "status", c.getStatus().name()))
                .toList();
        m.put("complaints", cs);
        return m;
    }

    public Map<String, Object> sort(SortReview r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", r.getCategory().name());
        m.put("weightKg", r.getWeightKg());
        m.put("weightDiff", r.getWeightDiff());
        m.put("damageReason", r.getDamageReason());
        m.put("destination", r.getDestination());
        m.put("privacyRisk", r.isPrivacyRisk());
        m.put("privacyAction", r.getPrivacyAction() == null ? null : r.getPrivacyAction().name());
        m.put("privacyNote", r.getPrivacyNote());
        m.put("photoUrl", photoUrl(r.getPhotoPath()));
        m.put("privacyPhotoUrl", photoUrl(r.getPrivacyPhotoPath()));
        // 对外统一的隐私处置结论（标准化措辞，不含原始个人标识）
        m.put("privacyConclusion", privacyConclusion(r));
        m.put("sorter", Map.of("id", r.getSorter().getId(), "displayName", r.getSorter().getDisplayName()));
        m.put("reviewedAt", r.getReviewedAt());
        return m;
    }

    /** 公示端安全结论：仅给出标准处置结论与证据照片，不回传可能含个人标识的原始备注 */
    public Map<String, Object> privacyConclusion(SortReview r) {
        if (r == null || !r.isPrivacyRisk()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        if (r.getPrivacyAction() == PrivacyAction.DESENSITIZED) {
            m.put("action", "DESENSITIZED");
            m.put("label", "已脱敏后流转");
            m.put("conclusion", "校徽、工牌、姓名标签等个人标识已拆除或涂销，经分拣复检合格后流转");
            m.put("evidencePhotoUrl", photoUrl(r.getPrivacyPhotoPath()));
        } else if (r.getPrivacyAction() == PrivacyAction.REJECTED) {
            m.put("action", "REJECTED");
            m.put("label", "隐私拒收");
            m.put("conclusion", "含无法消除的个人标识，未进入公益流转，已按规定单独登记处置");
            m.put("evidencePhotoUrl", null);
        } else {
            m.put("action", "PENDING");
            m.put("label", "待处置");
            m.put("conclusion", "隐私风险待处置，暂不允许流转");
            m.put("evidencePhotoUrl", null);
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> batch(Batch b) {
        return batch(b, false);
    }

    /** 拒收类型中文标签 */
    public static String rejectTypeLabel(RejectReasonType t) {
        if (t == null) return null;
        return switch (t) {
            case SIZE_MISMATCH -> "尺码不匹配";
            case SEASON_MISMATCH -> "季节不匹配";
            case HYGIENE -> "卫生标准不匹配";
            case OTHER -> "其他原因";
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> batch(Batch b, boolean publicView) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("code", b.getCode());
        m.put("batchType", b.getBatchType());
        m.put("projectName", b.getProjectName());
        m.put("recyclerName", b.getRecyclerName());
        m.put("status", b.getStatus().name());
        m.put("totalWeightKg", b.getTotalWeightKg());
        m.put("itemCount", b.getItemCount());
        m.put("donationPhotoUrl", photoUrl(b.getDonationPhotoPath()));
        m.put("signPhotoUrl", photoUrl(b.getSignPhotoPath()));
        m.put("receiverName", b.getReceiverName());
        m.put("signNote", b.getSignNote());
        m.put("rejectReason", b.getRejectReason());
        m.put("rejectReasonType", b.getRejectReasonType() == null ? null : b.getRejectReasonType().name());
        m.put("rejectReasonTypeLabel", rejectTypeLabel(b.getRejectReasonType()));
        m.put("rejectPhotoUrl", photoUrl(b.getRejectPhotoPath()));
        m.put("recycledWeightKg", b.getRecycledWeightKg());
        m.put("publicNote", b.getPublicNote());
        m.put("shippedAt", b.getShippedAt());
        m.put("signedAt", b.getSignedAt());
        m.put("recycledAt", b.getRecycledAt());
        m.put("aidGivenAt", b.getAidGivenAt());
        m.put("createdAt", b.getCreatedAt());
        m.put("organization", b.getOrganization() == null ? null : user(b.getOrganization()));
        m.put("partner", b.getPartner() == null ? null : partner(b.getPartner()));
        // 拒收批退回后当前构成已释放，展示其原始批次构成
        List<RecycleOrder> memberOrders = b.getStatus() == BatchStatus.REJECTED
                ? b.getOriginalOrders() : b.getOrders();
        m.put("orderCount", memberOrders.size());

        // 拒收→重新分拣 证据与结论
        m.put("resortSummary", b.getResortSummary());
        m.put("resortReason", b.getResortReason());
        m.put("resortWeightKg", b.getResortWeightKg());
        m.put("resortPhotoUrl", photoUrl(b.getResortPhotoPath()));
        m.put("resortAt", b.getResortAt());
        m.put("resortSorter", b.getResortSorter() == null ? null
                : Map.of("id", b.getResortSorter().getId(), "displayName", b.getResortSorter().getDisplayName()));
        List<Map<String, Object>> resortRecords = publicView ? List.of()
                : resortRecordRepo.findByRejectedBatchOrderByCreatedAtAsc(b)
                .stream().map(rr -> {
                    Map<String, Object> rm = new LinkedHashMap<>();
                    rm.put("orderId", rr.getOrder().getId());
                    rm.put("orderCode", rr.getOrder().getCode());
                    rm.put("newCategory", rr.getNewCategory().name());
                    rm.put("weightKg", rr.getWeightKg());
                    rm.put("weightDiff", rr.getWeightDiff());
                    rm.put("reason", rr.getReason());
                    rm.put("photoUrl", photoUrl(rr.getPhotoPath()));
                    rm.put("outcome", rr.getOutcome().name());
                    return rm;
                }).toList();
        m.put("resortRecords", resortRecords);

        // 流向链：来源拒收批 / 再分配出的新批
        if (b.getSourceBatch() != null) {
            Map<String, Object> src = new LinkedHashMap<>();
            src.put("id", b.getSourceBatch().getId());
            src.put("code", b.getSourceBatch().getCode());
            src.put("status", b.getSourceBatch().getStatus().name());
            src.put("rejectReasonTypeLabel", rejectTypeLabel(b.getSourceBatch().getRejectReasonType()));
            m.put("sourceBatch", src);
        }
        List<Map<String, Object>> children = batchRepo.findBySourceBatchOrderByCreatedAtAsc(b).stream()
                .map(cb -> {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("id", cb.getId());
                    cm.put("code", cb.getCode());
                    cm.put("batchType", cb.getBatchType());
                    cm.put("status", cb.getStatus().name());
                    cm.put("projectName", cb.getProjectName());
                    cm.put("recyclerName", cb.getRecyclerName());
                    cm.put("totalWeightKg", cb.getTotalWeightKg());
                    cm.put("recycledWeightKg", cb.getRecycledWeightKg());
                    cm.put("publicNote", cb.getPublicNote());
                    cm.put("signPhotoUrl", photoUrl(cb.getSignPhotoPath()));
                    return cm;
                }).toList();
        m.put("redistributions", children);

        int hiddenCount = 0;
        List<Map<String, Object>> orders = memberOrders.stream().map(o -> {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("id", o.getId());
            om.put("status", o.getStatus().name());
            boolean hidden = o.isPublicHidden();
            om.put("publicHidden", hidden);
            if (hidden && publicView) {
                // 居民要求隐藏：公示端只展示批次与去向，不给任何住户明细（含内部单号）
                om.clear();
                om.put("anonymous", true);
                return om;
            }
            sortRepo.findByOrder(o).ifPresent(r -> {
                om.put("category", r.getCategory().name());
                om.put("weightKg", r.getWeightKg());
                Map<String, Object> conclusion = privacyConclusion(r);
                if (conclusion != null) om.put("privacy", conclusion);
            });
            om.put("code", o.getCode());
            om.put("communityName", o.getCommunityName());
            return om;
        }).toList();
        for (var o : memberOrders) if (o.isPublicHidden()) hiddenCount++;
        m.put("hiddenOrderCount", hiddenCount);
        m.put("orders", orders);
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> complaint(Complaint c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("type", c.getType().name());
        m.put("typeLabel", complaintLabel(c.getType()));
        m.put("status", c.getStatus().name());
        m.put("description", c.getDescription());
        m.put("resolutionNote", c.getResolutionNote());
        m.put("createdAt", c.getCreatedAt());
        m.put("resolvedAt", c.getResolvedAt());
        RecycleOrder o = c.getOrder();
        Map<String, Object> orderMap = new LinkedHashMap<>();
        orderMap.put("id", o.getId());
        orderMap.put("code", o.getCode());
        orderMap.put("communityName", o.getCommunityName());
        orderMap.put("address", o.getAddress());
        orderMap.put("status", o.getStatus().name());
        sortRepo.findByOrder(o).ifPresent(r -> {
            orderMap.put("category", r.getCategory().name());
            orderMap.put("privacy", privacyConclusion(r));
            orderMap.put("privacyNote", r.getPrivacyNote());
            orderMap.put("privacyPhotoUrl", photoUrl(r.getPrivacyPhotoPath()));
        });
        pickupRepo.findByOrder(o).ifPresent(p ->
                orderMap.put("pickupPhotoUrl", photoUrl(p.getPhotoPath())));
        m.put("order", orderMap);
        m.put("resident", user(c.getResident()));
        m.put("handler", c.getHandler() == null ? null : user(c.getHandler()));
        m.put("events", c.getEvents().stream().map(e -> {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("id", e.getId());
            em.put("authorName", e.getAuthor().getDisplayName());
            em.put("partyRole", e.getPartyRole());
            em.put("content", e.getContent());
            em.put("createdAt", e.getCreatedAt());
            return em;
        }).toList());
        return m;
    }

    public static String complaintLabel(ComplaintType t) {
        return switch (t) {
            case WEIGHT_DISPUTE -> "质疑称重";
            case LATE_VISIT -> "上门迟到";
            case MISJUDGED_DONATE -> "误判不可捐赠";
            case ORG_REJECTED -> "公益机构拒收";
            case POINTS_MISSING -> "积分未到账";
            case OPAQUE_DESTINATION -> "去向不透明";
        };
    }

    public Map<String, Object> ledger(PointsLedger l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("type", l.getType().name());
        m.put("points", l.getPoints());
        m.put("balanceAfter", l.getBalanceAfter());
        m.put("remark", l.getRemark());
        m.put("createdAt", l.getCreatedAt());
        m.put("orderCode", l.getOrder() == null ? null : l.getOrder().getCode());
        return m;
    }

    public Map<String, Object> partner(Partner p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("type", p.getType().name());
        m.put("projectName", p.getProjectName());
        m.put("contactName", p.getContactName());
        m.put("contactPhone", p.getContactPhone());
        m.put("startDate", p.getStartDate());
        m.put("endDate", p.getEndDate());
        m.put("targetKg", p.getTargetKg());
        m.put("collectedKg", p.getCollectedKg());
        m.put("status", p.getStatus());
        m.put("description", p.getDescription());
        return m;
    }

    public Map<String, Object> aid(AidFamily f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("maskedName", f.getMaskedName());
        m.put("maskedPhone", f.getMaskedPhone());
        m.put("communityName", f.getCommunityName());
        m.put("familySize", f.getFamilySize());
        m.put("needNote", f.getNeedNote());
        m.put("voucherNo", f.getVoucherNo());
        m.put("status", f.getStatus().name());
        m.put("createdAt", f.getCreatedAt());
        m.put("receivedAt", f.getReceivedAt());
        m.put("remark", f.getRemark());
        m.put("batchCode", f.getBatch() == null ? null : f.getBatch().getCode());
        m.put("operator", f.getOperator() == null ? null : f.getOperator().getDisplayName());
        return m;
    }

    public Map<String, Object> product(Product p) {
        return Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "pointsCost", p.getPointsCost(),
                "stock", p.getStock(),
                "description", p.getDescription() == null ? "" : p.getDescription(),
                "icon", p.getIcon() == null ? "🎁" : p.getIcon()
        );
    }

    public Map<String, Object> exchange(ExchangeOrder e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("product", product(e.getProduct()));
        m.put("quantity", e.getQuantity());
        m.put("totalPoints", e.getTotalPoints());
        m.put("status", e.getStatus().name());
        m.put("createdAt", e.getCreatedAt());
        m.put("deliveredAt", e.getDeliveredAt());
        m.put("residentName", e.getResident().getDisplayName());
        m.put("residentUsername", e.getResident().getUsername());
        return m;
    }
}
