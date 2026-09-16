package com.clothing.recycle.controller;

import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import com.clothing.recycle.security.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

/** 社区复盘：按小区 / 回收员 / 公益项目 / 投诉类型多维统计回收效果 */
@RestController
@RequestMapping("/api/stats")
@Transactional(readOnly = true)
public class StatsController {

    private final OrderRepo orderRepo;
    private final SortReviewRepo sortRepo;
    private final BatchRepo batchRepo;
    private final ComplaintRepo complaintRepo;
    private final UserRepo userRepo;
    private final PickupRepo pickupRepo;
    private final CurrentUser cu;

    public StatsController(OrderRepo orderRepo, SortReviewRepo sortRepo, BatchRepo batchRepo,
                           ComplaintRepo complaintRepo, UserRepo userRepo, PickupRepo pickupRepo,
                           CurrentUser cu) {
        this.orderRepo = orderRepo;
        this.sortRepo = sortRepo;
        this.batchRepo = batchRepo;
        this.complaintRepo = complaintRepo;
        this.userRepo = userRepo;
        this.pickupRepo = pickupRepo;
        this.cu = cu;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        cu.require(Role.COMMUNITY, Role.ADMIN, Role.FINANCE);
        Map<String, Object> m = new LinkedHashMap<>();
        List<RecycleOrder> orders = orderRepo.findAll();
        m.put("totalOrders", orders.size());
        m.put("pendingCount", orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count());
        m.put("pickedCount", orders.stream().filter(o -> o.getStatus() == OrderStatus.PICKED_UP).count());
        m.put("donatedCount", orders.stream().filter(o -> o.getStatus() == OrderStatus.DONATED).count());
        m.put("recycledCount", orders.stream().filter(o -> o.getStatus() == OrderStatus.RECYCLED).count());
        m.put("rejectedCount", orders.stream().filter(o -> o.getStatus() == OrderStatus.REJECTED).count());

        BigDecimal pickupWeight = BigDecimal.ZERO;
        BigDecimal sortWeight = BigDecimal.ZERO;
        for (SortReview r : sortRepo.findAll()) {
            if (r.getWeightKg() != null) sortWeight = sortWeight.add(r.getWeightKg());
        }
        for (PickupRecord p : pickupRepo.findAll()) {
            if (p.getWeightKg() != null) pickupWeight = pickupWeight.add(p.getWeightKg());
        }
        m.put("pickupWeightKg", pickupWeight);
        m.put("sortWeightKg", sortWeight);
        m.put("openComplaints", complaintRepo.countByStatus(ComplaintStatus.OPEN)
                + complaintRepo.countByStatus(ComplaintStatus.PROCESSING));
        m.put("resolvedComplaints", complaintRepo.countByStatus(ComplaintStatus.RESOLVED)
                + complaintRepo.countByStatus(ComplaintStatus.CLOSED));
        return m;
    }

