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

    /** 可入批的已分拣回收单（batchType=DONATION|RECYCLE） */
    @GetMapping("/sorted-orders")
    public List<Map<String, Object>> sortedOrders(@RequestParam String batchType) {
        cu.require(Role.SORTER, Role.ADMIN, Role.COMMUNITY);
        return orderRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getStatus() == OrderStatus.SORTED && o.getBatch() == null)
                .filter(o -> sortRepo.findByOrder(o).map(r ->
                        "RECYCLE".equals(batchType)
                                ? r.getCategory() == SortCategory.ECO_RECYCLE
                                : r.getCategory() == SortCategory.DIRECT_DONATE
                                   || r.getCategory() == SortCategory.NEED_CLEAN
                ).orElse(false))
                .map(vm::order).toList();
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

    /** 公益机构拒收 */
    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody Map<String, String> dto) {
        cu.require(Role.ORG, Role.COMMUNITY, Role.ADMIN);
        return vm.batch(svc.rejectBatch(id, cu.get(), dto.getOrDefault("reason", "未说明")));
    }

    /** 再生处理登记（再生处理量、公示说明） */
    @PostMapping("/{id}/recycle-json")
    public Map<String, Object> recycle(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        cu.require(Role.SORTER, Role.ADMIN);
        return vm.batch(svc.recycleBatch(id, dto));
    }
}
