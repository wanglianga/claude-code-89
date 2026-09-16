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

    public ViewMapper(PickupRepo pickupRepo, SortReviewRepo sortRepo,
                      ComplaintRepo complaintRepo, PointsLedgerRepo ledgerRepo) {
        this.pickupRepo = pickupRepo;
        this.sortRepo = sortRepo;
        this.complaintRepo = complaintRepo;
        this.ledgerRepo = ledgerRepo;
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
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", o.getBatch().getId());
            bm.put("code", o.getBatch().getCode());
            bm.put("batchType", o.getBatch().getBatchType());
            bm.put("projectName", o.getBatch().getProjectName());
            bm.put("recyclerName", o.getBatch().getRecyclerName());
            bm.put("status", o.getBatch().getStatus().name());
            bm.put("totalWeightKg", o.getBatch().getTotalWeightKg());
            bm.put("signedAt", o.getBatch().getSignedAt());
            bm.put("organizationName", o.getBatch().getOrganization() == null
                    ? null : o.getBatch().getOrganization().getOrganizationName());
            m.put("batch", bm);
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
        m.put("sorter", Map.of("id", r.getSorter().getId(), "displayName", r.getSorter().getDisplayName()));
        m.put("reviewedAt", r.getReviewedAt());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> batch(Batch b) {
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
        m.put("recycledWeightKg", b.getRecycledWeightKg());
        m.put("publicNote", b.getPublicNote());
        m.put("shippedAt", b.getShippedAt());
        m.put("signedAt", b.getSignedAt());
        m.put("recycledAt", b.getRecycledAt());
        m.put("aidGivenAt", b.getAidGivenAt());
        m.put("createdAt", b.getCreatedAt());
        m.put("organization", b.getOrganization() == null ? null : user(b.getOrganization()));
        m.put("partner", b.getPartner() == null ? null : partner(b.getPartner()));
        m.put("orderCount", b.getOrders().size());
        List<Map<String, Object>> orders = b.getOrders().stream().map(o -> {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("id", o.getId());
            om.put("code", o.getCode());
            om.put("communityName", o.getCommunityName());
            om.put("status", o.getStatus().name());
            sortRepo.findByOrder(o).ifPresent(r -> {
                om.put("category", r.getCategory().name());
                om.put("weightKg", r.getWeightKg());
            });
            return om;
        }).toList();
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
        m.put("order", Map.of(
                "id", o.getId(), "code", o.getCode(),
                "communityName", o.getCommunityName(), "address", o.getAddress(),
                "status", o.getStatus().name()));
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
