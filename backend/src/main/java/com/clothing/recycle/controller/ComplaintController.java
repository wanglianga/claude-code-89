package com.clothing.recycle.controller;

import com.clothing.recycle.config.ViewMapper;
import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.ComplaintRepo;
import com.clothing.recycle.security.CurrentUser;
import com.clothing.recycle.service.RecycleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 投诉协同：六类争议在同一回收单上下文中跨角色处理 */
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintRepo complaintRepo;
    private final RecycleService svc;
    private final ViewMapper vm;
    private final CurrentUser cu;

    public ComplaintController(ComplaintRepo complaintRepo, RecycleService svc, ViewMapper vm, CurrentUser cu) {
        this.complaintRepo = complaintRepo;
        this.svc = svc;
        this.vm = vm;
        this.cu = cu;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String type,
                                          @RequestParam(required = false) String status) {
        User me = cu.get();
        var stream = (me.getRole() == Role.RESIDENT
                ? complaintRepo.findByResidentIdOrderByCreatedAtDesc(me.getId())
                : complaintRepo.findAllByOrderByCreatedAtDesc()).stream();
        if (type != null && !type.isBlank()) stream = stream.filter(c -> c.getType().name().equals(type));
        if (status != null && !status.isBlank()) stream = stream.filter(c -> c.getStatus().name().equals(status));
        return stream.map(vm::complaint).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Complaint c = svc.mustComplaint(id);
        User me = cu.get();
        if (me.getRole() == Role.RESIDENT && !c.getResident().getId().equals(me.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "无权查看该投诉单");
        }
        return vm.complaint(c);
    }

    /** 居民发起投诉 */
    @PostMapping
    public Map<String, Object> open(@RequestBody Map<String, Object> dto) {
        cu.require(Role.RESIDENT);
        Long orderId = Long.parseLong(dto.get("orderId").toString());
        ComplaintType type = ComplaintType.valueOf(dto.get("type").toString());
        String desc = dto.getOrDefault("description", "").toString();
        return vm.complaint(svc.openComplaint(orderId, cu.get(), type, desc));
    }

    /** 任一参与角色补充处理进展 */
    @PostMapping("/{id}/events")
    public Map<String, Object> event(@PathVariable Long id, @RequestBody Map<String, String> dto) {
        return vm.complaint(svc.addComplaintEvent(id, cu.get(), dto.getOrDefault("content", "")));
    }

    /** 牵头角色解决投诉，可由积分财务补发积分 */
    @PostMapping("/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        User me = cu.get();
        if (me.getRole() == Role.RESIDENT) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "居民不能自行结案");
        }
        Integer compensate = dto.get("compensatePoints") == null
                || dto.get("compensatePoints").toString().isBlank()
                ? null : Integer.parseInt(dto.get("compensatePoints").toString());
        // 积分补发仅财务可执行
        if (compensate != null && compensate > 0 && me.getRole() != Role.FINANCE && me.getRole() != Role.ADMIN) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "积分补发须由积分财务执行");
        }
        return vm.complaint(svc.resolveComplaint(id, me,
                dto.getOrDefault("resolutionNote", "").toString(), compensate));
    }
}
