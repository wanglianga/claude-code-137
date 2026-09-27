package com.community.medrecycle.web;

import com.community.medrecycle.auth.AuthUser;
import com.community.medrecycle.auth.CurrentUser;
import com.community.medrecycle.auth.RequireRole;
import com.community.medrecycle.common.ApiResponse;
import com.community.medrecycle.common.BizException;
import com.community.medrecycle.domain.*;
import com.community.medrecycle.dto.AdminDtos;
import com.community.medrecycle.repo.*;
import com.community.medrecycle.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 社区管理员：看板、复核、环保交接、异常处理、基础数据维护 */
@RestController
@RequestMapping("/api/admin")
@RequireRole(User.Role.ADMIN)
@RequiredArgsConstructor
public class AdminController {

    private final DashboardService dashboardService;
    private final ReviewService reviewService;
    private final HandoverService handoverService;
    private final ExceptionService exceptionService;
    private final TraceService traceService;
    private final PointsRuleRepository pointsRuleRepository;
    private final StorageCabinetRepository cabinetRepository;
    private final ActivityDayRepository activityDayRepository;
    private final CommunityPointRepository communityPointRepository;

    // ---------- 看板 ----------

    @GetMapping("/dashboard")
    public ApiResponse<AdminDtos.DashboardView> dashboard(@RequestParam Long communityPointId) {
        return ApiResponse.ok(dashboardService.overview(communityPointId));
    }

    // ---------- 人工复核 ----------

    @GetMapping("/reviews")
    public ApiResponse<List<ReviewTicket>> reviews(
            @RequestParam(required = false) ReviewTicket.Status status) {
        return ApiResponse.ok(reviewService.list(status));
    }

    @PostMapping("/reviews/{id}/decision")
    public ApiResponse<ReviewTicket> decide(@CurrentUser AuthUser user, @PathVariable Long id,
                                            @Valid @RequestBody AdminDtos.ReviewDecisionRequest req) {
        return ApiResponse.ok(reviewService.decide(id, user, req.approved(), req.comment()));
    }

    // ---------- 环保交接 ----------

    @PostMapping("/handovers")
    public ApiResponse<Handover> createHandover(@CurrentUser AuthUser user,
                                                @Valid @RequestBody AdminDtos.HandoverCreateRequest req) {
        return ApiResponse.ok(handoverService.create(user, req));
    }

    @PostMapping("/handovers/{id}/complete")
    public ApiResponse<Handover> completeHandover(@PathVariable Long id,
                                                  @Valid @RequestBody AdminDtos.HandoverCompleteRequest req) {
        return ApiResponse.ok(handoverService.complete(id, req));
    }

    @GetMapping("/handovers")
    public ApiResponse<List<Handover>> handovers(@RequestParam Long communityPointId) {
        return ApiResponse.ok(handoverService.listByPoint(communityPointId));
    }

    /** 交接报告：去向、重量、照片、签收人、积分发放结果 */
    @GetMapping("/handovers/{id}/report")
    public ApiResponse<Map<String, Object>> handoverReport(@PathVariable Long id) {
        return ApiResponse.ok(handoverService.report(id));
    }

    // ---------- 异常处理 ----------

    @GetMapping("/exceptions")
    public ApiResponse<List<ExceptionRecord>> exceptions(
            @RequestParam(required = false) ExceptionRecord.Status status) {
        return ApiResponse.ok(exceptionService.list(status));
    }

    @PostMapping("/exceptions/{id}/resolve")
    public ApiResponse<ExceptionRecord> resolveException(@CurrentUser AuthUser user,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody AdminDtos.ResolveExceptionRequest req) {
        return ApiResponse.ok(exceptionService.resolve(id, user, req));
    }

    // ---------- 追溯 ----------

    @GetMapping("/trace/{batchNo}")
    public ApiResponse<Map<String, Object>> trace(@PathVariable String batchNo) {
        return ApiResponse.ok(traceService.traceByBatchNo(batchNo, null));
    }

    // ---------- 基础数据维护 ----------

    @PutMapping("/points-rules")
    public ApiResponse<PointsRule> upsertRule(@Valid @RequestBody AdminDtos.PointsRuleRequest req) {
        PointsRule rule = pointsRuleRepository.findByCategory(req.category())
                .orElseGet(() -> {
                    PointsRule r = new PointsRule();
                    r.setCategory(req.category());
                    return r;
                });
        rule.setPointsPerGram(req.pointsPerGram());
        rule.setDescription(req.description());
        if (req.enabled() != null) {
            rule.setEnabled(req.enabled());
        }
        return ApiResponse.ok(pointsRuleRepository.save(rule));
    }

    @PostMapping("/cabinets")
    public ApiResponse<StorageCabinet> createCabinet(@Valid @RequestBody AdminDtos.CabinetRequest req) {
        communityPointRepository.findById(req.communityPointId())
                .orElseThrow(() -> new BizException("社区点不存在"));
        if (cabinetRepository.findByCode(req.code()).isPresent()) {
            throw new BizException("柜子编号已存在: " + req.code());
        }
        StorageCabinet cabinet = new StorageCabinet();
        cabinet.setCode(req.code());
        cabinet.setCapacityBags(req.capacityBags());
        cabinet.setCommunityPointId(req.communityPointId());
        return ApiResponse.ok(cabinetRepository.save(cabinet));
    }

    @GetMapping("/cabinets")
    public ApiResponse<List<StorageCabinet>> cabinets(@RequestParam Long communityPointId) {
        return ApiResponse.ok(cabinetRepository.findByCommunityPointId(communityPointId));
    }

    @PostMapping("/activity-days")
    public ApiResponse<ActivityDay> createActivityDay(@Valid @RequestBody AdminDtos.ActivityDayRequest req) {
        communityPointRepository.findById(req.communityPointId())
                .orElseThrow(() -> new BizException("社区点不存在"));
        ActivityDay day = new ActivityDay();
        day.setName(req.name());
        day.setActivityDate(req.activityDate());
        day.setCommunityPointId(req.communityPointId());
        return ApiResponse.ok(activityDayRepository.save(day));
    }
}
