package com.clothing.recycle.service;

import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
 */
@Service
public class AidService {

    private final AidFamilyRepo familyRepo;
    private final AidDistributionRepo distRepo;
    private final AidDistributionItemRepo distItemRepo;
    private final AidIssueRepo issueRepo;
    private final AidIssueEventRepo issueEventRepo;
    private final AidVisitRepo visitRepo;
    private final AidValueRepo valueRepo;
    private final BatchRepo batchRepo;
    private final OrderRepo orderRepo;
    private final SortReviewRepo sortRepo;

    @PersistenceContext
    private EntityManager em;

    public AidService(AidFamilyRepo familyRepo, AidDistributionRepo distRepo,
                      AidDistributionItemRepo distItemRepo, AidIssueRepo issueRepo,
                      AidIssueEventRepo issueEventRepo, AidVisitRepo visitRepo, AidValueRepo valueRepo,
                      BatchRepo batchRepo, OrderRepo orderRepo, SortReviewRepo sortRepo) {
        this.familyRepo = familyRepo;
        this.distRepo = distRepo;
        this.distItemRepo = distItemRepo;
        this.issueRepo = issueRepo;
        this.issueEventRepo = issueEventRepo;
        this.visitRepo = visitRepo;
        this.valueRepo = valueRepo;
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

    /** 可匹配批次：公益机构已签收（卫生把关）的捐赠批，且含可直接捐赠类回收单；
     *  待消毒/环保再生/不可回收/拒收/在途批次均不可用。属地（同小区/同街道）优先。
     *  来源单剩余可分配件数为 0（已发完/已预占完）的批次不再进入候选。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> candidateBatches(AidFamily family) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Batch b : batchRepo.findAllByOrderByCreatedAtDesc()) {
            if (!"DONATION".equals(b.getBatchType())) continue;
            if (b.getStatus() != BatchStatus.RECEIVED && b.getStatus() != BatchStatus.AID_GIVEN) continue;
            List<RecycleOrder> ready = readyOrders(b);
            if (ready.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("code", b.getCode());
            m.put("projectName", b.getProjectName());
            m.put("designatedTarget", b.getDesignatedTarget());
            m.put("organizationName", b.getOrganization() == null ? null : b.getOrganization().getOrganizationName());
            m.put("totalWeightKg", b.getTotalWeightKg());
            m.put("aidGivenQuantity", b.getAidGivenQuantity());
            m.put("readyOrderCount", ready.size());
            m.put("availableQuantity", ready.stream().mapToInt(RecycleOrder::aidAvailable).sum());
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
        List<RecycleOrder> ready = new ArrayList<>();
        for (RecycleOrder o : eligibleOrders(b)) {
            if (o.aidAvailable() > 0) ready.add(o); // 剩余可分配为 0（已发完/已预占完）不再候选
        }
        return ready;
    }

    /** 批次内按品类与隐私处置结论允许定向发放的回收单（不看剩余库存） */
    private List<RecycleOrder> eligibleOrders(Batch b) {
        List<RecycleOrder> members = b.getStatus() == BatchStatus.REJECTED
                ? b.getOriginalOrders() : b.getOrders();
        List<RecycleOrder> eligible = new ArrayList<>();
        for (RecycleOrder o : members) {
            SortReview r = sortRepo.findByOrder(o).orElse(null);
            // 仅可直接捐赠；需消毒整理、环保再生、不可回收、特殊处理一律不得发放
            if (r != null && r.getCategory() == SortCategory.DIRECT_DONATE
                    && r.getPrivacyAction() != PrivacyAction.REJECTED) {
                eligible.add(o);
            }
        }
        return eligible;
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
        List<RecycleOrder> eligible = eligibleOrders(b);
        Map<Long, RecycleOrder> eligibleMap = new HashMap<>();
        eligible.forEach(o -> eligibleMap.put(o.getId(), o));
        if (orderIds == null || orderIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择匹配的回收单");
        }
        // 去重并保持选择顺序
        List<Long> uniqueIds = orderIds.stream().distinct().toList();
        for (Long oid : uniqueIds) {
            if (!eligibleMap.containsKey(oid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "回收单 " + oid + " 不属于可直接捐赠的已签收衣物，不能定向发放（待消毒/再生/不可回收均不可）");
            }
        }
        // 企业/学校指定对象：若批次或家庭带指定对象，必须一致
        if (b.getDesignatedTarget() != null && f.getDesignatedTarget() != null
                && !b.getDesignatedTarget().equals(f.getDesignatedTarget())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该批为指定捐赠批次，与登记的指定对象不一致");
        }

        // ---- 锁定来源单库存：逐单 FOR UPDATE，并发匹配时总预占不超过剩余可分配 ----
        Map<Long, RecycleOrder> locked = lockOrders(uniqueIds);
        int totalAvailable = uniqueIds.stream().mapToInt(id -> locked.get(id).aidAvailable()).sum();
        int planned;
        if (plannedQuantity == null) {
            planned = totalAvailable; // 未指定件数时预占所选来源单全部剩余
        } else if (plannedQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "计划发放件数必须大于 0");
        } else {
            planned = plannedQuantity;
        }
        if (totalAvailable <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "所选回收单可分配库存已用完（全部已发放或被其他家庭预占），不能重复匹配");
        }
        if (planned > totalAvailable) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "来源衣物库存不足：所选回收单剩余可分配共 " + totalAvailable
                            + " 件，无法预占 " + planned + " 件（已发放与已预占部分不得超发）");
        }

        AidDistribution d = new AidDistribution();
        d.setFamily(f);
        d.setBatch(b);
        d.setMatchedBy(sorter);
        d.setMatchedOrderIds(uniqueIds.toString().replaceAll("[\\[\\] ]", ""));
        d.setPlannedQuantity(planned);
        d.setStatus(AidDistributionStatus.MATCHED);
        d = distRepo.save(d);

        // 按选择顺序逐单预占，生成发放明细并累加来源单已预占件数
        int remaining = planned;
        for (Long oid : uniqueIds) {
            if (remaining <= 0) break;
            RecycleOrder o = locked.get(oid);
            int take = Math.min(o.aidAvailable(), remaining);
            if (take <= 0) continue;
            o.setAidReservedQuantity(o.getAidReservedQuantity() + take);
            orderRepo.save(o);
            AidDistributionItem item = new AidDistributionItem();
            item.setDistribution(d);
            item.setOrder(o);
            item.setReservedQuantity(take);
            distItemRepo.save(item);
            remaining -= take;
        }
        f.setStatus(AidStatus.MATCHED);
        familyRepo.save(f);
        return d;
    }

    /** 按 id 升序逐单加写锁（SELECT ... FOR UPDATE，固定加锁顺序避免死锁），
     *  并强制从数据库重载最新库存，保证并发匹配时读取到的是已提交的最新预占/已发放数。 */
    private Map<Long, RecycleOrder> lockOrders(Collection<Long> ids) {
        em.flush(); // 先落库本事务内已做的库存变更（如换货先释放再预占的场景）
        Map<Long, RecycleOrder> locked = new LinkedHashMap<>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            RecycleOrder o = orderRepo.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "回收单 " + id + " 不存在"));
            em.refresh(o, LockModeType.PESSIMISTIC_WRITE);
            locked.put(id, o);
        }
        return locked;
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
        // 实领不得超过本任务预占库存（按来源单明细），防止超发
        List<AidDistributionItem> items = distItemRepo.findByDistributionIdOrderByIdAsc(d.getId());
        int remainingReserved = items.stream()
                .mapToInt(i -> i.getReservedQuantity() - i.getDistributedQuantity()).sum();
        if (qty > remainingReserved) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "实领数量超出该任务预占库存（剩余可领 " + remainingReserved
                            + " 件），不得超发；如需调整请先取消任务重新匹配");
        }
        String relation = str(dto.get("receiverRelation"));
        if (relation == null || relation.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请核验并填写领取人与登记人关系");
        }
        if (!"本人".equals(relation)) {
            String proxyName = str(dto.get("proxyName"));
            String auth = str(dto.get("proxyAuthNote"));
            if (proxyName == null || proxyName.isBlank() || auth == null || auth.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "代领必须登记代领人脱敏姓名，以及登记人电话确认或社区确认的授权依据，防止冒领");
            }
            d.setProxyName(proxyName);
            d.setProxyAuthNote(auth);
        }
        if (signPhoto == null || signPhoto.isBlank()) {
            String signature = str(dto.get("signatureNote"));
            if (signature == null || signature.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须上传签收照片或留存签字记录");
            }
            signPhoto = null; // 签字记录写 handNote
        }
        d.setReceiverRelation(relation);
        d.setActualQuantity(qty);
        d.setSignPhotoPath(signPhoto);
        d.setHandNote(str(dto.get("handNote")) != null ? str(dto.get("handNote")) : str(dto.get("signatureNote")));
        d.setHandedBy(operator);
        d.setHandedAt(LocalDateTime.now());
        d.setStatus(AidDistributionStatus.HANDED_OUT);
        distRepo.save(d);

        // ---- 按同一明细结转库存：预占 → 已发放；未领取部分释放回可分配池 ----
        Map<Long, RecycleOrder> locked = lockOrders(items.stream().map(i -> i.getOrder().getId()).toList());
        int left = qty;
        for (AidDistributionItem item : items) {
            RecycleOrder o = locked.get(item.getOrder().getId());
            int itemRemaining = item.getReservedQuantity() - item.getDistributedQuantity();
            int consume = Math.min(itemRemaining, left);
            o.setAidReservedQuantity(o.getAidReservedQuantity() - itemRemaining);
            o.setAidDistributedQuantity(o.getAidDistributedQuantity() + consume);
            orderRepo.save(o);
            item.setDistributedQuantity(item.getDistributedQuantity() + consume);
            distItemRepo.save(item);
            left -= consume;
        }

        AidFamily f = d.getFamily();
        f.setStatus(AidStatus.RECEIVED);
        f.setReceivedAt(d.getHandedAt());
        f.setBatch(d.getBatch());
        familyRepo.save(f);

        Batch b = d.getBatch();
        b.setAidGivenQuantity((b.getAidGivenQuantity() == null ? 0 : b.getAidGivenQuantity()) + qty);
        b.setStatus(BatchStatus.AID_GIVEN);
        b.setAidGivenAt(LocalDateTime.now());
        batchRepo.save(b);
        return d;
    }

    // ===================== 异常协同（不直改公示去向） =====================

    /** 异常挂在具体回收单上下文中，居民/社区/分拣/机构/回收员通过时间线协同；
     *  处置只能换货/退回重分/转家庭/补充分拣，不允许直接改写已公示批次去向。 */
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
     *  来源单库存按发放明细统一释放（未领预占）或扣减（已领退回）。 */
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
                // 取消 / 转其他家庭：释放未领预占，衣物回到可分配池
                releaseReservation(d);
                d.setStatus(AidDistributionStatus.CANCELLED);
                d.getFamily().setStatus(AidStatus.RESERVED);
                familyRepo.save(d.getFamily());
            }
            case "RETURN_RESORT", "ADDITIONAL_SORT" -> {
                // 退回重新分拣 / 补充分拣：释放未领预占；已签收部分按退回扣减，
                // 衣物离开可分配池进入重分，公示累计同步减少（只统计真实已发放）
                releaseReservation(d);
                returnDistributed(d);
                d.setStatus(AidDistributionStatus.RETURNED);
                d.getFamily().setStatus(AidStatus.RESERVED);
                familyRepo.save(d.getFamily());
            }
            case "EXCHANGE" -> {
                if (replacementBatchId == null || replacementOrderIds == null || replacementOrderIds.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "换货须选择替代批次与回收单");
                }
                // 换货：旧任务未领预占释放，新任务按替代来源单重新预占（库存校验同匹配）
                releaseReservation(d);
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

    /** 释放任务未领取的预占件数（按明细逐单释放回来源单可分配池） */
    private void releaseReservation(AidDistribution d) {
        List<AidDistributionItem> items = distItemRepo.findByDistributionIdOrderByIdAsc(d.getId());
        if (items.isEmpty()) return;
        Map<Long, RecycleOrder> locked = lockOrders(items.stream().map(i -> i.getOrder().getId()).toList());
        for (AidDistributionItem item : items) {
            int unreceived = item.getReservedQuantity() - item.getDistributedQuantity();
            if (unreceived <= 0) continue;
            RecycleOrder o = locked.get(item.getOrder().getId());
            o.setAidReservedQuantity(o.getAidReservedQuantity() - unreceived);
            orderRepo.save(o);
            item.setReservedQuantity(item.getDistributedQuantity());
            distItemRepo.save(item);
        }
    }

    /** 已签收任务的退回：按明细扣减来源单已发放件数（衣物退出可分配池进入重分），
     *  并同步减少批次公示累计，保证公示只统计真实已发放数量。 */
    private void returnDistributed(AidDistribution d) {
        if (d.getStatus() != AidDistributionStatus.HANDED_OUT) return;
        List<AidDistributionItem> items = distItemRepo.findByDistributionIdOrderByIdAsc(d.getId());
        int returned = items.stream().mapToInt(AidDistributionItem::getDistributedQuantity).sum();
        if (returned <= 0) return;
        Map<Long, RecycleOrder> locked = lockOrders(items.stream().map(i -> i.getOrder().getId()).toList());
        for (AidDistributionItem item : items) {
            if (item.getDistributedQuantity() <= 0) continue;
            RecycleOrder o = locked.get(item.getOrder().getId());
            o.setAidDistributedQuantity(o.getAidDistributedQuantity() - item.getDistributedQuantity());
            // 退回衣物进入重新分拣，不再属于可分配库存
            o.setAidAllocatableQuantity(Math.max(0,
                    o.getAidAllocatableQuantity() - item.getDistributedQuantity()));
            orderRepo.save(o);
            item.setReservedQuantity(item.getReservedQuantity() - item.getDistributedQuantity());
            item.setDistributedQuantity(0);
            distItemRepo.save(item);
        }
        Batch b = d.getBatch();
        b.setAidGivenQuantity(Math.max(0,
                (b.getAidGivenQuantity() == null ? 0 : b.getAidGivenQuantity()) - returned));
        batchRepo.save(b);
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
        // 公益价值只统计真实已发放数量：记账件数不得超过批次实际签收件数
        int given = b.getAidGivenQuantity() == null ? 0 : b.getAidGivenQuantity();
        if (quantity != null && quantity > given) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "记账件数超过该批真实已发放件数（" + given + " 件），公益价值不得按未发放数量入账");
        }
        AidValueRecord rec = new AidValueRecord();
        rec.setBatch(b);
        rec.setValueAmount(amount);
        rec.setQuantity(quantity != null ? quantity : b.getAidGivenQuantity());
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
