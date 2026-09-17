package com.clothing.recycle.service;

import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 低收入家庭定向领取闭环：需求核验登记 → 分拣匹配（卫生/尺码/属地优先）
 * → 领取身份与代领核验 → 异常在同一回收单协同（不得直改公示）→ 回访 → 公益价值唯一记账。
 *
 * 库存口径（按件锁定到来源回收单）：
 * 每个可直接捐赠来源单在机构签收时初始化 aidAllocatableQuantity（可分配件数）；
 * 匹配时对来源单行加行锁并预占（aidReservedQuantity），生成 AidOrderAllocation 明细；
 * 签收把预占转为已发放（aidIssuedQuantity），少领部分释放；
 * 取消 / 退回重新分拣 / 换货 / 转其他家庭均按同一明细释放未签收预占；
 * 公示件数、批次 aidGivenQuantity 与公益价值件数只统计真实已发放数量。
 */
@Service
public class AidService {

    private final AidFamilyRepo familyRepo;
    private final AidDistributionRepo distRepo;
    private final AidIssueRepo issueRepo;
    private final AidIssueEventRepo issueEventRepo;
    private final AidVisitRepo visitRepo;
    private final AidValueRepo valueRepo;
    private final AidAllocationRepo allocationRepo;
    private final BatchRepo batchRepo;
    private final OrderRepo orderRepo;
    private final SortReviewRepo sortRepo;

    @PersistenceContext
    private EntityManager em;

    public AidService(AidFamilyRepo familyRepo, AidDistributionRepo distRepo, AidIssueRepo issueRepo,
                      AidIssueEventRepo issueEventRepo, AidVisitRepo visitRepo, AidValueRepo valueRepo,
                      AidAllocationRepo allocationRepo,
                      BatchRepo batchRepo, OrderRepo orderRepo, SortReviewRepo sortRepo) {
        this.familyRepo = familyRepo;
        this.distRepo = distRepo;
        this.issueRepo = issueRepo;
        this.issueEventRepo = issueEventRepo;
        this.visitRepo = visitRepo;
        this.valueRepo = valueRepo;
        this.allocationRepo = allocationRepo;
        this.batchRepo = batchRepo;
        this.orderRepo = orderRepo;
        this.sortRepo = sortRepo;
    }

    private static String str(Object v) { return v == null ? null : v.toString(); }
    private static int integer(Object v, int d) {
        if (v == null || v.toString().isBlank()) return d;
        return Integer.parseInt(v.toString());
    }
    private static boolean bool(Object v) { return v != null && Boolean.parseBoolean(v.toString()); }

    // ===================== 需求登记（社区核验） =====================

