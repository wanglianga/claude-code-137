package com.community.medrecycle.service;

import com.community.medrecycle.auth.AuthUser;
import com.community.medrecycle.common.BizException;
import com.community.medrecycle.common.NoGenerator;
import com.community.medrecycle.domain.*;
import com.community.medrecycle.dto.CollectorDtos;
import com.community.medrecycle.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 回收批次与现场作业服务：开批次、扫码登记、分类称重封袋、入暂存柜、异常上报。
 * 预约、称重、暂存、复核、积分、交接、异常全部串在同一批次上。
 */
@Service
@RequiredArgsConstructor
public class BatchService {

    private final RecycleBatchRepository batchRepository;
    private final AppointmentRepository appointmentRepository;
    private final WeighRecordRepository weighRecordRepository;
    private final SealedBagRepository sealedBagRepository;
    private final ReviewTicketRepository reviewTicketRepository;
    private final StorageCabinetRepository cabinetRepository;
    private final ExceptionRecordRepository exceptionRecordRepository;
    private final ActivityDayRepository activityDayRepository;
    private final PointsService pointsService;
    private final NoGenerator noGenerator;

    @Value("${app.cabinet-warn-ratio:0.8}")
    private double cabinetWarnRatio;

    // ---------- 批次 ----------

    @Transactional
    public RecycleBatch openBatch(AuthUser collector, CollectorDtos.OpenBatchRequest req) {
        if (req.activityDayId() != null) {
            ActivityDay day = activityDayRepository.findById(req.activityDayId())
                    .orElseThrow(() -> new BizException("活动日不存在"));
            if (!day.getCommunityPointId().equals(req.communityPointId())) {
                throw new BizException("活动日不属于该社区点");
            }
        }
        RecycleBatch batch = new RecycleBatch();
        batch.setBatchNo(noGenerator.next("BTH"));
        batch.setCommunityPointId(req.communityPointId());
        batch.setCollectorId(collector.userId());
        batch.setActivityDayId(req.activityDayId());
        return batchRepository.save(batch);
    }

