package com.clothing.recycle.seed;

import com.clothing.recycle.model.*;
import com.clothing.recycle.repo.*;
import com.clothing.recycle.service.RecycleService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据：覆盖完整链路与六类争议。
 * 账号统一密码 123456。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepo userRepo;
    private final ProductRepo productRepo;
    private final PartnerRepo partnerRepo;
    private final BatchRepo batchRepo;
    private final PickupRepo pickupRepo;
    private final RecycleService svc;
    private final PasswordEncoder encoder;
    private final PhotoGenerator photos;

    public DataSeeder(UserRepo userRepo, ProductRepo productRepo, PartnerRepo partnerRepo,
                      BatchRepo batchRepo, PickupRepo pickupRepo, RecycleService svc,
                      PasswordEncoder encoder, PhotoGenerator photos) {
        this.userRepo = userRepo;
        this.productRepo = productRepo;
        this.partnerRepo = partnerRepo;
        this.batchRepo = batchRepo;
        this.pickupRepo = pickupRepo;
        this.svc = svc;
        this.encoder = encoder;
        this.photos = photos;
    }

    private void setDonationPhoto(Long batchId, String path) {
        batchRepo.findById(batchId).ifPresent(b -> {
            b.setDonationPhotoPath(path);
            batchRepo.save(b);
        });
    }

    private User u(String username, String name, Role role, String community, String org, String phone) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode("123456"));
        user.setDisplayName(name);
        user.setRole(role);
        user.setCommunityName(community);
        user.setOrganizationName(org);
        user.setPhone(phone);
        return userRepo.save(user);
    }

    private Map<String, Object> orderDto(String community, int count, String cats, boolean washed,
                                         boolean bedding, String addr, String slot,
                                         boolean donate, Long partnerId) {
        Map<String, Object> m = new HashMap<>();
        m.put("communityName", community);
        m.put("itemCount", count);
        m.put("categories", cats);
        m.put("washed", washed);
        m.put("hasShoesBagsBedding", bedding);
        m.put("address", addr);
        m.put("timeSlot", slot);
        m.put("donateWanted", donate);
        if (partnerId != null) m.put("partnerId", partnerId);
        return m;
    }

    private Map<String, Object> pickup(BigDecimal w, String pre, boolean late, String note) {
        Map<String, Object> m = new HashMap<>();
        m.put("weightKg", w);
        m.put("preCategories", pre);
        m.put("arrivedLate", late);
        m.put("note", note);
        return m;
    }

    private Map<String, Object> sort(String cat, BigDecimal w, String damage, String dest,
                                     boolean privacy, String action, String privacyNote) {
        Map<String, Object> m = new HashMap<>();
        m.put("category", cat);
        m.put("weightKg", w);
        m.put("damageReason", damage);
        m.put("destination", dest);
        m.put("privacyRisk", privacy);
        if (action != null) m.put("privacyAction", action);
        m.put("privacyNote", privacyNote);
        return m;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepo.count() > 0) return;

        // ---------- 演示照片 ----------
        String pA = photos.generate("pickup-a.jpg", 0x6AA9A0);
        String pB = photos.generate("pickup-b.jpg", 0x8E9AAF);
        String pC = photos.generate("pickup-c.jpg", 0x7FB069);
        String pD = photos.generate("pickup-d.jpg", 0xB5838E);
        String pE = photos.generate("pickup-e.jpg", 0x9A8C98);
        String pF = photos.generate("pickup-f.jpg", 0xC9A227);
        String pK = photos.generate("pickup-k.jpg", 0x6D9DC5);
        String pL = photos.generate("pickup-l.jpg", 0x80B918);
        String sA = photos.generate("sort-a.jpg", 0x5FA8D3);
        String sD = photos.generate("sort-d.jpg", 0x4C9F70);
        String privacyC = photos.generate("privacy-c.jpg", 0x2A9D8F);
        String pM = photos.generate("pickup-m.jpg", 0xB5657D);
        String pN = photos.generate("pickup-n.jpg", 0x7D8CA6);
        String donate1 = photos.generate("donation-b1.jpg", 0xE07A5F);
        String donate2 = photos.generate("donation-b2.jpg", 0xF2A541);
        String recycle1 = photos.generate("recycle-b3.jpg", 0x3D5A80);
        String sign1 = photos.generate("sign-b1.jpg", 0xEE6C4D);
        String sign2 = photos.generate("sign-b2.jpg", 0x2EC4B6);

        // ---------- 用户（密码均为 123456） ----------
        User resident1 = u("resident", "李晓梅", Role.RESIDENT, "阳光花园小区", null, "13800000001");
        User resident2 = u("resident2", "王建国", Role.RESIDENT, "幸福里小区", null, "13800000002");
        User resident3 = u("resident3", "赵敏", Role.RESIDENT, "阳光花园小区", null, "13800000003");
        User collector1 = u("collector", "周强", Role.COLLECTOR, "阳光花园小区", null, "13900000001");
        User collector2 = u("collector2", "陈涛", Role.COLLECTOR, "幸福里小区", null, "13900000002");
        User sorter1 = u("sorter", "刘芳(分拣主管)", Role.SORTER, null, null, "13700000001");
        User community1 = u("community", "孙丽(阳光花园社区)", Role.COMMUNITY, "阳光花园小区", null, "13600000001");
        User org1 = u("org", "林主任(暖阳公益)", Role.ORG, null, "暖阳公益服务中心", "13500000001");
        User org2 = u("org2", "韩老师(童心助学)", Role.ORG, null, "童心助学公益中心", "13500000002");
        User finance1 = u("finance", "吴会计(积分财务)", Role.FINANCE, null, null, "13400000001");
        u("admin", "平台管理员", Role.ADMIN, null, null, null);

        // ---------- 兑换商品 ----------
        product(product("环保购物袋", "再生布袋，可折叠", 200, 50, "🛍️"));
        product(product("再生纸笔记本", "回收纸浆压制", 150, 80, "📓"));
        product(product("绿植种子包", "四季易活花草组合", 80, 120, "🌱"));
        product(product("竹纤维毛巾", "天然抗菌两条装", 300, 40, "🧻"));
        product(product("公交地铁充值券", "绿色出行 10 元券", 500, 30, "🚇"));

        // ---------- 企业合作 / 学校活动 ----------
        Partner enterprise = new Partner();
        enterprise.setName("华远地产集团");
        enterprise.setType(PartnerType.ENTERPRISE);
        enterprise.setProjectName("暖冬企业公益周");
        enterprise.setContactName("马总");
        enterprise.setContactPhone("010-88880001");
        enterprise.setStartDate(LocalDate.of(2026, 9, 1));
        enterprise.setEndDate(LocalDate.of(2026, 9, 30));
        enterprise.setTargetKg(new BigDecimal("500"));
        enterprise.setDescription("企业员工旧衣集中募集，统一再生处理与公益捐赠");
        partnerRepo.save(enterprise);

        Partner school = new Partner();
        school.setName("阳光实验小学");
        school.setType(PartnerType.SCHOOL);
        school.setProjectName("童心捐衣季");
        school.setContactName("赵老师");
        school.setContactPhone("010-88880002");
        school.setStartDate(LocalDate.of(2026, 9, 5));
        school.setEndDate(LocalDate.of(2026, 9, 25));
        school.setTargetKg(new BigDecimal("300"));
        school.setDescription("学生冬季外套募集，捐赠参与学生获公益积分奖励");
        partnerRepo.save(school);

        // ================= 回收单全链路 =================

        // A：可直接捐赠（学校活动）→ 批次B1 → 签收 → 定向发放
        RecycleOrder a = svc.createOrder(resident1, orderDto("阳光花园小区", 18, "上衣,外套",
                true, false, "阳光花园 3 栋 502", "2026-09-12 09:00-11:00", true, school.getId()));
        svc.assignOrder(a.getId(), collector1);
        svc.recordPickup(a.getId(), collector1, pickup(new BigDecimal("4.20"), "可直接捐赠", false,
                "衣物整洁，打包规范"), pA);
        svc.residentConfirm(a.getId(), resident1, "DONATION");
        svc.sortReview(a.getId(), sorter1, sort("DIRECT_DONATE", new BigDecimal("4.15"), null,
                "发往暖阳公益-打工子弟冬季温暖包", false, null, null), sA);

        // C：含校服 → 脱敏后捐赠 → 同入 B1
        RecycleOrder c = svc.createOrder(resident3, orderDto("阳光花园小区", 8, "上衣,校服",
                true, false, "阳光花园 12 栋 1803", "2026-09-12 14:00-16:00", true, school.getId()));
        svc.assignOrder(c.getId(), collector1);
        svc.recordPickup(c.getId(), collector1, pickup(new BigDecimal("2.30"), "可直接捐赠", false,
                "其中两件校服需脱敏"), pC);
        svc.residentConfirm(c.getId(), resident3, "MIXED");
        svc.sortReview(c.getId(), sorter1, sort("DIRECT_DONATE", new BigDecimal("2.28"), null,
                "脱敏后发往暖阳公益-打工子弟冬季温暖包", true, "DESENSITIZED",
                "已拆除校徽、胸牌与姓名标签，脱敏登记编号 S-0912-03"), sA, privacyC);

        Batch b1 = svc.createBatch(sorter1, Map.of(
                "batchType", "DONATION",
                "orderIds", List.of(a.getId(), c.getId()),
                "organizationId", org1.getId().toString(),
                "projectName", "打工子弟冬季温暖包",
                "partnerId", school.getId().toString(),
                "publicNote", "本批含阳光实验小学童心捐衣季衣物，校徽与个人标签已全部拆除"));
        svc.shipBatch(b1.getId());
        svc.signBatch(b1.getId(), org1, Map.of(
                "receiverName", "林主任",
                "signNote", "清点 26 件，与装箱单一致，已入库待发放"), sign1);
        // 捐赠照片单独上传（分拣打包/装车留痕）
        setDonationPhoto(b1.getId(), donate1);

        // 低收入家庭定向领取
        AidFamily fam1 = svc.registerAid(community1, Map.of(
                "maskedName", "张*",
                "maskedPhone", "138****2103",
                "communityName", "阳光花园小区",
                "familySize", 4,
                "needNote", "低保家庭，两名儿童冬季缺外套（社区已核实）"));
        svc.deliverAid(fam1.getId(), b1.getId(), community1, "凭凭证 AID 发放冬装 6 件，家属签字确认");

        // B：需消毒整理（含被褥、未清洗、上门迟到）→ 批次B2 签收
        RecycleOrder b = svc.createOrder(resident2, orderDto("幸福里小区", 12, "被褥,外套",
                false, true, "幸福里 7 栋 201", "2026-09-11 09:00-11:00", false, null));
        svc.assignOrder(b.getId(), collector2);
        svc.recordPickup(b.getId(), collector2, pickup(new BigDecimal("6.50"), "需消毒整理", true,
                "比约定时间晚约 40 分钟，被褥需消毒"), pB);
        svc.residentConfirm(b.getId(), resident2, "POINTS");
        svc.sortReview(b.getId(), sorter1, sort("NEED_CLEAN", new BigDecimal("6.40"),
                "被褥有轻微异味", "消毒清洗整理后转童心助学", false, null, null), null);

        Batch b2 = svc.createBatch(sorter1, Map.of(
                "batchType", "DONATION",
                "orderIds", List.of(b.getId()),
                "organizationId", org2.getId().toString(),
                "projectName", "乡村寄宿生被褥补充计划",
                "publicNote", "已完成高温消毒与重新包装"));
        setDonationPhoto(b2.getId(), donate2);
        svc.shipBatch(b2.getId());
        svc.signBatch(b2.getId(), org2, Map.of(
                "receiverName", "韩老师",
                "signNote", "被褥 8 床、外套 4 件验收合格"), sign2);

        // D：环保再生（企业公益周）→ 批次B3 再生完成
        RecycleOrder d = svc.createOrder(resident1, orderDto("阳光花园小区", 25, "裤装,外套,旧衣",
                false, false, "阳光花园 3 栋 502", "2026-09-10 14:00-16:00", false, enterprise.getId()));
        svc.assignOrder(d.getId(), collector2);
        svc.recordPickup(d.getId(), collector2, pickup(new BigDecimal("9.80"), "环保再生", false,
                "磨损严重，建议纤维化再生"), pD);
        svc.residentConfirm(d.getId(), resident1, "POINTS");
        svc.sortReview(d.getId(), sorter1, sort("ECO_RECYCLE", new BigDecimal("9.70"),
                "磨损起球严重，不适合二次穿着", "绿纤环保再生厂-开松纤维化", false, null, null), sD);
        Batch b3 = svc.createBatch(sorter1, Map.of(
                "batchType", "RECYCLE",
                "orderIds", List.of(d.getId()),
                "recyclerName", "绿纤环保再生资源厂",
                "partnerId", enterprise.getId().toString(),
                "publicNote", "开松纤维用于汽车隔音棉与保温材料"));
        setDonationPhoto(b3.getId(), recycle1);
        svc.shipBatch(b3.getId());
        svc.recycleBatch(b3.getId(), Map.of(
                "recycledWeightKg", new BigDecimal("9.50"),
                "publicNote", "9.50kg 完成开松纤维化，产物：保温棉原料，处置证明编号 RF-20260913-07"));

        // E：霉变不可回收 → 拒收，居民误判投诉处理中
        RecycleOrder e = svc.createOrder(resident2, orderDto("幸福里小区", 5, "上衣,鞋",
                false, true, "幸福里 9 栋 603", "2026-09-13 09:00-11:00", true, null));
        svc.assignOrder(e.getId(), collector1);
        svc.recordPickup(e.getId(), collector1, pickup(new BigDecimal("3.10"), "待复核", false,
                "现场有霉味"), pE);
        svc.residentConfirm(e.getId(), resident2, "DONATION");
        svc.sortReview(e.getId(), sorter1, sort("NON_RECYCLABLE", new BigDecimal("3.10"),
                "严重霉变且鞋底断裂，存在卫生风险", "不可回收，按其他垃圾无害化处理", false, null, null), null);

        // F：工作服含员工信息牌 → 隐私拒收（特殊处理）
        RecycleOrder f = svc.createOrder(resident3, orderDto("阳光花园小区", 10, "工作服,外套",
                true, false, "阳光花园 12 栋 1803", "2026-09-13 14:00-16:00", false, null));
        svc.assignOrder(f.getId(), collector2);
        svc.recordPickup(f.getId(), collector2, pickup(new BigDecimal("3.60"), "特殊处理", true,
                "含前单位工作服"), pF);
        svc.residentConfirm(f.getId(), resident3, "POINTS");
        svc.sortReview(f.getId(), sorter1, sort("SPECIAL", new BigDecimal("3.55"),
                "工作服反光条材质不可再生", "隐私衣物不进入公益流转", true, "REJECTED",
                "工作服绣有姓名且夹带员工信息牌，依隐私保护规定单独拒收并登记销毁"), null);

        // K：发运后被公益机构拒收 → 退回重新集货
        RecycleOrder k = svc.createOrder(resident3, orderDto("阳光花园小区", 6, "童装,上衣",
                true, false, "阳光花园 12 栋 1803", "2026-09-14 09:00-11:00", true, null));
        svc.assignOrder(k.getId(), collector1);
        svc.recordPickup(k.getId(), collector1, pickup(new BigDecimal("1.80"), "可直接捐赠", false, ""), pK);
        svc.residentConfirm(k.getId(), resident3, "DONATION");
        svc.sortReview(k.getId(), sorter1, sort("DIRECT_DONATE", new BigDecimal("1.78"), null,
                "原计划童心助学", false, null, null), null);
        Batch b4 = svc.createBatch(sorter1, Map.of(
                "batchType", "DONATION",
                "orderIds", List.of(k.getId()),
                "organizationId", org2.getId().toString(),
                "projectName", "秋季童装包"));
        svc.shipBatch(b4.getId());
        svc.rejectBatch(b4.getId(), org2, "本季已募足童装，且其中 2 件尺码与受助年龄不匹配，整批退回");

        // M / N：已分拣待入批
        RecycleOrder m = svc.createOrder(resident1, orderDto("阳光花园小区", 14, "裤装,上衣",
                false, false, "阳光花园 3 栋 502", "2026-09-15 09:00-11:00", true, null));
        svc.assignOrder(m.getId(), collector1);
        svc.recordPickup(m.getId(), collector1, pickup(new BigDecimal("5.20"), "需消毒整理", false, ""), pM);
        svc.residentConfirm(m.getId(), resident1, "DONATION");
        svc.sortReview(m.getId(), sorter1, sort("NEED_CLEAN", new BigDecimal("5.10"),
                "部分需清洗", "待消毒后并入下一捐赠批", false, null, null), null);

        RecycleOrder n = svc.createOrder(resident3, orderDto("阳光花园小区", 20, "旧衣,裤装",
                false, false, "阳光花园 12 栋 1803", "2026-09-15 14:00-16:00", false, null));
        svc.assignOrder(n.getId(), collector2);
        svc.recordPickup(n.getId(), collector2, pickup(new BigDecimal("7.40"), "环保再生", false, ""), pN);
        svc.residentConfirm(n.getId(), resident3, "POINTS");
        svc.sortReview(n.getId(), sorter1, sort("ECO_RECYCLE", new BigDecimal("7.30"), null,
                "待并入下一再生批", false, null, null), null);

        // H：已派单待上门；I：待派单
        RecycleOrder h = svc.createOrder(resident2, orderDto("幸福里小区", 9, "上衣,鞋包",
                true, true, "幸福里 7 栋 201", "2026-09-17 09:00-11:00", true, null));
        svc.assignOrder(h.getId(), collector1);
        svc.createOrder(resident3, orderDto("阳光花园小区", 7, "外套,被褥",
                false, true, "阳光花园 12 栋 1803", "2026-09-18 14:00-16:00", true, school.getId()));

        // L：已上门、居民已确认积分，但积分因系统延迟未入账 → 投诉（财务补发场景）
        RecycleOrder l = svc.createOrder(resident2, orderDto("幸福里小区", 11, "外套,裤装",
                true, false, "幸福里 9 栋 603", "2026-09-15 19:00-21:00", false, null));
        svc.assignOrder(l.getId(), collector2);
        svc.recordPickup(l.getId(), collector2, pickup(new BigDecimal("5.00"), "需消毒整理", false, ""), pL);
        PickupRecord pl = pickupRepo.findByOrder(l).orElseThrow();
        pl.setConfirmOption("POINTS");
        pl.setResidentConfirmed(true);
        pickupRepo.save(pl); // 直接落库确认状态，模拟积分尚未入账

        // ================= 六类投诉 =================
        // 1. 上门迟到 → 回收员致歉 → 社区约谈 → 财务补偿积分，已解决
        Complaint c1 = svc.openComplaint(b.getId(), resident2, ComplaintType.LATE_VISIT,
                "预约 09:00-11:00 时段，实际 11:40 才到，老人在家等了一上午");
        svc.addComplaintEvent(c1.getId(), collector2,
                "非常抱歉，当天幸福里电梯检修需爬楼，前一单延误导致连锁迟到，已向居民电话致歉");
        svc.addComplaintEvent(c1.getId(), community1,
                "社区已约谈回收班组，要求提前一天电话确认并预留缓冲时间");
        svc.resolveComplaint(c1.getId(), finance1,
                "迟到情况属实，按服务规范补偿 50 积分，后续加强排班", 50);

        // 2. 误判不可捐赠 → 分拣复核说明，社区安排二次核验，处理中
        Complaint c2 = svc.openComplaint(e.getId(), resident2, ComplaintType.MISJUDGED_DONATE,
                "其中两件外套只穿过几次，不认可整批被判不可捐赠");
        svc.addComplaintEvent(c2.getId(), sorter1,
                "分拣复核照片显示霉变主要集中在鞋和装箱内侧，两件外套确实受霉味污染；可安排二次核验确认是否单独挽救");
        svc.addComplaintEvent(c2.getId(), community1,
                "已协调分拣中心本周对两件外套重新开包核验，回收员陪同居民现场确认");

        // 3. 公益机构拒收 → 机构说明 → 改配其他项目，已解决
        Complaint c3 = svc.openComplaint(k.getId(), resident3, ComplaintType.ORG_REJECTED,
                "公示里看到捐的童装被机构拒收，没人通知我，想知道衣服最后去哪");
        svc.addComplaintEvent(c3.getId(), org2,
                "拒收仅因尺码与本季受助儿童不匹配，非衣物质量问题，已随单注明并整批退回");
        svc.addComplaintEvent(c3.getId(), community1,
                "已协调改配暖阳公益低年级尺码项目，重新消毒后并入下一批，全程在公示端更新照片与签收");
        svc.resolveComplaint(c3.getId(), community1,
                "改配完成后将短信告知居民批次号，可在公示端追踪去向", null);

        // 4. 积分未到账 → 待财务补发，处理中
        Complaint c4 = svc.openComplaint(l.getId(), resident2, ComplaintType.POINTS_MISSING,
                "9 月 15 日晚已确认按 5kg 折算积分，至今账户没有到账记录");
        svc.addComplaintEvent(c4.getId(), collector2,
                "上门称重 5.00kg 与居民签字单一致，确认选项为积分");

        // 5. 质疑称重 → 分拣对比重量在误差内，已解决
        Complaint c5 = svc.openComplaint(d.getId(), resident1, ComplaintType.WEIGHT_DISPUTE,
                "家里旧衣称重约 10kg，回收单写 9.80kg、分拣又变成 9.70kg，要求说明");
        svc.addComplaintEvent(c5.getId(), sorter1,
                "上门秤 9.80kg、分拣复核 9.70kg，差异 0.10kg 为运输途中水分散失与秤具正常误差（阈值 1%），两张称重照片已随单存档");
        svc.resolveComplaint(c5.getId(), community1,
                "两次称重均有照片留痕，误差在允许范围内，向居民公示对比照片后认可", null);

        // 6. 去向不透明 → 待社区更新公示，处理中
        Complaint c6 = svc.openComplaint(m.getId(), resident1, ComplaintType.OPAQUE_DESTINATION,
                "衣服收走五天了，公示端查不到任何去向，也没有批次信息");
        svc.addComplaintEvent(c6.getId(), sorter1,
                "该批在消毒整理环节，尚未集货发运，按规则发运后才进入公示，已提前向居民说明进度");

        // ---------- 财务对其他居民的历史调整（演示流水） ----------
        svc.financeAdjust(finance1, resident3.getId(), 20, "童心捐衣季社区好评奖励");
    }

    private void product(Product p) {
        productRepo.save(p);
    }

    private Product product(String name, String desc, int cost, int stock, String icon) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(desc);
        p.setPointsCost(cost);
        p.setStock(stock);
        p.setIcon(icon);
        return p;
    }
}
