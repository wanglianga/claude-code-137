package com.community.medrecycle.service;

import com.community.medrecycle.common.BizException;
import com.community.medrecycle.domain.*;
import com.community.medrecycle.dto.PointsDtos;
import com.community.medrecycle.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 积分服务：按品类规则计算积分、入账、兑换（峰值下原子扣减）。
 */
@Service
@RequiredArgsConstructor
public class PointsService {

    private final PointsRuleRepository pointsRuleRepository;
    private final PointsAccountRepository pointsAccountRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final ExchangeOrderRepository exchangeOrderRepository;
    private final ExceptionRecordRepository exceptionRecordRepository;

    /** 按品类规则计算积分：每克积分 × 克重，四舍五入取整 */
    public int calculatePoints(DrugCategory category, int weightGrams) {
        PointsRule rule = pointsRuleRepository.findByCategory(category)
                .filter(PointsRule::getEnabled)
                .orElseThrow(() -> new BizException("该品类积分规则不存在或已停用: " + category));
        return rule.getPointsPerGram()
                .multiply(BigDecimal.valueOf(weightGrams))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** 入账积分并记录流水 */
    @Transactional
    public PointsTransaction credit(Long userId, Long batchId, PointsTransaction.Type type,
                                    Long refId, int points, String remark) {
        if (points <= 0) {
            throw new BizException("入账积分必须为正数");
        }
        ensureAccount(userId);
        pointsAccountRepository.addBalance(userId, points);
        PointsTransaction txn = new PointsTransaction();
        txn.setUserId(userId);
        txn.setBatchId(batchId);
        txn.setRefType(type);
        txn.setRefId(refId);
        txn.setPoints(points);
        txn.setStatus(PointsTransaction.Status.SUCCESS);
        txn.setRemark(remark);
        return pointsTransactionRepository.save(txn);
    }

    /**
     * 积分兑换：原子扣减，峰值并发下不会超扣；
     * 余额不足时生成失败订单并记录异常（POINTS_EXCHANGE_FAILED），与批次链路对齐。
     */
    @Transactional
    public PointsDtos.RedeemResult redeem(Long userId, String item, int points) {
        pointsAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException("积分账户不存在"));

        int updated = pointsAccountRepository.deductBalance(userId, points);
        if (updated == 0) {
            ExchangeOrder order = new ExchangeOrder();
            order.setUserId(userId);
            order.setItem(item);
            order.setPoints(points);
            order.setStatus(ExchangeOrder.Status.FAILED);
            order.setFailReason("积分余额不足");
            exchangeOrderRepository.save(order);

            ExceptionRecord ex = new ExceptionRecord();
            ex.setExchangeOrderId(order.getId());
            ex.setType(ExceptionRecord.Type.POINTS_EXCHANGE_FAILED);
            ex.setDescription("用户 " + userId + " 兑换[" + item + "]需 " + points + " 分，余额不足");
            exceptionRecordRepository.save(ex);

            int balance = pointsAccountRepository.findById(userId).get().getBalance();
            return new PointsDtos.RedeemResult(order.getId(), "FAILED", balance, "积分余额不足，兑换失败");
        }

        ExchangeOrder order = new ExchangeOrder();
        order.setUserId(userId);
        order.setItem(item);
        order.setPoints(points);
        order.setStatus(ExchangeOrder.Status.SUCCESS);
        exchangeOrderRepository.save(order);

        PointsTransaction txn = new PointsTransaction();
        txn.setUserId(userId);
        txn.setRefType(PointsTransaction.Type.REDEEM);
        txn.setRefId(order.getId());
        txn.setPoints(-points);
        txn.setStatus(PointsTransaction.Status.SUCCESS);
        txn.setRemark("兑换[" + item + "]");
        pointsTransactionRepository.save(txn);

        int balance = pointsAccountRepository.findById(userId).get().getBalance();
        return new PointsDtos.RedeemResult(order.getId(), "SUCCESS", balance, "兑换成功");
    }

    /** 争议调整（可正可负） */
    @Transactional
    public void adjust(Long userId, Long batchId, Long refId, int deltaPoints, String remark) {
        if (deltaPoints == 0) {
            return;
        }
        ensureAccount(userId);
        if (deltaPoints > 0) {
            pointsAccountRepository.addBalance(userId, deltaPoints);
        } else {
            pointsAccountRepository.deductBalance(userId, -deltaPoints);
        }
        PointsTransaction txn = new PointsTransaction();
        txn.setUserId(userId);
        txn.setBatchId(batchId);
        txn.setRefType(PointsTransaction.Type.ADJUST);
        txn.setRefId(refId);
        txn.setPoints(deltaPoints);
        txn.setStatus(PointsTransaction.Status.SUCCESS);
        txn.setRemark(remark);
        pointsTransactionRepository.save(txn);
    }

    /** 兜底：积分账户不存在时创建，避免入账静默丢失 */
    private void ensureAccount(Long userId) {
        if (!pointsAccountRepository.existsById(userId)) {
            PointsAccount account = new PointsAccount();
            account.setUserId(userId);
            account.setBalance(0);
            pointsAccountRepository.saveAndFlush(account);
        }
    }

    public PointsDtos.AccountView accountOf(Long userId) {
        PointsAccount account = pointsAccountRepository.findById(userId)
                .orElseGet(() -> {
                    PointsAccount a = new PointsAccount();
                    a.setUserId(userId);
                    a.setBalance(0);
                    return a;
                });
        return new PointsDtos.AccountView(userId, account.getBalance());
    }

    public List<PointsTransaction> transactionsOf(Long userId) {
        return pointsTransactionRepository.findByUserIdOrderByIdDesc(userId);
    }

    public List<ExchangeOrder> ordersOf(Long userId) {
        return exchangeOrderRepository.findByUserIdOrderByIdDesc(userId);
    }
}
