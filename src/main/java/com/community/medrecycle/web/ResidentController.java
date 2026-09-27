package com.community.medrecycle.web;

import com.community.medrecycle.auth.AuthUser;
import com.community.medrecycle.auth.CurrentUser;
import com.community.medrecycle.auth.RequireRole;
import com.community.medrecycle.common.ApiResponse;
import com.community.medrecycle.domain.ExchangeOrder;
import com.community.medrecycle.domain.PointsTransaction;
import com.community.medrecycle.domain.User;
import com.community.medrecycle.dto.AppointmentDtos;
import com.community.medrecycle.dto.PointsDtos;
import com.community.medrecycle.service.AppointmentService;
import com.community.medrecycle.service.PointsService;
import com.community.medrecycle.service.TraceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 居民端：预约、积分、兑换、批次追溯 */
@RestController
@RequestMapping("/api/resident")
@RequireRole(User.Role.RESIDENT)
@RequiredArgsConstructor
public class ResidentController {

    private final AppointmentService appointmentService;
    private final PointsService pointsService;
    private final TraceService traceService;

    @PostMapping("/appointments")
    public ApiResponse<AppointmentDtos.View> create(@CurrentUser AuthUser user,
                                                    @Valid @RequestBody AppointmentDtos.CreateRequest req) {
        return ApiResponse.ok(appointmentService.create(user.userId(), req));
    }

    @GetMapping("/appointments")
    public ApiResponse<List<AppointmentDtos.View>> my(@CurrentUser AuthUser user) {
        return ApiResponse.ok(appointmentService.myAppointments(user.userId()));
    }

    @PostMapping("/appointments/{id}/cancel")
    public ApiResponse<Void> cancel(@CurrentUser AuthUser user, @PathVariable Long id) {
        appointmentService.cancel(user.userId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/points")
    public ApiResponse<PointsDtos.AccountView> points(@CurrentUser AuthUser user) {
        return ApiResponse.ok(pointsService.accountOf(user.userId()));
    }

    @GetMapping("/points/transactions")
    public ApiResponse<List<PointsTransaction>> transactions(@CurrentUser AuthUser user) {
        return ApiResponse.ok(pointsService.transactionsOf(user.userId()));
    }

    @GetMapping("/points/orders")
    public ApiResponse<List<ExchangeOrder>> orders(@CurrentUser AuthUser user) {
        return ApiResponse.ok(pointsService.ordersOf(user.userId()));
    }

    @PostMapping("/points/redeem")
    public ApiResponse<PointsDtos.RedeemResult> redeem(@CurrentUser AuthUser user,
                                                       @Valid @RequestBody PointsDtos.RedeemRequest req) {
        return ApiResponse.ok(pointsService.redeem(user.userId(), req.item(), req.points()));
    }

    /** 居民查询批次去向与积分发放结果（仅本人部分） */
    @GetMapping("/trace/{batchNo}")
    public ApiResponse<Map<String, Object>> trace(@CurrentUser AuthUser user,
                                                  @PathVariable String batchNo) {
        return ApiResponse.ok(traceService.traceByBatchNo(batchNo, user.userId()));
    }
}
