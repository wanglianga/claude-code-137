package com.community.medrecycle.service;

import com.community.medrecycle.auth.AuthUser;
import com.community.medrecycle.common.BizException;
import com.community.medrecycle.common.NoGenerator;
import com.community.medrecycle.domain.*;
import com.community.medrecycle.dto.AdminDtos;
import com.community.medrecycle.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 环保交接服务：创建交接单（校验每袋有来源与分类、批次无未解决异常）、
 * 完成交接（双人校验、生成去向与积分发放结果报告）。
 */
@Service
@RequiredArgsConstructor
public class HandoverService {

    private final HandoverRepository handoverRepository;
    private final HandoverBagRepository handoverBagRepository;
    private final SealedBagRepository sealedBagRepository;
    private final RecycleBatchRepository batchRepository;
    private final ExceptionRecordRepository exceptionRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final NoGenerator noGenerator;

    /**
     * 创建交接单：收集社区点下可交接（STORED）的袋，可按批次过滤。
     * 交接前校验：每袋必须有来源预约与分类；相关批次不能有未解决异常。
     */
    @Transactional
    public Handover create(AuthUser admin, AdminDtos.HandoverCreateRequest req) {
        List<SealedBag> stored = sealedBagRepository
                .findByPointAndStatus(req.communityPointId(), SealedBag.Status.STORED);
        if (req.batchIds() != null && !req.batchIds().isEmpty()) {
            Set<Long> filter = new HashSet<>(req.batchIds());
            stored = stored.stream().filter(b -> filter.contains(b.getBatchId())).toList();
        }
        if (stored.isEmpty()) {
            throw new BizException("没有可交接的暂存药品（需已入柜且复核通过）");
        }

        // 校验：每袋必须有来源与分类
        for (SealedBag bag : stored) {
            if (bag.getAppointmentId() == null || bag.getCategory() == null) {
                throw new BizException("封袋 " + bag.getSealBagNo() + " 缺少来源或分类，不能交接");
            }
        }
        // 校验：相关批次不能有未解决异常
        Set<Long> batchIds = stored.stream().map(SealedBag::getBatchId).collect(Collectors.toSet());
        for (Long batchId : batchIds) {
            long open = exceptionRecordRepository.countByBatchIdAndStatus(batchId,
                    ExceptionRecord.Status.OPEN);
            if (open > 0) {
                throw new BizException("批次 " + batchId + " 存在 " + open
                        + " 条未解决异常，请先处理再交接");
            }
        }

        boolean dual = stored.stream().anyMatch(b -> DrugCategory.DUAL_HANDOVER.contains(b.getCategory()));
        int totalWeight = stored.stream().mapToInt(SealedBag::getWeightGrams).sum();

        Handover handover = new Handover();
        handover.setHandoverNo(noGenerator.next("HDO"));
        handover.setCommunityPointId(req.communityPointId());
        handover.setEnvCompany(req.envCompany());
        handover.setTotalWeightGrams(totalWeight);
        handover.setBagCount(stored.size());
        handover.setDualRequired(dual);
        handover.setHandedBy(admin.userId());
        handoverRepository.save(handover);

        for (SealedBag bag : stored) {
            HandoverBag hb = new HandoverBag();
            hb.setHandoverId(handover.getId());
            hb.setBagId(bag.getId());
            handoverBagRepository.save(hb);
            bag.setHandoverId(handover.getId());
            sealedBagRepository.save(bag);
        }
        return handover;
    }

    /**
     * 完成交接：录入签收人（双人交接需第二签收人）与照片，
     * 袋与批次推进状态，生成去向/重量/照片/签收人/积分发放结果。
     */
    @Transactional
    public Handover complete(Long handoverId, AdminDtos.HandoverCompleteRequest req) {
        Handover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new BizException("交接单不存在"));
        if (handover.getStatus() != Handover.Status.DRAFT) {
            throw new BizException("交接单已完成");
        }
        if (Boolean.TRUE.equals(handover.getDualRequired())
                && (req.secondReceiverName() == null || req.secondReceiverName().isBlank())) {
            throw new BizException("本批次含精神类/注射器等高风险药品，需双人交接，请填写第二签收人");
        }

        List<HandoverBag> links = handoverBagRepository.findByHandoverId(handoverId);
        List<SealedBag> bags = sealedBagRepository.findByIdIn(
                links.stream().map(HandoverBag::getBagId).toList());

