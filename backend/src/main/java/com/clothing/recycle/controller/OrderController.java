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

/** 回收单：预约、派单、上门、居民确认、分拣复核（按角色开放） */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepo orderRepo;
    private final UserRepo userRepo;
    private final RecycleService svc;
    private final ViewMapper vm;
    private final CurrentUser cu;
    private final FileStorage storage;

    public OrderController(OrderRepo orderRepo, UserRepo userRepo, RecycleService svc,
                           ViewMapper vm, CurrentUser cu, FileStorage storage) {
        this.orderRepo = orderRepo;
        this.userRepo = userRepo;
        this.svc = svc;
        this.vm = vm;
        this.cu = cu;
        this.storage = storage;
    }

    /** 我的回收单（居民看本人；回收员看本人；其他角色看全部） */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String community) {
        User me = cu.get();
        List<RecycleOrder> all;
        if (me.getRole() == Role.RESIDENT) {
            all = orderRepo.findByResidentOrderByCreatedAtDesc(me);
        } else if (me.getRole() == Role.COLLECTOR) {
            all = orderRepo.findByCollectorOrderByCreatedAtDesc(me);
        } else if (community != null && !community.isBlank()) {
            all = orderRepo.findByCommunityNameOrderByCreatedAtDesc(community);
        } else {
            all = orderRepo.findAllByOrderByCreatedAtDesc();
        }
        var stream = all.stream();
        if (status != null && !status.isBlank()) {
            stream = stream.filter(o -> o.getStatus().name().equals(status));
        }
        return stream.map(vm::order).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        RecycleOrder o = svc.mustOrder(id);
        User me = cu.get();
        // 居民只能看本人的单
        if (me.getRole() == Role.RESIDENT && !o.getResident().getId().equals(me.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "无权查看该回收单");
        }
        return vm.order(o);
    }

    /** 居民提交预约 */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> dto) {
        cu.require(Role.RESIDENT);
        return vm.order(svc.createOrder(cu.get(), dto));
    }

    /** 待派单列表（回收员/社区/管理员） */
    @GetMapping("/pending")
    public List<Map<String, Object>> pending() {
        cu.require(Role.COLLECTOR, Role.COMMUNITY, Role.ADMIN, Role.SORTER);
        return orderRepo.findByStatusOrderByCreatedAtAsc(OrderStatus.PENDING)
                .stream().map(vm::order).toList();
    }

    /** 回收员抢单/社区派单 */
    @PostMapping("/{id}/assign")
    public Map<String, Object> assign(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, Object> dto) {
        cu.require(Role.COLLECTOR, Role.COMMUNITY, Role.ADMIN);
        User collector = cu.get();
        if (dto != null && dto.get("collectorId") != null && cu.hasRole(Role.COMMUNITY, Role.ADMIN)) {
            collector = svc.mustUser(Long.parseLong(dto.get("collectorId").toString()));
        }
        if (collector.getRole() != Role.COLLECTOR) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "被指派人不是回收员");
        }
        return vm.order(svc.assignOrder(id, collector));
    }

    /** 回收员上门登记：称重 + 照片 + 初步分类 */
    @PostMapping(value = "/{id}/pickup", consumes = "multipart/form-data")
    public Map<String, Object> pickup(@PathVariable Long id,
                                      @RequestParam Map<String, String> form,
                                      @RequestParam(required = false) MultipartFile photo)
            throws Exception {
        cu.require(Role.COLLECTOR);
        String path = storage.save(photo);
        svc.recordPickup(id, cu.get(), new java.util.HashMap<>(form), path);
        return vm.order(svc.mustOrder(id));
    }

    @PostMapping("/{id}/pickup-json")
    public Map<String, Object> pickupJson(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        cu.require(Role.COLLECTOR);
        PickupRecord p = svc.recordPickup(id, cu.get(), dto, dto.get("photoPath") == null ? null : dto.get("photoPath").toString());
        return vm.order(svc.mustOrder(id));
    }

    /** 居民确认：POINTS / DONATION / MIXED */
    @PostMapping("/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable Long id, @RequestBody Map<String, String> dto) {
        cu.require(Role.RESIDENT);
        svc.residentConfirm(id, cu.get(), dto.get("option"));
        return vm.order(svc.mustOrder(id));
    }

    /** 分拣中心复核（multipart：字段 + 照片） */
    @PostMapping(value = "/{id}/sort", consumes = "multipart/form-data")
    public Map<String, Object> sort(@PathVariable Long id,
                                    @RequestParam Map<String, String> form,
                                    @RequestParam(required = false) MultipartFile photo) throws Exception {
        cu.require(Role.SORTER);
        String path = storage.save(photo);
        svc.sortReview(id, cu.get(), new java.util.HashMap<>(form), path);
        return vm.order(svc.mustOrder(id));
    }

    /** 分拣中心复核（JSON，便于无文件场景） */
    @PostMapping("/{id}/sort-json")
    public Map<String, Object> sortJson(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        cu.require(Role.SORTER);
        svc.sortReview(id, cu.get(), dto, dto.get("photoPath") == null ? null : dto.get("photoPath").toString());
        return vm.order(svc.mustOrder(id));
    }

    /** 回收员名单（派单下拉） */
    @GetMapping("/collectors")
    public List<Map<String, Object>> collectors() {
        return userRepo.findByRole(Role.COLLECTOR).stream()
                .map(u -> Map.<String, Object>of("id", u.getId(), "displayName", u.getDisplayName(),
                        "communityName", u.getCommunityName() == null ? "" : u.getCommunityName()))
                .toList();
    }
}
