package com.clothing.recycle.controller;

import com.clothing.recycle.config.FileStorage;
import com.clothing.recycle.config.ViewMapper;
import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import com.clothing.recycle.security.CurrentUser;
import com.clothing.recycle.service.AidService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

/** 低收入家庭定向领取：需求登记、分拣匹配、领取核验、异常协同、回访、公益价值唯一记账 */
@RestController
@RequestMapping("/api/aid")
public class AidController {

    private final AidService aid;
    private final AidFamilyRepo familyRepo;
    private final AidDistributionRepo distRepo;
    private final AidIssueRepo issueRepo;
    private final AidIssueEventRepo eventRepo;
    private final AidVisitRepo visitRepo;
    private final AidValueRepo valueRepo;
    private final ViewMapper vm;
    private final CurrentUser cu;
    private final FileStorage storage;

    public AidController(AidService aid, AidFamilyRepo familyRepo, AidDistributionRepo distRepo,
                         AidIssueRepo issueRepo, AidIssueEventRepo eventRepo, AidVisitRepo visitRepo,
                         AidValueRepo valueRepo, ViewMapper vm, CurrentUser cu, FileStorage storage) {
        this.aid = aid;
        this.familyRepo = familyRepo;
        this.distRepo = distRepo;
        this.issueRepo = issueRepo;
        this.eventRepo = eventRepo;
        this.visitRepo = visitRepo;
        this.valueRepo = valueRepo;
        this.vm = vm;
        this.cu = cu;
        this.storage = storage;
    }

    // ---------- 家庭登记 ----------

    @GetMapping("/families")
    public List<Map<String, Object>> families() {
        cu.require(Role.COMMUNITY, Role.ORG, Role.SORTER, Role.FINANCE, Role.ADMIN);
        return familyRepo.findAllByOrderByCreatedAtDesc().stream().map(this::familyFull).toList();
    }

    @GetMapping("/families/{id}")
    public Map<String, Object> family(@PathVariable Long id) {
        cu.require(Role.COMMUNITY, Role.ORG, Role.SORTER, Role.ADMIN);
        return familyFull(familyRepo.findById(id).orElseThrow());
    }

    @PostMapping("/families")
    public Map<String, Object> register(@RequestBody Map<String, Object> dto) {
        cu.require(Role.COMMUNITY, Role.ADMIN);
        return familyFull(aid.register(cu.get(), dto));
    }

    // ---------- 分拣匹配 ----------

    @GetMapping("/candidates")
    public List<Map<String, Object>> candidates(@RequestParam Long familyId) {
        cu.require(Role.SORTER, Role.COMMUNITY, Role.ORG, Role.ADMIN);
        AidFamily f = familyRepo.findById(familyId).orElseThrow();
        return aid.candidateBatches(f);
    }

    @PostMapping("/match")
    public Map<String, Object> match(@RequestBody Map<String, Object> dto) {
        cu.require(Role.SORTER, Role.ADMIN);
        @SuppressWarnings("unchecked")
        List<Long> orderIds = ((List<Object>) dto.get("orderIds")).stream()
                .map(x -> Long.parseLong(x.toString())).toList();
        Integer qty = dto.get("plannedQuantity") == null ? null : Integer.parseInt(dto.get("plannedQuantity").toString());
        AidDistribution d = aid.match(Long.parseLong(dto.get("familyId").toString()),
                Long.parseLong(dto.get("batchId").toString()), orderIds, qty, cu.get());
        return distribution(d);
    }

    // ---------- 领取任务与核验 ----------

    @GetMapping("/distributions")
    public List<Map<String, Object>> distributions() {
        cu.require(Role.SORTER, Role.COMMUNITY, Role.ORG, Role.FINANCE, Role.ADMIN);
        return distRepo.findAllByOrderByCreatedAtDesc().stream().map(this::distribution).toList();
    }

    @PostMapping(value = "/distributions/{id}/handout", consumes = "multipart/form-data")
    public Map<String, Object> handout(@PathVariable Long id,
                                       @RequestParam Map<String, String> form,
                                       @RequestParam(required = false) MultipartFile signPhoto) throws Exception {
        cu.require(Role.COMMUNITY, Role.ORG, Role.ADMIN);
        String path = storage.save(signPhoto);
        return distribution(aid.handout(id, new HashMap<>(form), path, cu.get()));
    }

