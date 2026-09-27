package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 异常记录：与批次串联，未解决的异常会阻止批次交接 */
@Getter
@Setter
@Entity
@Table(name = "exception_records")
public class ExceptionRecord {

    public enum Type {
        UNEXPIRED_DRUG,            // 夹带未过期药
        PRESCRIPTION_INFO_EXPOSED, // 药盒上有完整处方信息
        UNSAFE_INJECTION,          // 针剂未做安全封装
        LIQUID_LEAK,               // 液体泄漏
        WEIGHT_DISPUTE,            // 称重争议
        POINTS_EXCHANGE_FAILED,    // 积分兑换失败
        ENV_REJECT,                // 环保公司拒收某类药品
        REVIEW_REJECTED,           // 人工复核不通过
        GENERAL                    // 其他
    }

    public enum Status {OPEN, RESOLVED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long batchId;

    private Long appointmentId;

    private Long bagId;

    private Long exchangeOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    private Long handledBy;

    @Column(length = 500)
    private String resolution;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime resolvedAt;
}
