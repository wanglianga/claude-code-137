package com.community.medrecycle.config;

import com.community.medrecycle.domain.*;
import com.community.medrecycle.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 首次启动初始化演示数据：社区点、积分规则、暂存柜、活动日、测试账号。
 * 幂等：已有数据则跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CommunityPointRepository communityPointRepository;
    private final UserRepository userRepository;
    private final PointsRuleRepository pointsRuleRepository;
    private final StorageCabinetRepository cabinetRepository;
    private final ActivityDayRepository activityDayRepository;
    private final PointsAccountRepository pointsAccountRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(String... args) {
        if (communityPointRepository.count() > 0) {
            return;
        }
        log.info("初始化演示数据...");

        CommunityPoint p1 = new CommunityPoint();
        p1.setName("阳光社区回收点");
        p1.setAddress("阳光路 12 号社区服务中心");
        p1.setPickupThresholdGrams(50000);
        communityPointRepository.save(p1);

        CommunityPoint p2 = new CommunityPoint();
        p2.setName("河畔社区回收点");
        p2.setAddress("河畔街 3 号文化活动室");
        p2.setPickupThresholdGrams(30000);
        communityPointRepository.save(p2);

        rule(DrugCategory.TABLET, "0.10", "普通片剂 0.1 分/克");
        rule(DrugCategory.ANTIBIOTIC, "0.20", "抗生素 0.2 分/克（高风险，需复核）");
        rule(DrugCategory.PSYCHOTROPIC, "0.50", "精神类 0.5 分/克（高风险，需复核，双人交接）");
        rule(DrugCategory.SYRINGE, "0.50", "注射器 0.5 分/克（高风险，需复核，双人交接）");
        rule(DrugCategory.EXTERNAL, "0.10", "外用药 0.1 分/克");
        rule(DrugCategory.LIQUID, "0.15", "液体药 0.15 分/克");

        cabinet("YG-A01", 20, p1.getId());
        cabinet("YG-A02", 20, p1.getId());
        cabinet("HP-B01", 30, p2.getId());

        ActivityDay day = new ActivityDay();
        day.setName("秋季过期药集中回收日");
        day.setActivityDate(LocalDate.now().plusDays(3));
        day.setCommunityPointId(p1.getId());
        activityDayRepository.save(day);

        user("admin", "admin123", "社区管理员", User.Role.ADMIN, p1.getId());
        user("collector01", "collector123", "回收员小王", User.Role.COLLECTOR, p1.getId());
        user("resident01", "resident123", "居民张三", User.Role.RESIDENT, null);
        user("resident02", "resident123", "居民李四", User.Role.RESIDENT, null);

        log.info("演示数据初始化完成。测试账号: admin/admin123, collector01/collector123, resident01/resident123");
    }

    private void rule(DrugCategory category, String pointsPerGram, String description) {
        PointsRule rule = new PointsRule();
        rule.setCategory(category);
        rule.setPointsPerGram(new BigDecimal(pointsPerGram));
        rule.setDescription(description);
        pointsRuleRepository.save(rule);
    }

    private void cabinet(String code, int capacity, Long pointId) {
        StorageCabinet cabinet = new StorageCabinet();
        cabinet.setCode(code);
        cabinet.setCapacityBags(capacity);
        cabinet.setCommunityPointId(pointId);
        cabinetRepository.save(cabinet);
    }

    private void user(String username, String password, String name, User.Role role, Long pointId) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setName(name);
        u.setRole(role);
        u.setCommunityPointId(pointId);
        userRepository.save(u);
        if (role == User.Role.RESIDENT) {
            PointsAccount account = new PointsAccount();
            account.setUserId(u.getId());
            account.setBalance(0);
            pointsAccountRepository.save(account);
        }
    }
}
