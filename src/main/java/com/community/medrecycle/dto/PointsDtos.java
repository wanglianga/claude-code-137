package com.community.medrecycle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 积分相关 DTO */
public class PointsDtos {

    public record RedeemRequest(
            @NotBlank(message = "兑换物品不能为空") String item,
            @NotNull(message = "兑换积分不能为空") @Min(value = 1, message = "兑换积分至少1分") Integer points) {
    }

    public record AccountView(Long userId, Integer balance) {
    }

    public record RedeemResult(Long orderId, String status, Integer balanceAfter, String message) {
    }
}
