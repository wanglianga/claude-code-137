package com.community.medrecycle.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * 药品分类：普通片剂、抗生素、精神类、注射器、外用药、液体药。
 */
public enum DrugCategory {
    TABLET("普通片剂"),
    ANTIBIOTIC("抗生素"),
    PSYCHOTROPIC("精神类"),
    SYRINGE("注射器"),
    EXTERNAL("外用药"),
    LIQUID("液体药");

    private final String label;

    DrugCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 高风险品类：称重后须先进入人工复核，复核通过才发放积分 */
    public static final Set<DrugCategory> HIGH_RISK =
            EnumSet.of(ANTIBIOTIC, PSYCHOTROPIC, SYRINGE);

    /** 含此类袋的交接批次需要双人交接 */
    public static final Set<DrugCategory> DUAL_HANDOVER =
            EnumSet.of(PSYCHOTROPIC, SYRINGE);
}
