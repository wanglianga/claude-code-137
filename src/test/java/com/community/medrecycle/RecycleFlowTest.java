package com.community.medrecycle;

import com.community.medrecycle.auth.AuthUser;
import com.community.medrecycle.common.BizException;
import com.community.medrecycle.domain.*;
import com.community.medrecycle.dto.AdminDtos;
import com.community.medrecycle.dto.AppointmentDtos;
import com.community.medrecycle.dto.AuthDtos;
import com.community.medrecycle.dto.CollectorDtos;
import com.community.medrecycle.repo.*;
import com.community.medrecycle.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端主流程：预约 → 开批次 → 扫码登记 → 分类称重（低风险即时积分 / 高风险复核）
 * → 复核发放 → 入柜 → 异常阻塞交接 → 争议复称调积分 → 双人交接 → 报告与追溯。
 */
@SpringBootTest
@ActiveProfiles("test")
class RecycleFlowTest {

    @Autowired
    AuthService authService;
    @Autowired
    AppointmentService appointmentService;
    @Autowired
    BatchService batchService;
    @Autowired
    ReviewService reviewService;
    @Autowired
    PointsService pointsService;
    @Autowired
    HandoverService handoverService;
    @Autowired
    ExceptionService exceptionService;
    @Autowired
    DashboardService dashboardService;
    @Autowired
    TraceService traceService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    StorageCabinetRepository cabinetRepository;
    @Autowired
    SealedBagRepository sealedBagRepository;

    private AuthUser operator(String username) {
        User u = userRepository.findByUsername(username).orElseThrow();
        return new AuthUser(u.getId(), u.getUsername(), u.getName(), u.getRole(), u.getCommunityPointId());
    }

    @Test
    void fullRecycleFlow() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long pointId = 1L; // 阳光社区回收点（DataInitializer）
        AuthUser collector = operator("collector01");
        AuthUser admin = operator("admin");

        // 1. 居民注册并预约：一单普通药，一单含处方药
        AuthDtos.UserView resident = authService.register(new AuthDtos.RegisterRequest(
                "flow_" + suffix, "secret123", "流程居民", "13800000000"));
        Long residentId = resident.id();
        AppointmentDtos.View apptNormal = appointmentService.create(residentId,
                new AppointmentDtos.CreateRequest("感冒灵", 2, pointId, null,
                        false, false, false, false, null));
        AppointmentDtos.View apptRx = appointmentService.create(residentId,
                new AppointmentDtos.CreateRequest("安定片", 1, pointId, null,
                        true, false, false, true, "药盒上有处方单"));

        // 2. 回收员开批次并扫码登记
        RecycleBatch batch = batchService.openBatch(collector,
                new CollectorDtos.OpenBatchRequest(pointId, null));
        batchService.checkin(batch.getId(),
                new CollectorDtos.CheckinRequest(apptNormal.appointmentNo()));
        batchService.checkin(batch.getId(),
                new CollectorDtos.CheckinRequest(apptRx.appointmentNo()));

        // 3. 低风险称重：积分即时到账（TABLET 0.1*500=50）
        CollectorDtos.WeighResult normal = batchService.weigh(batch.getId(), collector,
                new CollectorDtos.WeighRequest(apptNormal.appointmentNo(), DrugCategory.TABLET,
                        500, "BAG-" + suffix + "-1", List.of("pkg1.jpg", "seal1.jpg")));
        assertFalse(normal.highRisk());
        assertEquals(50, normal.pointsCredited());
        assertEquals(50, pointsService.accountOf(residentId).balance());

        // 4. 高风险称重（精神类+含处方药）：进入人工复核，暂不发积分
        CollectorDtos.WeighResult risky = batchService.weigh(batch.getId(), collector,
                new CollectorDtos.WeighRequest(apptRx.appointmentNo(), DrugCategory.PSYCHOTROPIC,
                        100, "BAG-" + suffix + "-2", List.of("pkg2.jpg")));
        assertTrue(risky.highRisk());
        assertNull(risky.pointsCredited());
        assertEquals(50, pointsService.accountOf(residentId).balance(), "复核前不应发放积分");

        // 5. 人工复核通过：发放积分（PSYCHOTROPIC 0.5*100=50）
        reviewService.decide(risky.reviewTicketId(), admin, true, "现场核验无误");
        assertEquals(100, pointsService.accountOf(residentId).balance());

        // 6. 入暂存柜
        StorageCabinet cabinet = cabinetRepository.findByCode("YG-A01").orElseThrow();
        batchService.store(batch.getId(), new CollectorDtos.StoreRequest(normal.sealBagNo(), cabinet.getId()));
        batchService.store(batch.getId(), new CollectorDtos.StoreRequest(risky.sealBagNo(), cabinet.getId()));

        // 7. 上报称重争议异常：未解决时阻止创建交接
        SealedBag bag1 = sealedBagRepository.findBySealBagNo(normal.sealBagNo()).orElseThrow();
        ExceptionRecord dispute = batchService.reportException(batch.getId(), collector,
                new CollectorDtos.ExceptionRequest(ExceptionRecord.Type.WEIGHT_DISPUTE,
                        "居民对称重结果有异议", null, bag1.getId()));
        AdminDtos.HandoverCreateRequest createReq =
                new AdminDtos.HandoverCreateRequest(pointId, "绿源环保公司", List.of(batch.getId()));
        assertThrows(BizException.class, () -> handoverService.create(admin, createReq),
                "存在未解决异常时不允许交接");