    /** 按小区复盘 */
    @GetMapping("/by-community")
    public List<Map<String, Object>> byCommunity(@RequestParam(required = false) String community) {
        cu.require(Role.COMMUNITY, Role.ADMIN);
        List<RecycleOrder> orders = filterOrders(community);
        Map<String, List<RecycleOrder>> grouped = new TreeMap<>();
        orders.forEach(o -> grouped.computeIfAbsent(
                o.getCommunityName() == null ? "未填写" : o.getCommunityName(), k -> new ArrayList<>()).add(o));

        List<Map<String, Object>> out = new ArrayList<>();
        grouped.forEach((name, list) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("communityName", name);
            row.put("orderCount", list.size());
            row.put("donatedCount", list.stream().filter(o -> o.getStatus() == OrderStatus.DONATED).count());
            row.put("recycledCount", list.stream().filter(o -> o.getStatus() == OrderStatus.RECYCLED).count());
            row.put("rejectedCount", list.stream().filter(o -> o.getStatus() == OrderStatus.REJECTED).count());
            BigDecimal weight = BigDecimal.ZERO;
            for (RecycleOrder o : list) {
                var r = sortRepo.findByOrder(o);
                if (r.isPresent() && r.get().getWeightKg() != null) weight = weight.add(r.get().getWeightKg());
            }
            row.put("weightKg", weight);
            long complaints = complaintRepo.findAll().stream()
                    .filter(c -> name.equals(c.getOrder().getCommunityName())).count();
            row.put("complaintCount", complaints);
            out.add(row);
        });
        return out;
    }

    /** 按回收员复盘：单量、重量、迟到次数、被投诉次数 */
    @GetMapping("/by-collector")
    public List<Map<String, Object>> byCollector(@RequestParam(required = false) String community) {
        cu.require(Role.COMMUNITY, Role.ADMIN);
        List<RecycleOrder> orders = filterOrders(community);
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (RecycleOrder o : orders) {
            if (o.getCollector() == null) continue;
            Long cid = o.getCollector().getId();
            Map<String, Object> row = byId.computeIfAbsent(cid, k -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("collectorId", cid);
                r.put("collectorName", o.getCollector().getDisplayName());
                r.put("orderCount", 0);
                r.put("lateCount", 0);
                r.put("weightKg", BigDecimal.ZERO);
                r.put("complaintCount", 0L);
                return r;
            });
            row.put("orderCount", (int) row.get("orderCount") + 1);
            pickupRepo.findByOrder(o).ifPresent(p -> {
                if (p.isArrivedLate()) row.put("lateCount", (int) row.get("lateCount") + 1);
                if (p.getWeightKg() != null) row.put("weightKg",
                        ((BigDecimal) row.get("weightKg")).add(p.getWeightKg()));
            });
        }
        long weightComplaints = complaintRepo.findAll().stream()
                .filter(c -> c.getType() == ComplaintType.WEIGHT_DISPUTE
                        || c.getType() == ComplaintType.LATE_VISIT)
                .filter(c -> c.getOrder().getCollector() != null)
                .count();
        for (var row : byId.values()) {
            Long cid = (Long) row.get("collectorId");
            long cc = complaintRepo.findAll().stream()
                    .filter(c -> c.getOrder().getCollector() != null
                            && cid.equals(c.getOrder().getCollector().getId())
                            && (c.getType() == ComplaintType.WEIGHT_DISPUTE
                            || c.getType() == ComplaintType.LATE_VISIT))
                    .count();
            row.put("complaintCount", cc);
        }
        return new ArrayList<>(byId.values());
    }

    /** 按公益项目/批次复盘 */
    @GetMapping("/by-project")
    public List<Map<String, Object>> byProject() {
        cu.require(Role.COMMUNITY, Role.ADMIN, Role.ORG, Role.FINANCE);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Batch b : batchRepo.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("batchCode", b.getCode());
            row.put("batchType", b.getBatchType());
            row.put("projectName", b.getProjectName());
            row.put("recyclerName", b.getRecyclerName());
            row.put("orgName", b.getOrganization() == null ? "-" : b.getOrganization().getOrganizationName());
            row.put("partnerName", b.getPartner() == null ? null : b.getPartner().getName());
            row.put("totalWeightKg", b.getTotalWeightKg());
            row.put("recycledWeightKg", b.getRecycledWeightKg());
            row.put("orderCount", b.getStatus() == BatchStatus.REJECTED
                    ? b.getOriginalOrders().size() : b.getOrders().size());
            row.put("status", b.getStatus().name());
            row.put("rejectReason", b.getRejectReason());
            out.add(row);
        }
        return out;
    }

    /** 按投诉类型复盘 */
    @GetMapping("/by-complaint")
    public List<Map<String, Object>> byComplaint(@RequestParam(required = false) String community) {
        cu.require(Role.COMMUNITY, Role.ADMIN);
        List<Complaint> all = complaintRepo.findAllByOrderByCreatedAtDesc();
        if (community != null && !community.isBlank()) {
            all = all.stream().filter(c -> community.equals(c.getOrder().getCommunityName())).toList();
        }
        Map<ComplaintType, long[]> grouped = new EnumMap<>(ComplaintType.class);
        for (Complaint c : all) {
            long[] v = grouped.computeIfAbsent(c.getType(), k -> new long[3]);
            v[0]++;
            if (c.getStatus() == ComplaintStatus.RESOLVED || c.getStatus() == ComplaintStatus.CLOSED) v[1]++;
            else v[2]++;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        grouped.forEach((type, v) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", type.name());
            row.put("typeLabel", com.clothing.recycle.config.ViewMapper.complaintLabel(type));
            row.put("total", v[0]);
            row.put("resolved", v[1]);
            row.put("open", v[2]);
            out.add(row);
        });
        return out;
    }

    private List<RecycleOrder> filterOrders(String community) {
        List<RecycleOrder> orders;
        User me = cu.get();
        if (me.getRole() == Role.COMMUNITY && me.getCommunityName() != null) {
            orders = orderRepo.findByCommunityNameOrderByCreatedAtDesc(me.getCommunityName());
        } else if (community != null && !community.isBlank()) {
            orders = orderRepo.findByCommunityNameOrderByCreatedAtDesc(community);
        } else {
            orders = orderRepo.findAll();
        }
        return orders;
    }
}
