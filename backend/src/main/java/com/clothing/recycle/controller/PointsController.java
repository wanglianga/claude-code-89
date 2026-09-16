package com.clothing.recycle.controller;

import com.clothing.recycle.config.ViewMapper;
import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import com.clothing.recycle.security.CurrentUser;
import com.clothing.recycle.service.RecycleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 积分流水、环保积分兑换、积分财务 */
@RestController
@RequestMapping("/api/points")
public class PointsController {

    private final PointsLedgerRepo ledgerRepo;
    private final ProductRepo productRepo;
    private final ExchangeRepo exchangeRepo;
    private final UserRepo userRepo;
    private final RecycleService svc;
    private final ViewMapper vm;
    private final CurrentUser cu;

    public PointsController(PointsLedgerRepo ledgerRepo, ProductRepo productRepo, ExchangeRepo exchangeRepo,
                            UserRepo userRepo, RecycleService svc, ViewMapper vm, CurrentUser cu) {
        this.ledgerRepo = ledgerRepo;
        this.productRepo = productRepo;
        this.exchangeRepo = exchangeRepo;
        this.userRepo = userRepo;
        this.svc = svc;
        this.vm = vm;
        this.cu = cu;
    }

    /** 积分流水：居民看本人，财务看全部（带 residentId 可指定居民） */
    @GetMapping("/ledger")
    public List<Map<String, Object>> ledger(@RequestParam(required = false) Long residentId) {
        User me = cu.get();
        if (me.getRole() == Role.FINANCE || me.getRole() == Role.ADMIN) {
            User target = residentId != null ? svc.mustUser(residentId) : null;
            if (target != null) {
                return ledgerRepo.findByResidentOrderByCreatedAtDesc(target).stream().map(vm::ledger).toList();
            }
            return ledgerRepo.findAll().stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .map(vm::ledger).toList();
        }
        return ledgerRepo.findByResidentOrderByCreatedAtDesc(me).stream().map(vm::ledger).toList();
    }

    @GetMapping("/balance")
    public Map<String, Object> balance() {
        return Map.of("balance", svc.pointsBalance(cu.get()));
    }

    /** 居民账户列表（财务补发选择） */
    @GetMapping("/residents")
    public List<Map<String, Object>> residents() {
        cu.require(Role.FINANCE, Role.ADMIN);
        return userRepo.findByRole(Role.RESIDENT).stream().map(vm::user).toList();
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products() {
        return productRepo.findAllByOrderByPointsCostAsc().stream().map(vm::product).toList();
    }

    @PostMapping("/exchange")
    public Map<String, Object> exchange(@RequestBody Map<String, Object> dto) {
        cu.require(Role.RESIDENT);
        ExchangeOrder eo = svc.exchange(cu.get(),
                Long.parseLong(dto.get("productId").toString()),
                Integer.parseInt(dto.getOrDefault("quantity", "1").toString()));
        return vm.exchange(eo);
    }

    @GetMapping("/exchanges")
    public List<Map<String, Object>> exchanges() {
        User me = cu.get();
        var list = (me.getRole() == Role.RESIDENT)
                ? exchangeRepo.findByResidentOrderByCreatedAtDesc(me)
                : exchangeRepo.findAllByOrderByCreatedAtDesc();
        return list.stream().map(vm::exchange).toList();
    }

    /** 财务确认兑换商品已发放 */
    @PostMapping("/exchanges/{id}/deliver")
    public Map<String, Object> deliver(@PathVariable Long id) {
        cu.require(Role.FINANCE, Role.ADMIN, Role.COMMUNITY);
        return vm.exchange(svc.deliverExchange(id, cu.get()));
    }

    /** 财务手工调整/补发积分 */
    @PostMapping("/adjust")
    public Map<String, Object> adjust(@RequestBody Map<String, Object> dto) {
        cu.require(Role.FINANCE, Role.ADMIN);
        return vm.ledger(svc.financeAdjust(cu.get(),
                Long.parseLong(dto.get("residentId").toString()),
                Integer.parseInt(dto.get("points").toString()),
                dto.getOrDefault("remark", "").toString()));
    }
}
