package com.community.medrecycle.dto;

import com.community.medrecycle.domain.Appointment;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 预约相关 DTO */
public class AppointmentDtos {

    public record CreateRequest(
            @NotBlank(message = "药品类型不能为空") String drugType,
            @NotNull(message = "数量不能为空") @Min(value = 1, message = "数量至少为1") Integer quantity,
            @NotNull(message = "社区点不能为空") Long communityPointId,
            Long activityDayId,
            Boolean hasPrescription,
            Boolean isLiquid,
            Boolean isInjection,
            Boolean hasPersonalInfo,
            String note) {
    }

    public record View(Long id, String appointmentNo, String drugType, Integer quantity,
                       Long communityPointId, Long activityDayId,
                       Boolean hasPrescription, Boolean isLiquid, Boolean isInjection,
                       Boolean hasPersonalInfo, String note,
                       Appointment.Status status, LocalDateTime createdAt, LocalDateTime checkedInAt) {
        public static View of(Appointment a) {
            return new View(a.getId(), a.getAppointmentNo(), a.getDrugType(), a.getQuantity(),
                    a.getCommunityPointId(), a.getActivityDayId(),
                    a.getHasPrescription(), a.getIsLiquid(), a.getIsInjection(), a.getHasPersonalInfo(),
                    a.getNote(), a.getStatus(), a.getCreatedAt(), a.getCheckedInAt());
        }
    }
}