        handover.setReceiverName(req.receiverName());
        handover.setSecondReceiverName(req.secondReceiverName());
        handover.setPhotoUrls(req.photoUrls() == null ? null : String.join(",", req.photoUrls()));
        handover.setStatus(Handover.Status.COMPLETED);
        handover.setCompletedAt(LocalDateTime.now());
        handoverRepository.save(handover);

        Set<Long> batchIds = new HashSet<>();
        for (SealedBag bag : bags) {
            bag.setStatus(SealedBag.Status.HANDED_OVER);
            sealedBagRepository.save(bag);
            batchIds.add(bag.getBatchId());
        }
        // 批次内所有袋都已交接/拒收 → 批次完成
        for (Long batchId : batchIds) {
            boolean allDone = sealedBagRepository.findByBatchId(batchId).stream()
                    .allMatch(b -> b.getStatus() == SealedBag.Status.HANDED_OVER
                            || b.getStatus() == SealedBag.Status.REJECTED);
            if (allDone) {
                RecycleBatch batch = batchRepository.findById(batchId).orElseThrow();
                batch.setStatus(RecycleBatch.Status.HANDED_OVER);
                batchRepository.save(batch);
            }
        }
        return handover;
    }

    /** 交接报告：去向、重量、照片、签收人、每袋明细与积分发放结果 */
    public Map<String, Object> report(Long handoverId) {
        Handover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new BizException("交接单不存在"));
        List<HandoverBag> links = handoverBagRepository.findByHandoverId(handoverId);
        List<SealedBag> bags = sealedBagRepository.findByIdIn(
                links.stream().map(HandoverBag::getBagId).toList());

        List<Map<String, Object>> bagDetails = new ArrayList<>();
        Map<Long, Integer> pointsByResident = new LinkedHashMap<>();
        for (SealedBag bag : bags) {
            Appointment a = appointmentRepository.findById(bag.getAppointmentId()).orElse(null);
            // 该袋关联的积分流水（称重发放 + 复核发放 + 调整）
            List<PointsTransaction> txns = new ArrayList<>(
                    pointsTransactionRepository.findByRefTypeAndRefId(
                            PointsTransaction.Type.EARN_WEIGH, bag.getId()));
            txns.addAll(pointsTransactionRepository.findByRefTypeAndRefId(
                    PointsTransaction.Type.EARN_REVIEW, bag.getId()));
            txns.addAll(pointsTransactionRepository.findByRefTypeAndRefId(
                    PointsTransaction.Type.ADJUST, bag.getId()));
            int bagPoints = txns.stream()
                    .filter(t -> t.getStatus() == PointsTransaction.Status.SUCCESS)
                    .mapToInt(PointsTransaction::getPoints).sum();
            Long residentId = a == null ? null : a.getResidentId();
            if (residentId != null) {
                pointsByResident.merge(residentId, bagPoints, Integer::sum);
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("sealBagNo", bag.getSealBagNo());
            detail.put("category", bag.getCategory());
            detail.put("weightGrams", bag.getWeightGrams());
            detail.put("sourceAppointmentNo", a == null ? null : a.getAppointmentNo());
            detail.put("residentId", residentId);
            detail.put("pointsIssued", bagPoints);
            detail.put("photoUrls", bag.getPhotoUrls());
            bagDetails.add(detail);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("handoverNo", handover.getHandoverNo());
        report.put("status", handover.getStatus());
        report.put("communityPointId", handover.getCommunityPointId());
        report.put("destination", handover.getEnvCompany());
        report.put("totalWeightGrams", handover.getTotalWeightGrams());
        report.put("bagCount", handover.getBagCount());
        report.put("dualRequired", handover.getDualRequired());
        report.put("receiverName", handover.getReceiverName());
        report.put("secondReceiverName", handover.getSecondReceiverName());
        report.put("photoUrls", handover.getPhotoUrls());
        report.put("completedAt", handover.getCompletedAt());
        report.put("bags", bagDetails);
        report.put("pointsIssuedByResident", pointsByResident);
        report.put("totalPointsIssued", pointsByResident.values().stream().mapToInt(i -> i).sum());
        return report;
    }

    public List<Handover> listByPoint(Long communityPointId) {
        return handoverRepository.findByCommunityPointIdOrderByIdDesc(communityPointId);
    }
}
