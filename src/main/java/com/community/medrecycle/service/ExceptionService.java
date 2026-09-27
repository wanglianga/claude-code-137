package com.community.medrecycle.service;

import com.community.medrecycle.auth.AuthUser;
import com.community.medrecycle.common.BizException;
import com.community.medrecycle.domain.*;
import com.community.medrecycle.dto.AdminDtos;
import com.community.medrecycle.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 异常处理：称重争议复称调整、异常解决 */
@Service
@RequiredArgsConstructor
public class ExceptionService {

    private final ExceptionRecordRepository exceptionRecordRepository;
    private final SealedBagRepository sealedBagRepository;
    private final AppointmentRepository appointmentRepository;
    private final PointsService pointsService;

    public List<ExceptionRecord> list(ExceptionRecord.Status status) {
        return status == null ? exceptionRecordRepository.findAll()
                : exceptionRecordRepository.findByStatusOrderByIdDesc(status);
    }

    /**
     * 解决异常。称重争议（WEIGHT_DISPUTE）可带 finalWeightGrams 复称定重：
     * 更新袋重并按已发放积分做差额调整（ADJUST）。
     */
    @Transactional
    public ExceptionRecord resolve(Long exceptionId, AuthUser handler,
                                   AdminDtos.ResolveExceptionRequest req) {
        ExceptionRecord ex = exceptionRecordRepository.findById(exceptionId)
                .orElseThrow(() -> new BizException("异常记录不存在"));
        if (ex.getStatus() == ExceptionRecord.Status.RESOLVED) {
            throw new BizException("异常已处理");
        }

        if (ex.getType() == ExceptionRecord.Type.WEIGHT_DISPUTE
                && req.finalWeightGrams() != null && ex.getBagId() != null) {
            reweigh(ex, req.finalWeightGrams());
        }

        ex.setStatus(ExceptionRecord.Status.RESOLVED);
        ex.setHandledBy(handler.userId());
        ex.setResolution(req.resolution());
        ex.setResolvedAt(LocalDateTime.now());
        return exceptionRecordRepository.save(ex);
    }

    private void reweigh(ExceptionRecord ex, int finalWeightGrams) {
        SealedBag bag = sealedBagRepository.findById(ex.getBagId())
                .orElseThrow(() -> new BizException("封袋不存在"));
        if (bag.getStatus() == SealedBag.Status.HANDED_OVER) {
            throw new BizException("该袋已完成环保交接，不能再改重量");
        }
        int oldWeight = bag.getWeightGrams();
        if (oldWeight == finalWeightGrams) {
            return;
        }
        bag.setWeightGrams(finalWeightGrams);
        // 先落库再调积分：积分调整会清理持久化上下文
        sealedBagRepository.saveAndFlush(bag);

        // 已发放积分按新重量重算，差额调整
        Appointment a = appointmentRepository.findById(bag.getAppointmentId())
                .orElseThrow(() -> new BizException("来源预约不存在"));
        int oldPoints = pointsService.calculatePoints(bag.getCategory(), oldWeight);
        int newPoints = pointsService.calculatePoints(bag.getCategory(), finalWeightGrams);
        int delta = newPoints - oldPoints;
        if (delta != 0) {
            pointsService.adjust(a.getResidentId(), bag.getBatchId(), bag.getId(), delta,
                    "称重争议复称 " + oldWeight + "g→" + finalWeightGrams + "g，积分差额调整");
        }
    }
}
