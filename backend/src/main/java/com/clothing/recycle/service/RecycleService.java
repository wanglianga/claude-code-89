package com.clothing.recycle.service;

import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 核心业务服务：回收单全生命周期 + 投诉协同 + 积分流水。
 * 控制器与种子数据统一调用此处，保证状态机与积分余额一致。
 */
@Service
public class RecycleService {

    private final UserRepo userRepo;
    private final OrderRepo orderRepo;
    private final PickupRepo pickupRepo;
    private final SortReviewRepo sortRepo;
    private final BatchRepo batchRepo;
    private final ComplaintRepo complaintRepo;
    private final PointsLedgerRepo ledgerRepo;
    private final PartnerRepo partnerRepo;
    private final AidFamilyRepo aidRepo;
    private final ProductRepo productRepo;
    private final ExchangeRepo exchangeRepo;
    private final ResortRecordRepo resortRecordRepo;

    public RecycleService(UserRepo userRepo, OrderRepo orderRepo, PickupRepo pickupRepo,
                          SortReviewRepo sortRepo, BatchRepo batchRepo, ComplaintRepo complaintRepo,
                          PointsLedgerRepo ledgerRepo, PartnerRepo partnerRepo, AidFamilyRepo aidRepo,
                          ProductRepo productRepo, ExchangeRepo exchangeRepo,
                          ResortRecordRepo resortRecordRepo) {
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.pickupRepo = pickupRepo;
        this.sortRepo = sortRepo;
        this.batchRepo = batchRepo;
        this.complaintRepo = complaintRepo;
        this.ledgerRepo = ledgerRepo;
        this.partnerRepo = partnerRepo;
        this.aidRepo = aidRepo;
        this.productRepo = productRepo;
        this.exchangeRepo = exchangeRepo;
        this.resortRecordRepo = resortRecordRepo;
    }

    private static BigDecimal bd(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return new BigDecimal(v.toString());
    }

    private static int integer(Object v, int dflt) {
        if (v == null || v.toString().isBlank()) return dflt;
        return Integer.parseInt(v.toString());
    }

    private static boolean bool(Object v) {
        return v != null && Boolean.parseBoolean(v.toString());
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    // ===================== 积分 =====================

    @Transactional
    public int pointsBalance(User resident) {
        return ledgerRepo.findFirstByResidentOrderByCreatedAtDesc(resident)
                .map(PointsLedger::getBalanceAfter).orElse(0);
    }

    @Transactional
    public PointsLedger addLedger(User resident, RecycleOrder order, PointsType type,
                                  int points, String remark) {
        int balance = pointsBalance(resident);
        PointsLedger l = new PointsLedger();
        l.setResident(resident);
        l.setOrder(order);
        l.setType(type);
        l.setPoints(points);
        l.setBalanceAfter(balance + points);
        l.setRemark(remark);
        return ledgerRepo.save(l);
    }

    /** 积分规则：积分 50/kg；积分+捐赠混合 25/kg；纯捐赠仅活动奖励 100 分 */
    private int computePoints(BigDecimal weightKg, String option, Partner partner) {
        if (weightKg == null) return 0;
        int w = weightKg.multiply(BigDecimal.valueOf(50)).setScale(0, RoundingMode.HALF_UP).intValue();
        return switch (option == null ? "" : option) {
            case "POINTS" -> w;
            case "MIXED" -> w / 2;
            case "DONATION" -> partner != null ? 100 : 0;
            default -> 0;
        };
    }

    // ===================== 预约 -> 上门 -> 确认 =====================

    @Transactional
    public RecycleOrder createOrder(User resident, Map<String, Object> dto) {
        RecycleOrder o = new RecycleOrder();
        o.setResident(resident);
        o.setCommunityName(str(dto.getOrDefault("communityName", resident.getCommunityName())));
        o.setItemCount(integer(dto.get("itemCount"), 0));
        o.setCategories(str(dto.get("categories")));
        o.setWashed(bool(dto.get("washed")));
        o.setHasShoesBagsBedding(bool(dto.get("hasShoesBagsBedding")));
        String address = str(dto.get("address"));
        if (address == null || address.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上门地址必填");
        }
        o.setAddress(address);
        String slot = str(dto.get("timeSlot"));
        if (slot == null || slot.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "可预约时段必选");
        }
        o.setTimeSlot(slot);
        o.setDonateWanted(bool(dto.get("donateWanted")));
        Object pid = dto.get("partnerId");
        if (pid != null && !pid.toString().isBlank()) {
            partnerRepo.findById(Long.parseLong(pid.toString())).ifPresent(o::setPartner);
        }
        o.setPublicHidden(bool(dto.get("publicHidden")));
        RecycleOrder saved = orderRepo.save(o);
        saved.setCode("RO2026" + String.format("%05d", saved.getId()));
        return orderRepo.save(saved);
    }

