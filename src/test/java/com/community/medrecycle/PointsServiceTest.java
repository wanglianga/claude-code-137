package com.community.medrecycle;

import com.community.medrecycle.domain.DrugCategory;
import com.community.medrecycle.domain.ExceptionRecord;
import com.community.medrecycle.domain.User;
import com.community.medrecycle.dto.PointsDtos;
import com.community.medrecycle.repo.ExceptionRecordRepository;
import com.community.medrecycle.repo.PointsAccountRepository;
import com.community.medrecycle.repo.UserRepository;
import com.community.medrecycle.service.PointsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PointsServiceTest {

    @Autowired
    PointsService pointsService;
    @Autowired
    PointsAccountRepository accountRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    ExceptionRecordRepository exceptionRecordRepository;

    @Test
    void calculatePointsByCategoryRule() {
        // TABLET 0.1 分/克；PSYCHOTROPIC 0.5 分/克
        assertEquals(50, pointsService.calculatePoints(DrugCategory.TABLET, 500));
        assertEquals(250, pointsService.calculatePoints(DrugCategory.PSYCHOTROPIC, 500));
        assertEquals(15, pointsService.calculatePoints(DrugCategory.LIQUID, 100));
    }

    @Test
    void redeemSuccessAndInsufficientBalance() {
        Long residentId = userRepository.findByUsername("resident01").orElseThrow().getId();
        pointsService.credit(residentId, null, com.community.medrecycle.domain.PointsTransaction.Type.EARN_WEIGH,
                -1L, 100, "测试入账");

        PointsDtos.RedeemResult ok = pointsService.redeem(residentId, "环保袋", 30);
        assertEquals("SUCCESS", ok.status());
        assertEquals(70, ok.balanceAfter());

        // 余额不足：兑换失败 + 生成异常记录
        PointsDtos.RedeemResult fail = pointsService.redeem(residentId, "大米", 1000);
        assertEquals("FAILED", fail.status());
        assertEquals(70, fail.balanceAfter());
        List<ExceptionRecord> exs = exceptionRecordRepository.findByStatusOrderByIdDesc(
                ExceptionRecord.Status.OPEN);
        assertTrue(exs.stream().anyMatch(e -> e.getType() == ExceptionRecord.Type.POINTS_EXCHANGE_FAILED));
    }

    @Test
    void concurrentRedeemNeverOverdraft() throws InterruptedException {
        // 新用户，入账 100 分，20 线程并发各兑 10 分：最多成功 10 次，余额永不为负
        User u = new User();
        u.setUsername("peak_user");
        u.setPasswordHash("x");
        u.setName("峰值用户");
        u.setRole(User.Role.RESIDENT);
        userRepository.save(u);
        pointsService.credit(u.getId(), null, com.community.medrecycle.domain.PointsTransaction.Type.EARN_WEIGH,
                -1L, 100, "测试入账");

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    if ("SUCCESS".equals(pointsService.redeem(u.getId(), "纸巾", 10).status())) {
                        success.incrementAndGet();
                    }
                } catch (Throwable t) {
                    synchronized (errors) {
                        errors.add(t);
                    }
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertTrue(errors.isEmpty(), "并发兑换出现异常: " + errors);
        assertEquals(10, success.get(), "100 分最多成功 10 次 10 分兑换");
        int balance = accountRepository.findById(u.getId()).orElseThrow().getBalance();
        assertEquals(0, balance, "余额应恰好扣完且不为负");
    }
}
