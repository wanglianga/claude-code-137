package com.community.medrecycle.auth;

import com.community.medrecycle.domain.User;

import java.lang.annotation.*;

/** 标注在 Controller 类/方法上，要求登录且角色匹配 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 允许的角色；空数组表示只需登录 */
    User.Role[] value() default {};
}