    @Transactional
    public AidFamily register(User operator, Map<String, Object> dto) {
        AidFamily f = new AidFamily();
        applyFamily(f, dto);
        if (f.getMaskedName() == null || f.getMaskedName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写脱敏姓名");
        }
        if (f.getProofType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择并核验资格证明类型");
        }
        f.setCommunityName(first(f.getCommunityName(), operator.getCommunityName()));
        f.setVerifiedBy(operator);
        f.setVerifiedAt(LocalDateTime.now());
        f.setOperator(operator);
        f.setStatus(AidStatus.RESERVED);
        f.setVoucherNo("AID2026" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        return familyRepo.save(f);
    }

    private void applyFamily(AidFamily f, Map<String, Object> dto) {
        f.setMaskedName(str(dto.get("maskedName")));
        f.setMaskedPhone(str(dto.get("maskedPhone")));
        f.setCommunityName(str(dto.get("communityName")));
        f.setSubdistrict(str(dto.get("subdistrict")));
        if (dto.get("proofType") != null) f.setProofType(AidProofType.valueOf(str(dto.get("proofType"))));
        f.setProofNote(str(dto.get("proofNote")));
        f.setFamilySize(integer(dto.get("familySize"), 1));
        f.setNeedCount(integer(dto.get("needCount"), f.getFamilySize() == null ? 1 : f.getFamilySize()));
        f.setGenderAgeDesc(str(dto.get("genderAgeDesc")));
        f.setSizes(str(dto.get("sizes")));
        f.setSeasons(str(dto.get("seasons")));
        f.setClothTypes(str(dto.get("clothTypes")));
        f.setAcceptUsed(bool(dto.get("acceptUsed")));
        f.setNeedShoesBagsBedding(bool(dto.get("needShoesBagsBedding")));
        if (dto.get("deliveryMethod") != null) f.setDeliveryMethod(DeliveryMethod.valueOf(str(dto.get("deliveryMethod"))));
        f.setPublicHidden(dto.get("publicHidden") == null || bool(dto.get("publicHidden")));
        f.setDesignatedTarget(str(dto.get("designatedTarget")));
        f.setNeedNote(str(dto.get("needNote")));
    }

    private String first(String a, String b) { return (a != null && !a.isBlank()) ? a : b; }

    // ===================== 分拣匹配 =====================

    /** 可匹配批次：公益机构已签收（卫生把关）的捐赠批，且含可直接捐赠类、仍有按件剩余库存的回收单；
     *  待消毒/环保再生/不可回收/拒收/在途批次均不可用，已发完的来源单不再进入候选。属地（同小区/同街道）优先。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> candidateBatches(AidFamily family) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Batch b : batchRepo.findAllByOrderByCreatedAtDesc()) {
            if (!"DONATION".equals(b.getBatchType())) continue;
            if (b.getStatus() != BatchStatus.RECEIVED && b.getStatus() != BatchStatus.AID_GIVEN) continue;
            List<RecycleOrder> ready = readyOrders(b);
            if (ready.isEmpty()) continue;
            // 库存口径覆盖批内全部可直接捐赠来源单（含已发完退出候选的单），
            // ready 仅用于判断该批是否还能继续匹配
            List<RecycleOrder> directDonate = directDonateOrders(b);
            int allocatable = directDonate.stream().mapToInt(RecycleOrder::aidAllocatable).sum();
            int reserved = directDonate.stream().mapToInt(RecycleOrder::aidReserved).sum();
            int issued = directDonate.stream().mapToInt(RecycleOrder::aidIssued).sum();
            int available = directDonate.stream().mapToInt(RecycleOrder::aidAvailable).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("code", b.getCode());
            m.put("projectName", b.getProjectName());
            m.put("designatedTarget", b.getDesignatedTarget());
            m.put("organizationName", b.getOrganization() == null ? null : b.getOrganization().getOrganizationName());
            m.put("totalWeightKg", b.getTotalWeightKg());
            m.put("aidGivenQuantity", issued);
            m.put("aidAllocatableQuantity", allocatable);
            m.put("aidReservedQuantity", reserved);
            m.put("aidIssuedQuantity", issued);
            m.put("aidAvailableQuantity", available);
            m.put("readyOrderCount", ready.size());
            m.put("signedAt", b.getSignedAt());
            boolean local = ready.stream().anyMatch(o ->
                    family.getCommunityName() != null
                            && family.getCommunityName().equals(o.getCommunityName()));
            m.put("localPriority", local);
            out.add(m);
        }
        // 属地优先，其次签收时间近者
        out.sort((x, y) -> Boolean.compare((Boolean) y.get("localPriority"), (Boolean) x.get("localPriority")));
        return out;
    }

    private List<RecycleOrder> readyOrders(Batch b) {
        return directDonateOrders(b).stream().filter(o -> o.aidAvailable() > 0).toList();
    }

    /** 批内可直接捐赠、未被隐私拒收的来源单（机构签收时已初始化可分配件数） */
    private List<RecycleOrder> directDonateOrders(Batch b) {
        List<RecycleOrder> members = b.getStatus() == BatchStatus.REJECTED
                ? b.getOriginalOrders() : b.getOrders();
        List<RecycleOrder> direct = new ArrayList<>();
        for (RecycleOrder o : members) {
            SortReview r = sortRepo.findByOrder(o).orElse(null);
            // 仅可直接捐赠；需消毒整理、环保再生、不可回收、特殊处理一律不得发放
            if (r != null && r.getCategory() == SortCategory.DIRECT_DONATE
                    && r.getPrivacyAction() != PrivacyAction.REJECTED) {
                direct.add(o);
            }
        }
        return direct;
    }

    @Transactional
    public AidDistribution match(Long familyId, Long batchId, List<Long> orderIds,
                                 Integer plannedQuantity, User sorter) {
        return match(familyId, batchId, orderIds, plannedQuantity, sorter, false);
    }

    @Transactional
    public AidDistribution match(Long familyId, Long batchId, List<Long> orderIds,
                                 Integer plannedQuantity, User sorter, boolean isExchange) {
        AidFamily f = familyRepo.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "家庭登记不存在"));
        if (!isExchange && f.getStatus() == AidStatus.RECEIVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "该家庭已完成领取；如产生新需求请重新登记后再匹配，防止重复领取");
        }
        if (!isExchange) {
            boolean pending = distRepo.findAllByOrderByCreatedAtDesc().stream()
                    .anyMatch(d -> d.getFamily().getId().equals(f.getId())
                            && d.getStatus() == AidDistributionStatus.MATCHED);
            if (pending) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该家庭已有待领取任务，请勿重复匹配");
            }
        }
        Batch b = batchRepo.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在"));
        if (!"DONATION".equals(b.getBatchType())
                || (b.getStatus() != BatchStatus.RECEIVED && b.getStatus() != BatchStatus.AID_GIVEN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅公益机构已签收的捐赠批可定向发放");
        }
        // 去重后的来源单 id（校验阶段不加锁）
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        if (orderIds != null) orderIds.forEach(uniqueIds::add);
        if (uniqueIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择匹配的回收单");
        }
        List<RecycleOrder> readyCheck = readyOrders(b);
        Map<Long, RecycleOrder> readyMap = new HashMap<>();
        readyCheck.forEach(o -> readyMap.put(o.getId(), o));
        for (Long oid : uniqueIds) {
            if (!readyMap.containsKey(oid)) {
                RecycleOrder raw = orderRepo.findById(oid).orElse(null);
                String code = raw == null ? String.valueOf(oid) : raw.getCode();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "回收单 " + code + " 当前不可定向发放：须为机构已签收的可直接捐赠衣物且仍有未发剩余件数"
                                + "（待消毒/再生/不可回收/已发完均不可）");
            }
        }
        // 企业/学校指定对象：若批次或家庭带指定对象，必须一致
        if (b.getDesignatedTarget() != null && f.getDesignatedTarget() != null
                && !b.getDesignatedTarget().equals(f.getDesignatedTarget())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该批为指定捐赠批次，与登记的指定对象不一致");
        }
        int qty = plannedQuantity == null || plannedQuantity <= 0
                ? uniqueIds.size() * 3 : plannedQuantity;

        // ---- 全部业务校验通过后，锁定来源回收单行（FOR UPDATE）并预占件数 ----
        // 并发匹配相同来源单时在此串行：后提交的事务拿到行锁后强制刷新为已预占后的最新值，
        // 总预占绝不会超过剩余件数。
        List<RecycleOrder> locked = lockOrders(uniqueIds);
        int totalAvailable = locked.stream().mapToInt(RecycleOrder::aidAvailable).sum();
        if (qty > totalAvailable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "来源衣物库存不足：本次计划发放 " + qty + " 件，所选回收单当前可匹配剩余仅 "
                            + totalAvailable + " 件（可分配 "
                            + locked.stream().mapToInt(RecycleOrder::aidAllocatable).sum()
                            + " 件、已预占 " + locked.stream().mapToInt(RecycleOrder::aidReserved).sum()
                            + " 件、已发放 " + locked.stream().mapToInt(RecycleOrder::aidIssued).sum()
                            + " 件），请调减件数或改选其他来源单");
        }
        // 按各来源单剩余比例拆分预占（整除后的余量逐单补 1），任何一单都不会超锁
        Map<Long, Integer> reserveByOrder = distribute(locked, qty);

        AidDistribution d = new AidDistribution();
        d.setFamily(f);
        d.setBatch(b);
        d.setMatchedBy(sorter);
        d.setMatchedOrderIds(uniqueIds.toString().replaceAll("[\\[\\] ]", ""));
        d.setPlannedQuantity(qty);
        d.setStatus(AidDistributionStatus.MATCHED);
        d = distRepo.save(d);

        List<AidOrderAllocation> allocations = new ArrayList<>();
        for (RecycleOrder o : locked) {
            int take = reserveByOrder.getOrDefault(o.getId(), 0);
            if (take <= 0) continue;
            o.setAidReservedQuantity(o.aidReserved() + take);
            orderRepo.save(o);
            AidOrderAllocation a = new AidOrderAllocation();
            a.setDistribution(d);
            a.setOrder(o);
            a.setReservedQuantity(take);
            allocations.add(allocationRepo.save(a));
        }
        if (allocations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "来源衣物库存不足，预占失败");
        }

        f.setStatus(AidStatus.MATCHED);
        familyRepo.save(f);
        return d;
    }

    /** 对来源回收单加行锁并强制刷新为数据库最新值：
     *  FOR UPDATE 让并发匹配/签收/释放在此串行，refresh 确保拿到前一事务已提交的预占/发放数，
     *  避免一级缓存里的旧快照导致超发写覆盖。按 id 升序加锁，避免多单交叉加锁死锁。 */
    private List<RecycleOrder> lockOrders(Collection<Long> ids) {
        List<Long> sorted = ids.stream().distinct().sorted().toList();
        List<RecycleOrder> locked = orderRepo.findByIdInForUpdate(sorted);
        locked.sort(Comparator.comparing(RecycleOrder::getId));
        for (RecycleOrder o : locked) em.refresh(o);
        return locked;
    }

    /** 按来源单当前剩余件数比例分配 total，保证每单分配额 ≤ 该单剩余且合计恰为 total */
    private Map<Long, Integer> distribute(List<RecycleOrder> orders, int total) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        int capacity = orders.stream().mapToInt(RecycleOrder::aidAvailable).sum();
        int assigned = 0;
        for (RecycleOrder o : orders) {
            int share = capacity <= 0 ? 0 : (int) ((long) total * o.aidAvailable() / capacity);
            share = Math.min(share, o.aidAvailable());
            result.put(o.getId(), share);
            assigned += share;
        }
        int rest = total - assigned;
        for (RecycleOrder o : orders) {
            if (rest <= 0) break;
            if (result.get(o.getId()) < o.aidAvailable()) {
                result.put(o.getId(), result.get(o.getId()) + 1);
                rest--;
            }
        }
        return result;
    }

    // ===================== 领取核验（防冒领/代领授权/签收证据） =====================

    @Transactional
    public AidDistribution handout(Long distId, Map<String, Object> dto, String signPhoto, User operator) {
        AidDistribution d = must(distId);
        if (d.getStatus() != AidDistributionStatus.MATCHED && d.getStatus() != AidDistributionStatus.EXCHANGED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前任务状态不可领取");
        }
        int qty = integer(dto.get("actualQuantity"), 0);
        if (qty <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写实际领取数量");

        // ---- 先做人证/物证校验，任何一项不满足都不得扣库存、不得改公示 ----
        String relation = str(dto.get("receiverRelation"));
        if (relation == null || relation.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请核验并填写领取人与登记人关系");
        }
        String proxyName = null;
        String proxyAuth = null;
        if (!"本人".equals(relation)) {
            proxyName = str(dto.get("proxyName"));
            proxyAuth = str(dto.get("proxyAuthNote"));
            if (proxyName == null || proxyName.isBlank() || proxyAuth == null || proxyAuth.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "代领必须登记代领人脱敏姓名，以及登记人电话确认或社区确认的授权依据，防止冒领");
            }
        }
        if (signPhoto == null || signPhoto.isBlank()) {
            String signature = str(dto.get("signatureNote"));
            if (signature == null || signature.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须上传签收照片或留存签字记录");
            }
            signPhoto = null; // 签字记录写 handNote
        }

        // ---- 库存校验：实领件数不得超过该任务已从来源单锁定的件数 ----
        List<AidOrderAllocation> allocations = allocationRepo.findByDistributionOrderByIdAsc(d);
        int lockedQty = allocations.stream().mapToInt(AidOrderAllocation::getReservedQuantity).sum();
        if (allocations.isEmpty() || lockedQty <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该任务没有已锁定的来源衣物库存，不能签收（请重新匹配有剩余件数的来源回收单）");
        }
        if (qty > lockedQty) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "实际领取 " + qty + " 件超过本任务已锁定的来源库存 " + lockedQty
                            + " 件；禁止跨回收单超发，请按锁定件数签收或重新匹配");
        }

        // 锁定来源单行后，把预占按明细转为已发放；少领的余量在同一明细上释放
        List<Long> orderIds = allocations.stream().map(a -> a.getOrder().getId()).distinct().sorted().toList();
        Map<Long, RecycleOrder> lockedOrders = new HashMap<>();
        lockOrders(orderIds).forEach(o -> lockedOrders.put(o.getId(), o));

        int remaining = qty;
        for (AidOrderAllocation a : allocations) {
            RecycleOrder o = lockedOrders.get(a.getOrder().getId());
            int take = Math.min(a.getReservedQuantity(), remaining);
            if (take > 0) {
                o.setAidReservedQuantity(o.aidReserved() - take);
                o.setAidIssuedQuantity(o.aidIssued() + take);
                orderRepo.save(o);
                a.setReservedQuantity(a.getReservedQuantity() - take);
                a.setIssuedQuantity(a.getIssuedQuantity() + take);
            }
            remaining -= take;
        }
        if (remaining > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "来源库存扣减异常，剩余 " + remaining + " 件未锁定");
        }
        // 实领少于计划：未签收的预占全部释放回可分配余额
        for (AidOrderAllocation a : allocations) {
            if (a.getReservedQuantity() > 0) {
                RecycleOrder o = lockedOrders.get(a.getOrder().getId());
                o.setAidReservedQuantity(Math.max(0, o.aidReserved() - a.getReservedQuantity()));
                orderRepo.save(o);
                a.setReservedQuantity(0);
            }
            allocationRepo.save(a);
        }

        if (!"本人".equals(relation)) {
            d.setProxyName(proxyName);
            d.setProxyAuthNote(proxyAuth);
        }
        d.setReceiverRelation(relation);
        d.setActualQuantity(qty);
        d.setSignPhotoPath(signPhoto);
        d.setHandNote(str(dto.get("handNote")) != null ? str(dto.get("handNote")) : str(dto.get("signatureNote")));
        d.setHandedBy(operator);
        d.setHandedAt(LocalDateTime.now());
        d.setStatus(AidDistributionStatus.HANDED_OUT);
        distRepo.save(d);

        AidFamily f = d.getFamily();
        f.setStatus(AidStatus.RECEIVED);
        f.setReceivedAt(d.getHandedAt());
        f.setBatch(d.getBatch());
        familyRepo.save(f);

        // 公示件数 = 该批所有任务从来源单真实签收的件数（不由入参直接累加，杜绝超额写入公示）
        Batch b = d.getBatch();
        b.setAidGivenQuantity(allocationRepo.sumIssuedByBatchId(b.getId()));
        b.setStatus(BatchStatus.AID_GIVEN);
        b.setAidGivenAt(LocalDateTime.now());
        batchRepo.save(b);
        return d;
    }

    // ===================== 异常协同（不直改公示） =====================

    /** 异常挂在具体回收单上下文中，居民/社区/分拣/机构/回收员通过时间线协同；
     * 处置只能换货/退回重分/转家庭/补充分拣，不允许直接改写已公示批次去向。 */
    @Transactional
    public AidIssue openIssue(Long distId, Long orderId, AidIssueType type, String description, User author) {
        AidDistribution d = must(distId);
        RecycleOrder o = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "回收单不存在"));
        AidIssue issue = new AidIssue();
        issue.setDistribution(d);
        issue.setOrder(o);
        issue.setType(type);
        issue.setDescription(description);
        issue.setCreatedBy(author);
        issue = issueRepo.save(issue);
        addIssueEvent(issue.getId(), author, "发起异常：" + description);
        return issue;
    }

    @Transactional
    public AidIssueEvent addIssueEvent(Long issueId, User author, String content) {
        AidIssue issue = issueRepo.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "异常单不存在"));
        if (issue.getStatus() == ComplaintStatus.OPEN) issue.setStatus(ComplaintStatus.PROCESSING);
        issueRepo.save(issue);
        AidIssueEvent e = new AidIssueEvent();
        e.setIssue(issue);
        e.setAuthor(author);
        e.setPartyRole(roleLabel(author.getRole()));
        e.setContent(content);
        return issueEventRepo.save(e);
    }

    private String roleLabel(Role r) {
        return Map.of(Role.RESIDENT, "居民", Role.COLLECTOR, "回收员", Role.SORTER, "分拣中心",
                Role.COMMUNITY, "社区", Role.ORG, "公益机构", Role.FINANCE, "积分财务",
                Role.ADMIN, "平台管理员").get(r);
    }

    /** 异常处置：换货/退回重新分拣/转其他家庭/补充分拣/取消。
     *  只新增处置记录与新任务，绝不改写已签收批次的公示字段；
     *  对尚未签收的锁定件数，一律按原 AidOrderAllocation 明细释放回来源单可分配余额。 */
    @Transactional
    public AidIssue resolveIssue(Long issueId, String action, String note,
                                 Long replacementBatchId, List<Long> replacementOrderIds,
                                 User resolver) {
        AidIssue issue = issueRepo.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "异常单不存在"));
        if (issue.getStatus() == ComplaintStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "异常已处置");
        }
        AidDistribution d = issue.getDistribution();
        switch (action) {
            case "CANCEL", "TRANSFER_FAMILY" -> {
                releaseReservations(d, "取消/转其他家庭释放未领取预占");
                d.setStatus(AidDistributionStatus.CANCELLED);
                d.getFamily().setStatus(AidStatus.RESERVED);
                familyRepo.save(d.getFamily());
            }
            case "RETURN_RESORT", "ADDITIONAL_SORT" -> {
                releaseReservations(d, "退回重新分拣/补充分拣释放未领取预占");
                d.setStatus(AidDistributionStatus.RETURNED);
                d.getFamily().setStatus(AidStatus.RESERVED);
                familyRepo.save(d.getFamily());
            }
            case "EXCHANGE" -> {
                if (replacementBatchId == null || replacementOrderIds == null || replacementOrderIds.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "换货须选择替代批次与回收单");
                }
                // 先释放原任务未签收的预占（已签收件不回冲，由换货新任务补发），再走带库存锁定的匹配
                releaseReservations(d, "换货释放原任务未领取预占");
                d.setStatus(AidDistributionStatus.RETURNED);
                d.setRehandled(true);
                // 换货属同一家庭的补发，豁免防重复匹配，但不重复计算公益价值
                AidDistribution replacement = match(d.getFamily().getId(), replacementBatchId,
                        replacementOrderIds, d.getPlannedQuantity(), resolver, true);
                replacement.setRehandled(true);
                note = (note == null ? "" : note) + "；换货新任务 #" + replacement.getId();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知处置方式");
        }
        distRepo.save(d);
        issue.setResolutionAction(action);
        issue.setResolutionNote(note);
        issue.setResolvedBy(resolver);
        issue.setStatus(ComplaintStatus.RESOLVED);
        issue.setResolvedAt(LocalDateTime.now());
        return issueRepo.save(issue);
    }

    /** 按同一明细释放任务仍锁定（未签收）的件数；已发放件数不回冲，批次公示件数不变。 */
    private void releaseReservations(AidDistribution d, String reason) {
        List<AidOrderAllocation> allocations = allocationRepo.findByDistributionOrderByIdAsc(d);
        if (allocations.isEmpty()) return;
        List<Long> orderIds = allocations.stream().map(a -> a.getOrder().getId()).distinct().sorted().toList();
        Map<Long, RecycleOrder> locked = new HashMap<>();
        lockOrders(orderIds).forEach(o -> locked.put(o.getId(), o));
        for (AidOrderAllocation a : allocations) {
            int held = a.getReservedQuantity();
            if (held <= 0) continue;
            RecycleOrder o = locked.get(a.getOrder().getId());
            o.setAidReservedQuantity(Math.max(0, o.aidReserved() - held));
            orderRepo.save(o);
            a.setReservedQuantity(0);
            allocationRepo.save(a);
        }
    }

    // ===================== 回访 =====================

    @Transactional
    public AidVisit visit(Long familyId, Map<String, Object> dto, User operator) {
        AidFamily f = familyRepo.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "家庭不存在"));
        AidVisit v = new AidVisit();
        v.setFamily(f);
        v.setBatch(f.getBatch());
        v.setVisitedBy(operator);
        v.setWearingSituation(str(dto.get("wearingSituation")));
        v.setSatisfaction(integer(dto.get("satisfaction"), 5));
        v.setFollowupNeed(str(dto.get("followupNeed")));
        v.setNote(str(dto.get("note")));
        return visitRepo.save(v);
    }

    // ===================== 公益价值唯一记账（财务） =====================

    @Transactional
    public AidValueRecord recordValue(Long batchId, BigDecimal amount, Integer quantity,
                                      String remark, User finance) {
        Batch b = batchRepo.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在"));
        if (valueRepo.findByBatch(b).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "该批次公益价值已记账，禁止重复入账（积分/捐赠金额/物资价值每批只记一次）");
        }
        // 件数口径只认来源单真实已发放数量：未填则取实际值，填写值不得超过实际值
        int issued = allocationRepo.sumIssuedByBatchId(b.getId());
        Integer qty = quantity;
        if (qty == null) {
            qty = issued;
        } else if (qty > issued) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "记账件数 " + qty + " 件超过该批次真实已发放 " + issued
                            + " 件，公益价值只能按实际签收数量入账");
        }
        AidValueRecord rec = new AidValueRecord();
        rec.setBatch(b);
        rec.setValueAmount(amount);
        rec.setQuantity(qty);
        rec.setRecordedBy(finance);
        rec.setRemark(remark);
        return valueRepo.save(rec);
    }

    public static String typeLabel(AidIssueType t) {
        return switch (t) {
            case SIZE_WRONG -> "尺码不合";
            case NEED_MISMATCH -> "衣物与需求不符";
            case HYGIENE_DOUBT -> "卫生被质疑";
            case RECIPIENT_GIVEUP -> "领取人临时放弃";
            case SHORTAGE -> "物资不足";
            case FRAUD_DETECTED -> "发现冒领";
        };
    }

    private AidDistribution must(Long id) {
        return distRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "领取任务不存在"));
    }
}