    // ---------- 异常协同（同一回收单） ----------

    @GetMapping("/issues")
    public List<Map<String, Object>> issues() {
        return issueRepo.findAllByOrderByCreatedAtDesc().stream().map(this::issue).toList();
    }

    @GetMapping("/issues/{id}")
    public Map<String, Object> issueDetail(@PathVariable Long id) {
        return issue(issueRepo.findById(id).orElseThrow());
    }

    @PostMapping("/issues")
    public Map<String, Object> openIssue(@RequestBody Map<String, Object> dto) {
        cu.require(Role.RESIDENT, Role.COLLECTOR, Role.SORTER, Role.COMMUNITY, Role.ORG, Role.ADMIN);
        AidIssueType type = AidIssueType.valueOf(dto.get("type").toString());
        AidIssue issue = aid.openIssue(Long.parseLong(dto.get("distributionId").toString()),
                Long.parseLong(dto.get("orderId").toString()), type,
                dto.getOrDefault("description", "").toString(), cu.get());
        return issue(issue);
    }

    @PostMapping("/issues/{id}/events")
    public Map<String, Object> issueEvent(@PathVariable Long id, @RequestBody Map<String, String> dto) {
        aid.addIssueEvent(id, cu.get(), dto.getOrDefault("content", ""));
        return issue(issueRepo.findById(id).orElseThrow());
    }

    @PostMapping("/issues/{id}/resolve")
    public Map<String, Object> resolveIssue(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        cu.require(Role.SORTER, Role.COMMUNITY, Role.ORG, Role.ADMIN);
        List<Long> repOrders = null;
        if (dto.get("replacementOrderIds") instanceof List<?> l) {
            repOrders = l.stream().map(x -> Long.parseLong(x.toString())).toList();
        }
        Long repBatch = dto.get("replacementBatchId") == null ? null
                : Long.parseLong(dto.get("replacementBatchId").toString());
        AidIssue issue = aid.resolveIssue(id, dto.get("action").toString(),
                (String) dto.get("note"), repBatch, repOrders, cu.get());
        return issue(issue);
    }

    // ---------- 回访 ----------

    @PostMapping("/families/{id}/visit")
    public Map<String, Object> visit(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        cu.require(Role.COMMUNITY, Role.ADMIN);
        return visit(aid.visit(id, dto, cu.get()));
    }

    @GetMapping("/families/{id}/visits")
    public List<Map<String, Object>> visits(@PathVariable Long id) {
        cu.require(Role.COMMUNITY, Role.ORG, Role.SORTER, Role.ADMIN);
        return visitRepo.findByFamilyIdOrderByVisitedAtDesc(id).stream().map(this::visit).toList();
    }

    // ---------- 公益价值唯一记账 ----------

    @PostMapping("/value")
    public Map<String, Object> value(@RequestBody Map<String, Object> dto) {
        cu.require(Role.FINANCE, Role.ADMIN);
        AidValueRecord rec = aid.recordValue(Long.parseLong(dto.get("batchId").toString()),
                new BigDecimal(dto.get("valueAmount").toString()),
                dto.get("quantity") == null ? null : Integer.parseInt(dto.get("quantity").toString()),
                (String) dto.get("remark"), cu.get());
        return value(rec);
    }

    @GetMapping("/value")
    public List<Map<String, Object>> values() {
        cu.require(Role.FINANCE, Role.COMMUNITY, Role.ADMIN);
        return valueRepo.findAll().stream().map(this::value).toList();
    }

    // ---------- 视图（内部视图含脱敏联系信息，公示另有聚合接口，不返回任何家庭明细） ----------

    private Map<String, Object> familyFull(AidFamily f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("maskedName", f.getMaskedName());
        m.put("maskedPhone", f.getMaskedPhone());
        m.put("communityName", f.getCommunityName());
        m.put("subdistrict", f.getSubdistrict());
        m.put("proofType", f.getProofType() == null ? null : f.getProofType().name());
        m.put("proofNote", f.getProofNote());
        m.put("familySize", f.getFamilySize());
        m.put("needCount", f.getNeedCount());
        m.put("genderAgeDesc", f.getGenderAgeDesc());
        m.put("sizes", f.getSizes());
        m.put("seasons", f.getSeasons());
        m.put("clothTypes", f.getClothTypes());
        m.put("acceptUsed", f.isAcceptUsed());
        m.put("needShoesBagsBedding", f.isNeedShoesBagsBedding());
        m.put("deliveryMethod", f.getDeliveryMethod() == null ? null : f.getDeliveryMethod().name());
        m.put("publicHidden", f.isPublicHidden());
        m.put("designatedTarget", f.getDesignatedTarget());
        m.put("needNote", f.getNeedNote());
        m.put("voucherNo", f.getVoucherNo());
        m.put("status", f.getStatus().name());
        m.put("createdAt", f.getCreatedAt());
        m.put("receivedAt", f.getReceivedAt());
        m.put("remark", f.getRemark());
        m.put("distributions", distRepo.findByFamilyOrderByCreatedAtDesc(f).stream().map(this::distribution).toList());
        return m;
    }

