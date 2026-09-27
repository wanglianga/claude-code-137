package com.community.medrecycle.common;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** 业务单号生成器：前缀 + 时间戳 + 随机数，配合数据库唯一约束 */
@Component
public class NoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    public String next(String prefix) {
        String ts = LocalDateTime.now().format(FMT);
        int rand = ThreadLocalRandom.current().nextInt(1000, 10000);
        return prefix + ts + rand;
    }
}
