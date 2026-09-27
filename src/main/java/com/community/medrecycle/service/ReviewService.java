package com.community.medrecycle.service;

import com.community.medrecycle.auth.AuthUser;
import com.community.medrecycle.common.BizException;
import com.community.medrecycle.domain.*;
import com.community.medrecycle.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 高风险药品人工复核 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewTicketRepository reviewTicketRepository;
    private final SealedBagRepository sealedBagRepository;
    private final AppointmentRepository appointmentRepository;
    private final ExceptionRecordRepository exceptionRecordRepository;
    private final PointsService pointsService;

    public List<ReviewTicket> list(ReviewTicket.Status status) {
        return status == null ? reviewTicketRepository.findAll()
                : reviewTicketRepository.findByStatusOrderByIdAsc(status);
    }

    @Transactional
    public ReviewTicket decide(Long ticketId, AuthUser reviewer, boolean approved, String comment) {
        ReviewTicket ticket = reviewTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BizException("复核单不存在"));
        if (ticket.getStatus() != ReviewTicket.Status.PENDING) {
            throw new BizException("复核单已处理");
        }
        SealedBag bag = sealedBagRepository.findById(ticket.getBagId())
                .orElseThrow(() -> new BizException("封袋不存在"));
        if (bag.getStatus() != SealedBag.Status.REVIEW_HOLD) {
            throw new BizException("封袋不在复核状态");
        }

        ticket.setReviewerId(reviewer.userId());
        ticket.setComment(comment);
        ticket.setDecidedAt(LocalDateTime.now());

        if (approved) {
            ticket.setStatus(ReviewTicket.Status.APPROVED);
            bag.setStatus(SealedBag.Status.SEALED);
            // 先落库再发积分：积分入账会清理持久化上下文
            sealedBagRepository.saveAndFlush(bag);
            // 复核通过：按品类与重量发放积分
            Appointment a = appointmentRepository.findById(bag.getAppointmentId())
                    .orElseThrow(() -> new BizException("来源预约不存在"));
            int points = pointsService.calculatePoints(bag.getCategory(), bag.getWeightGrams());
            pointsService.credit(a.getResidentId(), bag.getBatchId(),
                    PointsTransaction.Type.EARN_REVIEW, bag.getId(), points,
                    "复核通过发放 " + bag.getCategory() + " " + bag.getWeightGrams() + "g");
        } else {
            ticket.setStatus(ReviewTicket.Status.REJECTED);
            bag.setStatus(SealedBag.Status.REJECTED);
            sealedBagRepository.save(bag);
            // 复核不通过：不发放积分，生成异常记录挂到批次
            ExceptionRecord ex = new ExceptionRecord();
            ex.setBatchId(bag.getBatchId());
            ex.setAppointmentId(bag.getAppointmentId());
            ex.setBagId(bag.getId());
            ex.setType(ExceptionRecord.Type.REVIEW_REJECTED);
            ex.setDescription("复核不通过：" + (comment == null ? "未填写原因" : comment));
            exceptionRecordRepository.save(ex);
        }
        return reviewTicketRepository.save(ticket);
    }
}
