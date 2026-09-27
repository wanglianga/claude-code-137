package com.community.medrecycle.auth;

import java.lang.annotation.*;

/** 注入当前登录用户（AuthUser） */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