        // 8. 复称解决争议：500g→520g，积分差额 +2
        exceptionService.resolve(dispute.getId(), admin,
                new AdminDtos.ResolveExceptionRequest("复称确认为 520g", 520));
        assertEquals(102, pointsService.accountOf(residentId).balance());

        // 9. 创建交接：含精神类袋 → 需双人交接
        Handover handover = handoverService.create(admin, createReq);
        assertTrue(handover.getDualRequired());
        assertEquals(2, handover.getBagCount());
        assertEquals(620, handover.getTotalWeightGrams());
        // 单人签收被拒绝
        assertThrows(BizException.class, () -> handoverService.complete(handover.getId(),
                new AdminDtos.HandoverCompleteRequest("赵环保", null, List.of("handover.jpg"))));
        // 双人签收成功
        handoverService.complete(handover.getId(),
                new AdminDtos.HandoverCompleteRequest("赵环保", "钱监督", List.of("handover.jpg")));

        // 10. 交接报告：去向、重量、签收人、积分发放结果
        Map<String, Object> report = handoverService.report(handover.getId());
        assertEquals("绿源环保公司", report.get("destination"));
        assertEquals(620, report.get("totalWeightGrams"));
        assertEquals("赵环保", report.get("receiverName"));
        assertEquals(102, report.get("totalPointsIssued"));

        // 11. 居民追溯：批次去向与本人积分
        Map<String, Object> trace = traceService.traceByBatchNo(batch.getBatchNo(), residentId);
        assertEquals("HANDED_OVER", trace.get("batchStatus").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bags = (List<Map<String, Object>>) trace.get("bags");
        assertEquals(2, bags.size());
        assertTrue(bags.stream().allMatch(b -> "绿源环保公司".equals(b.get("destination"))));

        // 12. 看板：交接后暂存清零；人为造一袋超期数据验证超期预警
        SealedBag overdueBag = sealedBagRepository.findBySealBagNo(normal.sealBagNo()).orElseThrow();
        overdueBag.setStatus(SealedBag.Status.STORED);
        overdueBag.setStoredAt(LocalDateTime.now().minusDays(20));
        overdueBag.setHandoverId(null);
        sealedBagRepository.save(overdueBag);
        AdminDtos.DashboardView dashboard = dashboardService.overview(pointId);
        assertTrue(dashboard.overdueBagCount() >= 1);
        assertTrue(dashboard.overdueBags().stream()
                .anyMatch(b -> b.sealBagNo().equals(normal.sealBagNo())));
    }

    @Test
    void batchWeighAndCabinetCapacityWarning() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long pointId = 1L;
        AuthUser collector = operator("collector01");
        AuthUser admin = operator("admin");

        AuthDtos.UserView resident = authService.register(new AuthDtos.RegisterRequest(
                "bulk_" + suffix, "secret123", "批量居民", null));

        // 活动日大批量：一次提交多条称重
        RecycleBatch batch = batchService.openBatch(collector,
                new CollectorDtos.OpenBatchRequest(pointId, null));
        List<AppointmentDtos.View> appts = new java.util.ArrayList<>();
        List<CollectorDtos.WeighRequest> items = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AppointmentDtos.View a = appointmentService.create(resident.id(),
                    new AppointmentDtos.CreateRequest("批量药" + i, 1, pointId, null,
                            false, false, false, false, null));
            appts.add(a);
            items.add(new CollectorDtos.WeighRequest(a.appointmentNo(), DrugCategory.EXTERNAL,
                    100, "BLK-" + suffix + "-" + i, null));
        }
        CollectorDtos.BatchWeighResult result = batchService.weighBatch(batch.getId(), collector,
                new CollectorDtos.BatchWeighRequest(items));
        assertEquals(5, result.succeeded());
        assertEquals(0, result.failed());
        // EXTERNAL 0.1*100*5 = 50 分
        assertEquals(50, pointsService.accountOf(resident.id()).balance());

        // 容量预警：建一个容量为 1 的柜子，入 1 袋后占用 100% 触发预警
        StorageCabinet tiny = new StorageCabinet();
        tiny.setCode("TINY-" + suffix);
        tiny.setCapacityBags(1);
        tiny.setCommunityPointId(pointId);
        cabinetRepository.save(tiny);
        CollectorDtos.StoreResult stored = batchService.store(batch.getId(),
                new CollectorDtos.StoreRequest("BLK-" + suffix + "-0", tiny.getId()));
        assertTrue(stored.capacityWarning(), "占用达到预警线应提示容量预警");

        // 柜满后再入应失败
        assertThrows(BizException.class, () -> batchService.store(batch.getId(),
                new CollectorDtos.StoreRequest("BLK-" + suffix + "-1", tiny.getId())));

        // 看板能看到该柜预警
        AdminDtos.DashboardView dashboard = dashboardService.overview(pointId);
        assertTrue(dashboard.cabinets().stream()
                .anyMatch(c -> c.code().equals("TINY-" + suffix) && c.capacityWarning()));
    }
}
