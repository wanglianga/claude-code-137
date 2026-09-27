package com.community.medrecycle.dto;

import com.community.medrecycle.domain.DrugCategory;
import com.community.medrecycle.domain.ExceptionRecord;
import com.community.medrecycle.domain.SealedBag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/** 回收员现场作业相关 DTO */
public class CollectorDtos {

    public record OpenBatchRequest(
            @NotNull(message = "社区点不能为空") Long communityPointId,
            Long activityDayId) {
    }

    public record CheckinRequest(
            @NotBlank(message = "预约号不能为空") String appointmentNo) {
    }

    public record WeighRequest(
            @NotBlank(message = "预约号不能为空") String appointmentNo,
            @NotNull(message = "药品分类不能为空") DrugCategory category,
            @NotNull(message = "重量不能为空") @Min(value = 1, message = "重量至少1克") Integer weightGrams,
            @NotBlank(message = "封袋编号不能为空") String sealBagNo,
            List<String> photoUrls) {
    }

    /** 活动日大批量称重 */
    public record BatchWeighRequest(
            @NotEmpty(message = "称重明细不能为空") List<@Valid WeighRequest> items) {
    }

    public record WeighResult(String sealBagNo, DrugCategory category, Integer weightGrams,
                              boolean highRisk, Long reviewTicketId,
                              Integer pointsCredited, String message) {
    }

    public record BatchWeighResult(int total, int succeeded, int failed,
                                   List<WeighResult> results, List<String> errors) {
    }

    public record StoreRequest(
            @NotBlank(message = "封袋编号不能为空") String sealBagNo,
            @NotNull(message = "暂存柜不能为空") Long cabinetId) {
    }

    public record StoreResult(String sealBagNo, SealedBag.Status status,
                              boolean capacityWarning, String message) {
    }

    public record ExceptionRequest(
            @NotNull(message = "异常类型不能为空") ExceptionRecord.Type type,
            @NotBlank(message = "异常描述不能为空") String description,
            Long appointmentId,
            Long bagId) {
    }

    public record BagView(Long id, String sealBagNo, Long batchId, Long appointmentId,
                          DrugCategory category, Integer weightGrams, List<String> photoUrls,
                          SealedBag.Status status, Long cabinetId,
                          LocalDateTime storedAt, Long handoverId, LocalDateTime createdAt) {
        public static BagView of(SealedBag b) {
            List<String> photos = b.getPhotoUrls() == null || b.getPhotoUrls().isBlank()
                    ? List.of() : List.of(b.getPhotoUrls().split(","));
            return new BagView(b.getId(), b.getSealBagNo(), b.getBatchId(), b.getAppointmentId(),
                    b.getCategory(), b.getWeightGrams(), photos, b.getStatus(), b.getCabinetId(),
                    b.getStoredAt(), b.getHandoverId(), b.getCreatedAt());
        }
    }
}