    @Transactional
    public RecycleOrder assignOrder(Long orderId, User collector) {
        RecycleOrder o = mustOrder(orderId);
        if (o.getStatus() != OrderStatus.PENDING && o.getStatus() != OrderStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前状态不可派单");
        }
        o.setCollector(collector);
        o.setStatus(OrderStatus.ASSIGNED);
        o.setAssignedAt(LocalDateTime.now());
        return orderRepo.save(o);
    }

    /** 回收员上门：称重、拍照、初步分类、记录迟到（居民确认在 residentConfirm 中完成）。
     *  强制证据：有效称重重量、初步分类、现场称重照片缺一不可，否则 4xx 且单据保持 ASSIGNED。 */
    @Transactional
    public PickupRecord recordPickup(Long orderId, User collector, Map<String, Object> dto, String photo) {
        RecycleOrder o = mustOrder(orderId);
        if (o.getStatus() != OrderStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅已派单单可上门登记");
        }
        // ---- 先做全部入参校验，任何一项不满足都不得落库、不得推进状态 ----
        BigDecimal weight = bd(dto.get("weightKg"));
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上门登记失败：请填写有效的现场称重重量");
        }
        String preCategories = str(dto.get("preCategories"));
        if (preCategories == null || preCategories.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上门登记失败：请填写现场初步分类");
        }
        if (photo == null || photo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "上门登记失败：必须上传现场称重/打包照片，作为称重证据与后续争议依据");
        }
        o.setCollector(collector);
        PickupRecord p = pickupRepo.findByOrder(o).orElseGet(PickupRecord::new);
        p.setOrder(o);
        p.setWeightKg(weight);
        p.setPhotoPath(photo);
        p.setPreCategories(preCategories);
        p.setArrivedLate(bool(dto.get("arrivedLate")));
        p.setNote(str(dto.get("note")));
        if (p.getArrivedAt() == null) p.setArrivedAt(LocalDateTime.now());
        pickupRepo.save(p);

        o.setStatus(OrderStatus.PICKED_UP);
        o.setPickedAt(LocalDateTime.now());
        o.setPickupWeight(weight);
        orderRepo.save(o);
        return p;
    }

    /** 居民确认积分 / 公益捐赠 / 混合选项，积分实时入账 */
    @Transactional
    public PickupRecord residentConfirm(Long orderId, User resident, String option) {
        RecycleOrder o = mustOrder(orderId);
        if (!o.getResident().getId().equals(resident.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅本人可确认");
        }
        PickupRecord p = pickupRepo.findByOrder(o)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "回收员尚未完成上门登记"));
        if (List.of("POINTS", "DONATION", "MIXED").contains(option)) {
            p.setConfirmOption(option);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认选项无效");
        }
        p.setResidentConfirmed(true);
        pickupRepo.save(p);

        // 同一回收单只发放一次积分
        boolean already = ledgerRepo.findByResidentOrderByCreatedAtDesc(resident).stream()
                .anyMatch(l -> l.getOrder() != null && l.getOrder().getId().equals(o.getId()));
        if (!already) {
            int pts = computePoints(p.getWeightKg(), option, o.getPartner());
            if (pts > 0) {
                PointsType type = "DONATION".equals(option) ? PointsType.CAMPAIGN_BONUS : PointsType.EARN;
                String remark = "DONATION".equals(option)
                        ? "公益捐赠活动奖励 · " + o.getCode()
                        : "回收积分 " + p.getWeightKg() + "kg · " + o.getCode();
                addLedger(resident, o, type, pts, remark);
            }
        }
        return p;
    }

    // ===================== 分拣中心复核 =====================

    /** 分拣中心复核。隐私风险衣物（校服/工作服/个人信息）强制处置：
     *  DESENSITIZED（脱敏后流转，须留存脱敏说明与处理后照片，且仅可进入捐赠/消毒整理类）
     *  或 REJECTED（拒收，终止公益流转）。禁止以 NONE 落库，所有校验先于任何持久化。 */
    @Transactional
    public SortReview sortReview(Long orderId, User sorter, Map<String, Object> dto, String photo) {
        return sortReview(orderId, sorter, dto, photo, null);
    }

    @Transactional
    public SortReview sortReview(Long orderId, User sorter, Map<String, Object> dto,
                                 String photo, String privacyPhoto) {
        RecycleOrder o = mustOrder(orderId);
        if (o.getStatus() != OrderStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅已上门回收单可分拣复核");
        }
        PickupRecord p = pickupRepo.findByOrder(o).orElseThrow();

        SortCategory cat;
        try {
            cat = SortCategory.valueOf(str(dto.get("category")));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分拣复核失败：请选择有效的复核分类");
        }
        BigDecimal weight = bd(dto.get("weightKg"));
        if (weight == null) weight = p.getWeightKg();
        boolean privacyRisk = bool(dto.get("privacyRisk"));
        String actionRaw = str(dto.get("privacyAction"));
        PrivacyAction action = PrivacyAction.NONE;
        if (actionRaw != null && !actionRaw.isBlank()) {
            try { action = PrivacyAction.valueOf(actionRaw); }
            catch (Exception e) { action = PrivacyAction.NONE; }
        }
        String privacyNote = str(dto.get("privacyNote"));

        // ---- 隐私风险衣物处置校验（先校验后落库，拒绝时不产生分拣记录） ----
        if (privacyRisk) {
            if (action != PrivacyAction.DESENSITIZED && action != PrivacyAction.REJECTED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "分拣复核失败：含校服/工作服/个人信息的隐私风险衣物，必须选择「脱敏后流转」或「拒收」，不得按无风险处理");
            }
            if (privacyNote == null || privacyNote.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "分拣复核失败：隐私衣物处置必须留存书面说明（脱敏措施或拒收依据）");
            }
            if (action == PrivacyAction.DESENSITIZED) {
                if (privacyPhoto == null || privacyPhoto.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "分拣复核失败：脱敏后流转必须上传脱敏处理后的复检照片作为证据");
                }
                if (cat != SortCategory.DIRECT_DONATE && cat != SortCategory.NEED_CLEAN) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "分拣复核失败：脱敏流转仅适用于可直接捐赠/需消毒整理类衣物；环保再生或不可回收类请直接拒收或按非风险登记");
                }
            }
        } else {
            action = PrivacyAction.NONE;
            privacyNote = null;
            privacyPhoto = null;
        }

        SortReview r = sortRepo.findByOrder(o).orElseGet(SortReview::new);
        r.setOrder(o);
        r.setSorter(sorter);
        r.setCategory(cat);
        r.setWeightKg(weight);
        r.setWeightDiff(weight.subtract(p.getWeightKg()));
        r.setDamageReason(str(dto.get("damageReason")));
        r.setDestination(str(dto.get("destination")));
        r.setPrivacyRisk(privacyRisk);
        r.setPrivacyAction(action);
        r.setPrivacyNote(privacyNote);
        r.setPrivacyPhotoPath(privacyPhoto);
        r.setPhotoPath(photo);
        sortRepo.save(r);

        // 不可回收 / 特殊处理 / 隐私拒收 → 终止公益流转
        if (cat == SortCategory.NON_RECYCLABLE || cat == SortCategory.SPECIAL
                || action == PrivacyAction.REJECTED) {
            o.setStatus(OrderStatus.REJECTED);
        } else {
            o.setStatus(OrderStatus.SORTED);
        }
        o.setSortedAt(LocalDateTime.now());
        orderRepo.save(o);
        return r;
    }

    // ===================== 批次 / 发运 / 签收 / 再生 =====================

    @Transactional
    public Batch createBatch(User creator, Map<String, Object> dto) {
        String batchType = str(dto.get("batchType")); // DONATION / RECYCLE
        List<Long> ids = parseIds(dto.get("orderIds"));
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择入批回收单");
        Object srcId = dto.get("sourceBatchId");

        // ---- 先校验全部回收单（任一项不满足则整体失败，不落空批次、不改状态） ----
        List<RecycleOrder> toAdd = new ArrayList<>();
        List<SortReview> reviews = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int items = 0;
        for (Long id : ids) {
            RecycleOrder o = mustOrder(id);
            if (o.getStatus() != OrderStatus.SORTED || o.getCurrentBatch() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "回收单 " + o.getCode() + " 未处于可入批状态");
            }
            SortReview r = sortRepo.findByOrder(o)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            o.getCode() + " 缺少分拣复核记录"));
            if (srcId != null && (o.getBatch() == null
                    || !o.getBatch().getId().equals(Long.parseLong(srcId.toString())))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        o.getCode() + " 不属于所选拒收来源批次");
            }
            if (r.getPrivacyAction() == PrivacyAction.REJECTED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        o.getCode() + " 为隐私拒收单，依法不得进入公益流转批次");
            }
            if (r.isPrivacyRisk() && r.getPrivacyAction() == PrivacyAction.DESENSITIZED
                    && (r.getPrivacyPhotoPath() == null || r.getPrivacyPhotoPath().isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        o.getCode() + " 缺少脱敏处理后复检照片，不得入批");
            }
            if ("DONATION".equals(batchType)
                    && r.getCategory() != SortCategory.DIRECT_DONATE
                    && r.getCategory() != SortCategory.NEED_CLEAN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        o.getCode() + " 非捐赠类，不能入捐赠批次");
            }
            if ("RECYCLE".equals(batchType) && r.getCategory() != SortCategory.ECO_RECYCLE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        o.getCode() + " 非环保再生类，不能入再生批次");
            }
            toAdd.add(o);
            reviews.add(r);
            total = total.add(r.getWeightKg());
            items += o.getItemCount() == null ? 0 : o.getItemCount();
        }

        // ---- 全部通过后才建批 ----
        Batch b = new Batch();
        b.setCode("B2026" + String.format("%05d", new Random().nextInt(100000)));
        b.setBatchType(batchType);
        b.setProjectName(str(dto.get("projectName")));
        b.setRecyclerName(str(dto.get("recyclerName")));
        b.setPublicNote(str(dto.get("publicNote")));
        b.setDesignatedTarget(str(dto.get("designatedTarget")));
        Object orgId = dto.get("organizationId");
        if (orgId != null && !orgId.toString().isBlank()) {
            b.setOrganization(mustUser(Long.parseLong(orgId.toString())));
        }
        Object pid = dto.get("partnerId");
        Partner partner = null;
        if (pid != null && !pid.toString().isBlank()) {
            partner = partnerRepo.findById(Long.parseLong(pid.toString())).orElse(null);
            b.setPartner(partner);
        }
        if (srcId != null && !srcId.toString().isBlank()) {
            Batch src = mustBatch(Long.parseLong(srcId.toString()));
            if (src.getStatus() != BatchStatus.REJECTED || src.getResortAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "来源批次未完成拒收后重新分拣，不能作为再分配来源");
            }
            b.setSourceBatch(src);
        }
        b = batchRepo.save(b);

        for (int i = 0; i < toAdd.size(); i++) {
            RecycleOrder o = toAdd.get(i);
            if (o.getBatch() == null) o.setBatch(b); // 首次入批：记录不可变来源
            o.setCurrentBatch(b);                    // 当前归属指向再分配批次
            orderRepo.save(o);
        }
        b.setTotalWeightKg(total);
        b.setItemCount(items);
        return batchRepo.save(b);
    }

    @Transactional
    public Batch shipBatch(Long batchId) {
        Batch b = mustBatch(batchId);
        if (b.getStatus() != BatchStatus.STAGED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅集货中批次可发运");
        }
        b.setStatus(BatchStatus.IN_TRANSIT);
        b.setShippedAt(LocalDateTime.now());
        for (RecycleOrder o : b.getOrders()) {
            o.setStatus(OrderStatus.IN_TRANSIT);
            orderRepo.save(o);
        }
        // 活动募集进度
        if (b.getPartner() != null) {
            Partner p = b.getPartner();
            p.setCollectedKg((p.getCollectedKg() == null ? BigDecimal.ZERO : p.getCollectedKg())
                    .add(b.getTotalWeightKg()));
            partnerRepo.save(p);
        }
        return batchRepo.save(b);
    }

    @Transactional
    public Batch signBatch(Long batchId, User orgUser, Map<String, Object> dto, String signPhoto) {
        Batch b = mustBatch(batchId);
        if (b.getStatus() != BatchStatus.IN_TRANSIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅在途批次可签收");
        }
        b.setStatus(BatchStatus.RECEIVED);
        b.setReceiverName(str(dto.get("receiverName")));
        b.setSignNote(str(dto.get("signNote")));
        b.setSignPhotoPath(signPhoto);
        b.setSignedAt(LocalDateTime.now());
        for (RecycleOrder o : b.getOrders()) {
            o.setStatus(OrderStatus.DONATED);
            orderRepo.save(o);
        }
        return batchRepo.save(b);
    }

    /** 公益机构拒收：记录拒收类型/原因/复核照片，批次保留为可追溯证据，
     *  回收单退回为 RETURNED 待重新分拣（保留与拒收批次的关联，不抹除流向）。 */
    @Transactional
    public Batch rejectBatch(Long batchId, User orgUser, RejectReasonType reasonType,
                             String reason, String rejectPhoto) {
        Batch b = mustBatch(batchId);
        if (b.getStatus() != BatchStatus.IN_TRANSIT && b.getStatus() != BatchStatus.STAGED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前批次状态不可拒收");
        }
        if (reasonType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "拒收失败：请选择拒收原因类型（尺码/季节/卫生/其他）");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "拒收失败：必须填写拒收原因说明");
        }
        if (rejectPhoto == null || rejectPhoto.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "拒收失败：请上传现场复核照片作为拒收证据");
        }
        b.setStatus(BatchStatus.REJECTED);
        b.setRejectReasonType(reasonType);
        b.setRejectReason(reason);
        b.setRejectPhotoPath(rejectPhoto);
        for (RecycleOrder o : b.getOrders()) {
            o.setStatus(OrderStatus.RETURNED);
            orderRepo.save(o);
        }
        return batchRepo.save(b);
    }    /** 拒收退回后分拣中心重新分拣：逐单记录新分类/重量变化/结论/原因与复核照片；
     *  REDONATE/TO_RECYCLE 退回可入批池，FINAL_REJECT 终止。重量变化汇总到拒收批次。 */
    @Transactional
    @SuppressWarnings("unchecked")
    public Batch resortBatch(Long batchId, User sorter, Map<String, Object> dto, String resortPhoto) {
        Batch b = mustBatch(batchId);
        if (b.getStatus() != BatchStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅被拒收批次可重新分拣");
        }
        List<Map<String, Object>> entries = (List<Map<String, Object>>) dto.getOrDefault("entries", List.of());
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请逐单提交重新分拣结果");
        }
        BigDecimal resortWeight = BigDecimal.ZERO;
        for (Map<String, Object> e : entries) {
            Long oid = Long.parseLong(e.get("orderId").toString());
            RecycleOrder o = mustOrder(oid);
            if (o.getCurrentBatch() == null || !o.getCurrentBatch().getId().equals(b.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "回收单 " + o.getCode() + " 不属于该拒收批次");
            }
            BigDecimal w = bd(e.get("weightKg"));
            if (w == null || w.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "回收单 " + o.getCode() + " 重新分拣重量无效");
            }
            SortCategory newCat = SortCategory.valueOf(str(e.get("newCategory")));
            ResortOutcome outcome = ResortOutcome.valueOf(str(e.get("outcome")));
            if (outcome == ResortOutcome.TO_RECYCLE && newCat != SortCategory.ECO_RECYCLE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        o.getCode() + " 转环保再生时新分类必须为环保再生");
            }
            String perReason = str(e.get("reason"));

            ResortRecord rr = new ResortRecord();
            rr.setRejectedBatch(b);
            rr.setOrder(o);
            rr.setSorter(sorter);
            rr.setNewCategory(newCat);
            rr.setWeightKg(w);
            SortReview old = sortRepo.findByOrder(o).orElseThrow();
            rr.setWeightDiff(w.subtract(old.getWeightKg()));
            rr.setReason(perReason);
            rr.setPhotoPath(str(e.get("photoPath")));
            rr.setOutcome(outcome);
            resortRecordRepo.save(rr);

            // 更新最新分拣结论（原始结论保留在拒收批次与 ResortRecord 中）
            old.setCategory(newCat);
            old.setWeightKg(w);
            old.setWeightDiff(w.subtract(o.getPickupWeight() == null ? w : o.getPickupWeight()));
            if (perReason != null) old.setDamageReason(perReason);
            old.setDestination(str(e.getOrDefault("destination", old.getDestination())));
            sortRepo.save(old);

            if (outcome == ResortOutcome.FINAL_REJECT) {
                o.setStatus(OrderStatus.REJECTED);
            } else {
                o.setStatus(OrderStatus.SORTED);
                o.setCurrentBatch(null); // 释放回可入批池，来源批次 batch 字段保留
                resortWeight = resortWeight.add(w);
            }
            o.setSortedAt(LocalDateTime.now());
            orderRepo.save(o);
        }
        b.setResortSummary(str(dto.get("resortSummary")));
        b.setResortReason(str(dto.get("resortReason")));
        b.setResortWeightKg(resortWeight);
        b.setResortPhotoPath(resortPhoto);
        b.setResortSorter(sorter);
        b.setResortAt(LocalDateTime.now());
        return batchRepo.save(b);
    }

    @Transactional
    public Batch recycleBatch(Long batchId, Map<String, Object> dto) {
        Batch b = mustBatch(batchId);
        if (b.getStatus() != BatchStatus.IN_TRANSIT && b.getStatus() != BatchStatus.STAGED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前批次状态不可登记再生");
        }
        b.setStatus(BatchStatus.RECYCLED);
        BigDecimal recycled = bd(dto.get("recycledWeightKg"));
        b.setRecycledWeightKg(recycled != null ? recycled : b.getTotalWeightKg());
        String extra = str(dto.get("publicNote"));
        if (extra != null && !extra.isBlank()) {
            // 保留建批时的替代去向说明（拒收原因/处理机构/重量变化），追加再生处理结果
            b.setPublicNote(b.getPublicNote() == null || b.getPublicNote().isBlank()
                    ? extra : b.getPublicNote() + "；" + extra);
        }
        b.setRecycledAt(LocalDateTime.now());
        for (RecycleOrder o : b.getOrders()) {
            o.setStatus(OrderStatus.RECYCLED);
            orderRepo.save(o);
        }
        return batchRepo.save(b);
    }

    // ===================== 低收入家庭定向领取 =====================

    @Transactional
    public AidFamily registerAid(User operator, Map<String, Object> dto) {
        AidFamily f = new AidFamily();
        f.setMaskedName(str(dto.get("maskedName")));
        f.setMaskedPhone(str(dto.get("maskedPhone")));
        f.setCommunityName(str(dto.getOrDefault("communityName", operator.getCommunityName())));
        f.setFamilySize(integer(dto.get("familySize"), 1));
        f.setNeedNote(str(dto.get("needNote")));
        f.setOperator(operator);
        f.setVoucherNo("AID2026" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        return aidRepo.save(f);
    }

    @Transactional
    public AidFamily deliverAid(Long familyId, Long batchId, User operator, String remark) {
        AidFamily f = aidRepo.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "家庭登记不存在"));
        Batch b = mustBatch(batchId);
        if (b.getStatus() != BatchStatus.RECEIVED && b.getStatus() != BatchStatus.AID_GIVEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅公益机构已签收批次可定向发放");
        }
        f.setStatus(AidStatus.RECEIVED);
        f.setBatch(b);
        f.setOperator(operator);
        f.setRemark(remark);
        f.setReceivedAt(LocalDateTime.now());
        aidRepo.save(f);

        b.setStatus(BatchStatus.AID_GIVEN);
        b.setAidGivenAt(LocalDateTime.now());
        batchRepo.save(b);
        return f;
    }

    // ===================== 投诉协同（六角色同一单） =====================

    private static final Map<ComplaintType, Role> HANDLER_ROLE = Map.of(
            ComplaintType.WEIGHT_DISPUTE, Role.COLLECTOR,
            ComplaintType.LATE_VISIT, Role.COLLECTOR,
            ComplaintType.MISJUDGED_DONATE, Role.SORTER,
            ComplaintType.ORG_REJECTED, Role.ORG,
            ComplaintType.POINTS_MISSING, Role.FINANCE,
            ComplaintType.OPAQUE_DESTINATION, Role.COMMUNITY
    );

    @Transactional
    public Complaint openComplaint(Long orderId, User resident, ComplaintType type, String description) {
        RecycleOrder o = mustOrder(orderId);
        Complaint c = new Complaint();
        c.setOrder(o);
        c.setResident(resident);
        c.setType(type);
        c.setDescription(description);
        c.setStatus(ComplaintStatus.OPEN);
        Role handlerRole = HANDLER_ROLE.getOrDefault(type, Role.COMMUNITY);
        userRepo.findByRole(handlerRole).stream().findFirst().ifPresent(c::setHandler);
        c = complaintRepo.save(c);

        ComplaintEvent e = new ComplaintEvent();
        e.setComplaint(c);
        e.setAuthor(resident);
        e.setPartyRole(roleLabel(resident.getRole()));
        e.setContent(description);
        c.getEvents().add(e);
        return complaintRepo.save(c);
    }

    @Transactional
    public Complaint addComplaintEvent(Long complaintId, User author, String content) {
        Complaint c = mustComplaint(complaintId);
        ComplaintEvent e = new ComplaintEvent();
        e.setComplaint(c);
        e.setAuthor(author);
        e.setPartyRole(roleLabel(author.getRole()));
        e.setContent(content);
        c.getEvents().add(e);
        if (c.getStatus() == ComplaintStatus.OPEN) c.setStatus(ComplaintStatus.PROCESSING);
        // 允许任意参与角色主动牵头
        if (c.getHandler() == null) c.setHandler(author);
        return complaintRepo.save(c);
    }

    @Transactional
    public Complaint resolveComplaint(Long complaintId, User resolver,
                                      String resolutionNote, Integer compensatePoints) {
        Complaint c = mustComplaint(complaintId);
        c.setStatus(ComplaintStatus.RESOLVED);
        c.setResolutionNote(resolutionNote);
        c.setResolvedAt(LocalDateTime.now());

        ComplaintEvent e = new ComplaintEvent();
        e.setComplaint(c);
        e.setAuthor(resolver);
        e.setPartyRole(roleLabel(resolver.getRole()));
        StringBuilder content = new StringBuilder("处理结果：" + resolutionNote);
        if (compensatePoints != null && compensatePoints > 0) {
            addLedger(c.getResident(), c.getOrder(), PointsType.ADJUST, compensatePoints,
                    "投诉补发积分 · 投诉单#" + c.getId());
            content.append("；财务补发积分 ").append(compensatePoints).append(" 分");
        }
        e.setContent(content.toString());
        c.getEvents().add(e);
        return complaintRepo.save(c);
    }

    // ===================== 积分兑换 / 财务 =====================

    @Transactional
    public ExchangeOrder exchange(User resident, Long productId, int quantity) {
        if (quantity <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "兑换数量无效");
        Product p = productRepo.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        if (p.getStock() < quantity) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足");
        int total = p.getPointsCost() * quantity;
        if (pointsBalance(resident) < total) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "积分余额不足");
        }
        p.setStock(p.getStock() - quantity);
        productRepo.save(p);

        ExchangeOrder eo = new ExchangeOrder();
        eo.setResident(resident);
        eo.setProduct(p);
        eo.setQuantity(quantity);
        eo.setTotalPoints(total);
        exchangeRepo.save(eo);

        addLedger(resident, null, PointsType.SPEND, -total,
                "兑换 " + p.getName() + " ×" + quantity);
        return eo;
    }

    @Transactional
    public ExchangeOrder deliverExchange(Long exchangeId, User finance) {
        ExchangeOrder eo = exchangeRepo.findById(exchangeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "兑换单不存在"));
        if (eo.getStatus() != ExchangeStatus.ORDERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "兑换单已处理");
        }
        eo.setStatus(ExchangeStatus.DELIVERED);
        eo.setDeliveredAt(LocalDateTime.now());
        return exchangeRepo.save(eo);
    }

    @Transactional
    public PointsLedger financeAdjust(User finance, Long residentId, int points, String remark) {
        User resident = mustUser(residentId);
        if (resident.getRole() != Role.RESIDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅居民账户可调整积分");
        }
        return addLedger(resident, null, PointsType.ADJUST, points,
                "财务调整：" + (remark == null ? "" : remark));
    }

    // ===================== 居民公示隐私设置 =====================

    @Transactional
    public RecycleOrder setPublicHidden(Long orderId, User resident, boolean hidden) {
        RecycleOrder o = mustOrder(orderId);
        if (!o.getResident().getId().equals(resident.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅本人可设置公示隐私");
        }
        o.setPublicHidden(hidden);
        return orderRepo.save(o);
    }

    // ===================== 工具 =====================

    public String roleLabel(Role role) {
        return Map.of(
                Role.RESIDENT, "居民",
                Role.COLLECTOR, "回收员",
                Role.SORTER, "分拣中心",
                Role.COMMUNITY, "社区",
                Role.ORG, "公益机构",
                Role.FINANCE, "积分财务",
                Role.ADMIN, "平台管理员"
        ).get(role);
    }

    private List<Long> parseIds(Object raw) {
        if (raw == null) return List.of();
        List<Long> out = new ArrayList<>();
        if (raw instanceof Collection<?> col) {
            for (Object o : col) out.add(Long.parseLong(o.toString()));
        } else {
            for (String s : raw.toString().split(",")) {
                if (!s.isBlank()) out.add(Long.parseLong(s.trim()));
            }
        }
        return out;
    }

    public RecycleOrder mustOrder(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "回收单不存在"));
    }

    public Batch mustBatch(Long id) {
        return batchRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在"));
    }

    public Complaint mustComplaint(Long id) {
        return complaintRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "投诉单不存在"));
    }

    public User mustUser(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }
}