    public RecycleBatch getBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new BizException("批次不存在"));
    }

    /** 关闭现场回收：批次进入暂存阶段（要求无 OPEN 状态的待称重预约不是硬性条件，仅推进状态） */
    @Transactional
    public RecycleBatch closeBatch(Long batchId) {
        RecycleBatch batch = getBatch(batchId);
        if (batch.getStatus() != RecycleBatch.Status.OPEN) {
            throw new BizException("批次不在现场回收阶段");
        }
        batch.setStatus(RecycleBatch.Status.IN_STORAGE);
        batch.setClosedAt(LocalDateTime.now());
        return batchRepository.save(batch);
    }

    // ---------- 扫码登记 ----------

    @Transactional
    public Appointment checkin(Long batchId, CollectorDtos.CheckinRequest req) {
        RecycleBatch batch = getBatch(batchId);
        requireOpen(batch);
        Appointment a = appointmentRepository.findByAppointmentNo(req.appointmentNo())
                .orElseThrow(() -> new BizException("预约不存在: " + req.appointmentNo()));
        if (!a.getCommunityPointId().equals(batch.getCommunityPointId())) {
            throw new BizException("该预约不属于本批次社区点");
        }
        if (a.getStatus() == Appointment.Status.CANCELLED) {
            throw new BizException("预约已取消");
        }
        if (a.getStatus() == Appointment.Status.COMPLETED) {
            throw new BizException("预约已完成回收，请勿重复登记");
        }
        if (a.getStatus() == Appointment.Status.PENDING) {
            a.setStatus(Appointment.Status.CHECKED_IN);
            a.setCheckedInAt(LocalDateTime.now());
            appointmentRepository.save(a);
        }
        return a;
    }

    // ---------- 称重封袋 ----------

    /**
     * 分类称重封袋：生成称重记录与封袋；
     * 高风险品类（或预约含处方药）先进入人工复核，其余按规则即时发放积分。
     */
    @Transactional
    public CollectorDtos.WeighResult weigh(Long batchId, AuthUser collector,
                                           CollectorDtos.WeighRequest req) {
        RecycleBatch batch = getBatch(batchId);
        requireOpen(batch);

        Appointment a = appointmentRepository.findByAppointmentNo(req.appointmentNo())
                .orElseThrow(() -> new BizException("预约不存在: " + req.appointmentNo()));
        if (!a.getCommunityPointId().equals(batch.getCommunityPointId())) {
            throw new BizException("该预约不属于本批次社区点");
        }
        if (a.getStatus() == Appointment.Status.CANCELLED) {
            throw new BizException("预约已取消");
        }
        if (a.getStatus() == Appointment.Status.COMPLETED) {
            throw new BizException("预约已完成回收，请勿重复称重");
        }
        if (sealedBagRepository.existsBySealBagNo(req.sealBagNo())) {
            throw new BizException("封袋编号已存在: " + req.sealBagNo());
        }

        // 未扫码登记的预约自动登记，保证每袋都有来源
        if (a.getStatus() == Appointment.Status.PENDING) {
            a.setStatus(Appointment.Status.CHECKED_IN);
            a.setCheckedInAt(LocalDateTime.now());
        }

        String photos = req.photoUrls() == null ? null : String.join(",", req.photoUrls());

        WeighRecord record = new WeighRecord();
        record.setBatchId(batchId);
        record.setAppointmentId(a.getId());
        record.setCategory(req.category());
        record.setWeightGrams(req.weightGrams());
        record.setPhotoUrls(photos);
        record.setSealBagNo(req.sealBagNo());
        record.setWeighedBy(collector.userId());
        weighRecordRepository.save(record);

        SealedBag bag = new SealedBag();
        bag.setSealBagNo(req.sealBagNo());
        bag.setBatchId(batchId);
        bag.setAppointmentId(a.getId());
        bag.setCategory(req.category());
        bag.setWeightGrams(req.weightGrams());
        bag.setPhotoUrls(photos);

        boolean highRisk = DrugCategory.HIGH_RISK.contains(req.category())
                || Boolean.TRUE.equals(a.getHasPrescription());

        Integer points = null;
        Long reviewTicketId = null;
        String message;
        if (highRisk) {
            bag.setStatus(SealedBag.Status.REVIEW_HOLD);
            sealedBagRepository.save(bag);
            ReviewTicket ticket = new ReviewTicket();
            ticket.setBatchId(batchId);
            ticket.setBagId(bag.getId());
            ticket.setCategory(req.category());
            ticket.setReason(buildReviewReason(req.category(), a));
            reviewTicketId = reviewTicketRepository.save(ticket).getId();
            message = "高风险药品，已进入人工复核，复核通过后发放积分";
        } else {
            bag.setStatus(SealedBag.Status.SEALED);
            sealedBagRepository.save(bag);
            points = pointsService.calculatePoints(req.category(), req.weightGrams());
            pointsService.credit(a.getResidentId(), batchId, PointsTransaction.Type.EARN_WEIGH,
                    bag.getId(), points, "称重回收 " + req.category() + " " + req.weightGrams() + "g");
            message = "称重完成，积分已发放";
        }

        a.setStatus(Appointment.Status.COMPLETED);
        appointmentRepository.save(a);

        return new CollectorDtos.WeighResult(bag.getSealBagNo(), req.category(), req.weightGrams(),
                highRisk, reviewTicketId, points, message);
    }

    /** 活动日大批量称重：逐条处理，单条失败不影响整批 */
    @Transactional
    public CollectorDtos.BatchWeighResult weighBatch(Long batchId, AuthUser collector,
                                                     CollectorDtos.BatchWeighRequest req) {
        List<CollectorDtos.WeighResult> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (CollectorDtos.WeighRequest item : req.items()) {
            try {
                results.add(weigh(batchId, collector, item));
            } catch (BizException e) {
                errors.add(item.appointmentNo() + "/" + item.sealBagNo() + ": " + e.getMessage());
            }
        }
        return new CollectorDtos.BatchWeighResult(req.items().size(), results.size(),
                errors.size(), results, errors);
    }

    // ---------- 暂存柜 ----------

    @Transactional
    public CollectorDtos.StoreResult store(Long batchId, CollectorDtos.StoreRequest req) {
        RecycleBatch batch = getBatch(batchId);
        SealedBag bag = sealedBagRepository.findBySealBagNo(req.sealBagNo())
                .orElseThrow(() -> new BizException("封袋不存在: " + req.sealBagNo()));
        if (!bag.getBatchId().equals(batch.getId())) {
            throw new BizException("该封袋不属于本批次");
        }
        if (bag.getStatus() == SealedBag.Status.REVIEW_HOLD) {
            throw new BizException("该袋正在人工复核，复核通过后才能入柜");
        }
        if (bag.getStatus() != SealedBag.Status.SEALED) {
            throw new BizException("当前状态不可入柜: " + bag.getStatus());
        }
        StorageCabinet cabinet = cabinetRepository.findById(req.cabinetId())
                .orElseThrow(() -> new BizException("暂存柜不存在"));
        if (!cabinet.getCommunityPointId().equals(batch.getCommunityPointId())) {
            throw new BizException("暂存柜不属于本批次社区点");
        }

        int used = sealedBagRepository.findByCabinetIdAndStatus(cabinet.getId(),
                SealedBag.Status.STORED).size();
        if (used >= cabinet.getCapacityBags()) {
            throw new BizException("暂存柜已满，请更换柜子或先安排交接");
        }

        bag.setCabinetId(cabinet.getId());
        bag.setStoredAt(LocalDateTime.now());
        bag.setStatus(SealedBag.Status.STORED);
        sealedBagRepository.save(bag);

        boolean warn = (used + 1.0) / cabinet.getCapacityBags() >= cabinetWarnRatio;
        String message = warn
                ? "已入柜。注意：暂存柜 " + cabinet.getCode() + " 占用已达预警线（" + (used + 1) + "/"
                + cabinet.getCapacityBags() + "）"
                : "已入柜";
        return new CollectorDtos.StoreResult(bag.getSealBagNo(), bag.getStatus(), warn, message);
    }

    // ---------- 异常上报 ----------

    /**
     * 现场异常上报：未过期药混入、处方信息暴露、针剂未安全封装、液体泄漏、
     * 称重争议、环保公司拒收等，全部挂到批次上。
     */
    @Transactional
    public ExceptionRecord reportException(Long batchId, AuthUser operator,
                                           CollectorDtos.ExceptionRequest req) {
        RecycleBatch batch = getBatch(batchId);

        ExceptionRecord ex = new ExceptionRecord();
        ex.setBatchId(batch.getId());
        ex.setAppointmentId(req.appointmentId());
        ex.setBagId(req.bagId());
        ex.setType(req.type());
        ex.setDescription(req.description());

        if (req.bagId() != null) {
            SealedBag bag = sealedBagRepository.findById(req.bagId())
                    .orElseThrow(() -> new BizException("封袋不存在"));
            if (!bag.getBatchId().equals(batch.getId())) {
                throw new BizException("封袋不属于本批次");
            }
            // 环保公司拒收：袋标记为 REJECTED，不再进入交接
            if (req.type() == ExceptionRecord.Type.ENV_REJECT) {
                bag.setStatus(SealedBag.Status.REJECTED);
                sealedBagRepository.save(bag);
            }
        }
        return exceptionRecordRepository.save(ex);
    }

    // ---------- 查询 ----------

    public List<SealedBag> bagsOfBatch(Long batchId) {
        return sealedBagRepository.findByBatchId(batchId);
    }

    public List<WeighRecord> weighRecordsOfBatch(Long batchId) {
        return weighRecordRepository.findByBatchId(batchId);
    }

    public List<ExceptionRecord> exceptionsOfBatch(Long batchId) {
        return exceptionRecordRepository.findByBatchId(batchId);
    }

    private void requireOpen(RecycleBatch batch) {
        if (batch.getStatus() != RecycleBatch.Status.OPEN) {
            throw new BizException("批次已关闭现场回收，不能再登记/称重");
        }
    }

    private String buildReviewReason(DrugCategory category, Appointment a) {
        List<String> reasons = new ArrayList<>();
        if (DrugCategory.HIGH_RISK.contains(category)) {
            reasons.add("高风险品类[" + category.getLabel() + "]");
        }
        if (Boolean.TRUE.equals(a.getHasPrescription())) {
            reasons.add("预约含处方药");
        }
        return String.join("；", reasons) + "，需人工复核";
    }
}