    private Map<String, Object> distribution(AidDistribution d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("status", d.getStatus().name());
        m.put("familyId", d.getFamily().getId());
        m.put("familyName", d.getFamily().getMaskedName());
        m.put("familyPublicHidden", d.getFamily().isPublicHidden());
        m.put("voucherNo", d.getFamily().getVoucherNo());
        m.put("communityName", d.getFamily().getCommunityName());
        m.put("batch", vm.batch(d.getBatch()));
        m.put("matchedOrderIds", d.getMatchedOrderIds());
        m.put("matchedBy", d.getMatchedBy().getDisplayName());
        m.put("plannedQuantity", d.getPlannedQuantity());
        m.put("actualQuantity", d.getActualQuantity());
        m.put("receiverRelation", d.getReceiverRelation());
        m.put("proxyName", d.getProxyName());
        m.put("proxyAuthNote", d.getProxyAuthNote());
        m.put("signPhotoUrl", ViewMapper.photoUrl(d.getSignPhotoPath()));
        m.put("handedBy", d.getHandedBy() == null ? null : d.getHandedBy().getDisplayName());
        m.put("handedAt", d.getHandedAt());
        m.put("handNote", d.getHandNote());
        m.put("rehandled", d.isRehandled());
        m.put("createdAt", d.getCreatedAt());
        m.put("issues", issueRepo.findByDistributionIdOrderByCreatedAtAsc(d.getId()).stream()
                .map(i -> Map.of("id", i.getId(), "type", i.getType().name(),
                        "typeLabel", AidService.typeLabel(i.getType()), "status", i.getStatus().name()))
                .toList());
        return m;
    }

    private Map<String, Object> issue(AidIssue i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("distributionId", i.getDistribution().getId());
        m.put("orderId", i.getOrder().getId());
        m.put("orderCode", i.getOrder().getCode());
        m.put("batchCode", i.getOrder().getBatch() == null ? null : i.getOrder().getBatch().getCode());
        m.put("type", i.getType().name());
        m.put("typeLabel", AidService.typeLabel(i.getType()));
        m.put("description", i.getDescription());
        m.put("status", i.getStatus().name());
        m.put("resolutionAction", i.getResolutionAction());
        m.put("resolutionNote", i.getResolutionNote());
        m.put("createdAt", i.getCreatedAt());
        m.put("events", eventRepo.findByIssueIdOrderByCreatedAtAsc(i.getId()).stream().map(e -> {
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

    private Map<String, Object> visit(AidVisit v) {
        return Map.of("id", v.getId(), "familyId", v.getFamily().getId(),
                "batchCode", v.getBatch() == null ? null : v.getBatch().getCode(),
                "visitedBy", v.getVisitedBy().getDisplayName(),
                "wearingSituation", v.getWearingSituation() == null ? "" : v.getWearingSituation(),
                "satisfaction", v.getSatisfaction(),
                "followupNeed", v.getFollowupNeed() == null ? "" : v.getFollowupNeed(),
                "note", v.getNote() == null ? "" : v.getNote(),
                "visitedAt", v.getVisitedAt());
    }

    private Map<String, Object> value(AidValueRecord r) {
        return Map.of("id", r.getId(), "batchId", r.getBatch().getId(),
                "batchCode", r.getBatch().getCode(),
                "projectName", r.getBatch().getProjectName() == null ? "" : r.getBatch().getProjectName(),
                "valueAmount", r.getValueAmount(), "quantity", r.getQuantity(),
                "recordedBy", r.getRecordedBy().getDisplayName(),
                "remark", r.getRemark() == null ? "" : r.getRemark(), "createdAt", r.getCreatedAt());
    }
}
