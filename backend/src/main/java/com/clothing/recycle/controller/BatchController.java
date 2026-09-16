package com.clothing.recycle.controller;

import com.clothing.recycle.config.FileStorage;
import com.clothing.recycle.config.ViewMapper;
import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import com.clothing.recycle.security.CurrentUser;
import com.clothing.recycle.service.RecycleService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 批次：集货入批、发运、公益机构签收/拒收、再生登记、捐赠照片 */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchRepo batchRepo;
    private final OrderRepo orderRepo;
    private final SortReviewRepo sortRepo;
    private final UserRepo userRepo;
    private final RecycleService svc;
    private final ViewMapper vm;
    private final CurrentUser cu;
    private final FileStorage storage;

    public BatchController(BatchRepo batchRepo, OrderRepo orderRepo, SortReviewRepo sortRepo,
                           UserRepo userRepo, RecycleService svc, ViewMapper vm, CurrentUser cu,
                           FileStorage storage) {
        this.batchRepo = batchRepo;
        this.orderRepo = orderRepo;
        this.sortRepo = sortRepo;
        this.userRepo = userRepo;
        this.svc = svc;
        this.vm = vm;
        this.cu = cu;
        this.storage = storage;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        var stream = batchRepo.findAllByOrderByCreatedAtDesc().stream();
        if (status != null && !status.isBlank()) {
            stream = stream.filter(b -> b.getStatus().name().equals(status));
        }
        return stream.map(vm::batch).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return vm.batch(svc.mustBatch(id));
    }

    /** 可入批的已分拣回收单（batchType=DONATION|RECYCLE），可按来源拒收批筛选 */
    @GetMapping("/sorted-orders")
    public List<Map<String, Object>> sortedOrders(@RequestParam String batchType,
                                                  @RequestParam(required = false) Long sourceBatchId) {
        cu.require(Role.SORTER, Role.ADMIN, Role.COMMUNITY);
        java.util.List<RecycleOrder> base;
        if (sourceBatchId != null) {
            Batch src = svc.mustBatch(sourceBatchId);
            base = src.getStatus() == BatchStatus.REJECTED ? src.getOriginalOrders() : src.getOrders();
        } else {
            base = orderRepo.findAllByOrderByCreatedAtDesc();
        }
        return base.stream()
                .filter(o -> o.getStatus() == OrderStatus.SORTED && o.getCurrentBatch() == null)
                .filter(o -> sortRepo.findByOrder(o).map(r ->
                        "RECYCLE".equals(batchType)
                                ? r.getCategory() == SortCategory.ECO_RECYCLE
                                : r.getCategory() == SortCategory.DIRECT_DONATE
                                   || r.getCategory() == SortCategory.NEED_CLEAN
                ).orElse(false))
                .map(vm::order).toList();
    }

    /** 拒收批次列表（分拣中心重新分拣入口） */
    @GetMapping("/rejected")
    public List<Map<String, Object>> rejected() {
        cu.require(Role.SORTER, Role.ADMIN, Role.COMMUNITY);
        return batchRepo.findByStatusOrderByCreatedAtDesc(BatchStatus.REJECTED)
                .stream().map(vm::batch).toList();
    }

    /** 公益机构名单（建批下拉） */
    @GetMapping("/organizations")
    public List<Map<String, Object>> organizations() {
        cu.require(Role.SORTER, Role.ADMIN, Role.COMMUNITY);
        return userRepo.findByRole(Role.ORG).stream()
                .map(u -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("organizationName", u.getOrganizationName() == null ? "" : u.getOrganizationName());
                    m.put("displayName", u.getDisplayName());
                    return m;
                })
                .toList();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> dto) {
        cu.require(Role.SORTER, Role.ADMIN);
        return vm.batch(svc.createBatch(cu.get(), dto));
    }

    /** 分拣中心上传捐赠照片（打包/装车） */
    @PostMapping(value = "/{id}/donation-photo", consumes = "multipart/form-data")
    public Map<String, Object> donationPhoto(@PathVariable Long id,
                                             @RequestParam(required = false) MultipartFile photo) throws Exception {
        cu.require(Role.SORTER, Role.ADMIN);
        Batch b = svc.mustBatch(id);
        b.setDonationPhotoPath(storage.save(photo));
        return vm.batch(batchRepo.save(b));
    }

    @PostMapping("/{id}/ship")
    public Map<String, Object> ship(@PathVariable Long id) {
        cu.require(Role.SORTER, Role.ADMIN);
        return vm.batch(svc.shipBatch(id));
    }

    /** 公益机构签收（multipart：签收照片 + 字段） */
    @PostMapping(value = "/{id}/sign", consumes = "multipart/form-data")
    public Map<String, Object> sign(@PathVariable Long id,
                                    @RequestParam Map<String, String> form,
                                    @RequestParam(required = false) MultipartFile photo) throws Exception {
        cu.require(Role.ORG);
        return vm.batch(svc.signBatch(id, cu.get(), new java.util.HashMap<>(form), storage.save(photo)));
    }

    /** 公益机构拒收：必须提供拒收类型、原因说明与现场复核照片（multipart） */
    @PostMapping(value = "/{id}/reject", consumes = "multipart/form-data")
    public Map<String, Object> reject(@PathVariable Long id,
                                      @RequestParam Map<String, String> form,
                                      @RequestParam(required = false) MultipartFile rejectPhoto) throws Exception {
        cu.require(Role.ORG, Role.COMMUNITY, Role.ADMIN);
        com.clothing.recycle.model.RejectReasonType type;
        try {
            type = com.clothing.recycle.model.RejectReasonType.valueOf(form.get("rejectReasonType"));
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "请选择拒收原因类型（尺码/季节/卫生/其他）");
        }
        return vm.batch(svc.rejectBatch(id, cu.get(), type,
                form.getOrDefault("reason", ""), storage.save(rejectPhoto)));
    }

    /** 拒收退回后分拣中心重新分拣（multipart：entries JSON + 汇总照片） */
    @PostMapping(value = "/{id}/resort", consumes = "multipart/form-data")
    public Map<String, Object> resort(@PathVariable Long id,
                                      @RequestParam("entries") String entriesJson,
                                      @RequestParam(required = false) String resortSummary,
                                      @RequestParam(required = false) String resortReason,
                                      @RequestParam(required = false) MultipartFile resortPhoto) throws Exception {
        cu.require(Role.SORTER, Role.ADMIN);
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> entries =
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(entriesJson, java.util.List.class);
        java.util.Map<String, Object> dto = new java.util.HashMap<>();
        dto.put("entries", entries);
        dto.put("resortSummary", resortSummary);
        dto.put("resortReason", resortReason);
        return vm.batch(svc.resortBatch(id, cu.get(), dto, storage.save(resortPhoto)));
    }

    /** 再生处理登记（再生处理量、公示说明） */
    @PostMapping("/{id}/recycle-json")
    public Map<String, Object> recycle(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        cu.require(Role.SORTER, Role.ADMIN);
        return vm.batch(svc.recycleBatch(id, dto));
    }
}
