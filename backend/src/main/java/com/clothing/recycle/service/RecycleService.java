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

    public RecycleService(UserRepo userRepo, OrderRepo orderRepo, PickupRepo pickupRepo,
                          SortReviewRepo sortRepo, BatchRepo batchRepo, ComplaintRepo complaintRepo,
                          PointsLedgerRepo ledgerRepo, PartnerRepo partnerRepo, AidFamilyRepo aidRepo,
                          ProductRepo productRepo, ExchangeRepo exchangeRepo) {
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

    /** 回收员上门：称重、拍照、初步分类、记录迟到（居民确认在 residentConfirm 中完成） */
    @Transactional
    public PickupRecord recordPickup(Long orderId, User collector, Map<String, Object> dto, String photo) {
        RecycleOrder o = mustOrder(orderId);
        if (o.getStatus() != OrderStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅已派单单可上门登记");
        }
        o.setCollector(collector);
        BigDecimal weight = bd(dto.get("weightKg"));
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "称重必须大于 0");
        }
        PickupRecord p = pickupRepo.findByOrder(o).orElseGet(PickupRecord::new);
        p.setOrder(o);
        p.setWeightKg(weight);
        p.setPhotoPath(photo);
        p.setPreCategories(str(dto.get("preCategories")));
        p.setArrivedLate(bool(dto.get("arrivedLate")));
        p.setNote(str(dto.get("note")));
        p.setArrivedAt(LocalDateTime.now());
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

    @Transactional
    public SortReview sortReview(Long orderId, User sorter, Map<String, Object> dto, String photo) {
        RecycleOrder o = mustOrder(orderId);
        if (o.getStatus() != OrderStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅已上门回收单可分拣复核");
        }
        PickupRecord p = pickupRepo.findByOrder(o).orElseThrow();
        SortCategory cat = SortCategory.valueOf(str(dto.get("category")));
        BigDecimal weight = bd(dto.get("weightKg"));
        if (weight == null) weight = p.getWeightKg();

        SortReview r = sortRepo.findByOrder(o).orElseGet(SortReview::new);
        r.setOrder(o);
        r.setSorter(sorter);
        r.setCategory(cat);
        r.setWeightKg(weight);
        r.setWeightDiff(weight.subtract(p.getWeightKg()));
        r.setDamageReason(str(dto.get("damageReason")));
        r.setDestination(str(dto.get("destination")));
        r.setPrivacyRisk(bool(dto.get("privacyRisk")));
        PrivacyAction action = r.isPrivacyRisk()
                ? PrivacyAction.valueOf(str(dto.getOrDefault("privacyAction", "NONE")))
                : PrivacyAction.NONE;
        r.setPrivacyAction(action);
        r.setPrivacyNote(str(dto.get("privacyNote")));
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

        Batch b = new Batch();
        b.setCode("B2026" + String.format("%05d", new Random().nextInt(100000)));
        b.setBatchType(batchType);
        b.setProjectName(str(dto.get("projectName")));
        b.setRecyclerName(str(dto.get("recyclerName")));
        b.setPublicNote(str(dto.get("publicNote")));
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
        b = batchRepo.save(b);

        BigDecimal total = BigDecimal.ZERO;
        int items = 0;
        for (Long id : ids) {
            RecycleOrder o = mustOrder(id);
            if (o.getStatus() != OrderStatus.SORTED || o.getBatch() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "回收单 " + o.getCode() + " 未处于可入批状态");
            }
            SortReview r = sortRepo.findByOrder(o).orElseThrow();
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
            o.setBatch(b);
            orderRepo.save(o);
            total = total.add(r.getWeightKg());
            items += o.getItemCount() == null ? 0 : o.getItemCount();
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

    /** 公益机构拒收：批次退回，回收单回到分拣环节重新安排去向 */
    @Transactional
    public Batch rejectBatch(Long batchId, User orgUser, String reason) {
        Batch b = mustBatch(batchId);
        if (b.getStatus() != BatchStatus.IN_TRANSIT && b.getStatus() != BatchStatus.STAGED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前批次状态不可拒收");
        }
        b.setStatus(BatchStatus.REJECTED);
        b.setRejectReason(reason);
        for (RecycleOrder o : b.getOrders()) {
            o.setBatch(null);
            o.setStatus(OrderStatus.SORTED);
            orderRepo.save(o);
        }
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
        b.setPublicNote(str(dto.getOrDefault("publicNote", b.getPublicNote())));
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
