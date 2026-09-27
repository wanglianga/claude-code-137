package com.community.medrecycle.dto;

import com.community.medrecycle.domain.DrugCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 管理端相关 DTO */
public class AdminDtos {

    public record PointsRuleRequest(
            @NotNull(message = "品类不能为空") DrugCategory category,
            @NotNull(message = "每克积分不能为空") @PositiveOrZero(message = "每克积分不能为负") BigDecimal pointsPerGram,
            String description,
            Boolean enabled) {
    }

    public record CabinetRequest(
            @NotBlank(message = "柜子编号不能为空") String code,
            @NotNull(message = "容量不能为空") Integer capacityBags,
            @NotNull(message = "社区点不能为空") Long communityPointId) {
    }

    public record ActivityDayRequest(
            @NotBlank(message = "活动名称不能为空") String name,
            @NotNull(message = "活动日期不能为空") LocalDate activityDate,
            @NotNull(message = "社区点不能为空") Long communityPointId) {
    }

    public record ReviewDecisionRequest(
            @NotNull(message = "复核结论不能为空") Boolean approved,
            String comment) {
    }

    public record HandoverCreateRequest(
            @NotNull(message = "社区点不能为空") Long communityPointId,
            @NotBlank(message = "环保公司不能为空") String envCompany,
            List<Long> batchIds) {
    }

    public record HandoverCompleteRequest(
            @NotBlank(message = "签收人不能为空") String receiverName,
            String secondReceiverName,
            List<String> photoUrls) {
    }

    public record ResolveExceptionRequest(
            @NotBlank(message = "处理结果不能为空") String resolution,
            Integer finalWeightGrams) {
    }

    // ---- 看板视图 ----

    public record CabinetStatus(Long cabinetId, String code, int capacityBags, int usedBags,
                                boolean capacityWarning) {
    }

    public record OverdueBag(String sealBagNo, DrugCategory category, int weightGrams,
                             String cabinetCode, String storedAt, long storedDays) {
    }

    public record DashboardView(Long communityPointId,
                                int storedBagCount,
                                long storedTotalWeightGrams,
                                int pickupThresholdGrams,
                                boolean pickupReady,
                                int overdueBagCount,
                                List<OverdueBag> overdueBags,
                                List<CabinetStatus> cabinets,
                                long pendingReviewCount,
                                long openExceptionCount,
                                boolean dualHandoverHint) {
    }
}
