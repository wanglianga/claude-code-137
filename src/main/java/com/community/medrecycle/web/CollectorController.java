package com.community.medrecycle.web;

import com.community.medrecycle.auth.AuthUser;
import com.community.medrecycle.auth.CurrentUser;
import com.community.medrecycle.auth.RequireRole;
import com.community.medrecycle.common.ApiResponse;
import com.community.medrecycle.domain.*;
import com.community.medrecycle.dto.CollectorDtos;
import com.community.medrecycle.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 回收员现场作业：开批次、扫码登记、称重封袋、入柜、异常上报 */
@RestController
@RequestMapping("/api/collector")
@RequireRole({User.Role.COLLECTOR, User.Role.ADMIN})
@RequiredArgsConstructor
public class CollectorController {

    private final BatchService batchService;

    @PostMapping("/batches")
    public ApiResponse<RecycleBatch> openBatch(@CurrentUser AuthUser user,
                                               @Valid @RequestBody CollectorDtos.OpenBatchRequest req) {
        return ApiResponse.ok(batchService.openBatch(user, req));
    }

    @GetMapping("/batches/{id}")
    public ApiResponse<RecycleBatch> batch(@PathVariable Long id) {
        return ApiResponse.ok(batchService.getBatch(id));
    }

    @PostMapping("/batches/{id}/close")
    public ApiResponse<RecycleBatch> close(@PathVariable Long id) {
        return ApiResponse.ok(batchService.closeBatch(id));
    }

    @PostMapping("/batches/{id}/checkin")
    public ApiResponse<Appointment> checkin(@PathVariable Long id,
                                            @Valid @RequestBody CollectorDtos.CheckinRequest req) {
        return ApiResponse.ok(batchService.checkin(id, req));
    }

    @PostMapping("/batches/{id}/weigh")
    public ApiResponse<CollectorDtos.WeighResult> weigh(@CurrentUser AuthUser user,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody CollectorDtos.WeighRequest req) {
        return ApiResponse.ok(batchService.weigh(id, user, req));
    }

    /** 活动日大批量称重 */
    @PostMapping("/batches/{id}/weigh/batch")
    public ApiResponse<CollectorDtos.BatchWeighResult> weighBatch(
            @CurrentUser AuthUser user, @PathVariable Long id,
            @Valid @RequestBody CollectorDtos.BatchWeighRequest req) {
        return ApiResponse.ok(batchService.weighBatch(id, user, req));
    }

    @PostMapping("/batches/{id}/store")
    public ApiResponse<CollectorDtos.StoreResult> store(@PathVariable Long id,
                                                        @Valid @RequestBody CollectorDtos.StoreRequest req) {
        return ApiResponse.ok(batchService.store(id, req));
    }

    @PostMapping("/batches/{id}/exceptions")
    public ApiResponse<ExceptionRecord> reportException(@CurrentUser AuthUser user,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody CollectorDtos.ExceptionRequest req) {
        return ApiResponse.ok(batchService.reportException(id, user, req));
    }

    @GetMapping("/batches/{id}/bags")
    public ApiResponse<List<CollectorDtos.BagView>> bags(@PathVariable Long id) {
        return ApiResponse.ok(batchService.bagsOfBatch(id).stream()
                .map(CollectorDtos.BagView::of).toList());
    }

    @GetMapping("/batches/{id}/weigh-records")
    public ApiResponse<List<WeighRecord>> weighRecords(@PathVariable Long id) {
        return ApiResponse.ok(batchService.weighRecordsOfBatch(id));
    }

    @GetMapping("/batches/{id}/exceptions")
    public ApiResponse<List<ExceptionRecord>> exceptions(@PathVariable Long id) {
        return ApiResponse.ok(batchService.exceptionsOfBatch(id));
    }
}
