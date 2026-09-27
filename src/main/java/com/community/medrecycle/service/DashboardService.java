package com.community.medrecycle.service;

import com.community.medrecycle.common.BizException;
import com.community.medrecycle.domain.*;
import com.community.medrecycle.dto.AdminDtos;
import com.community.medrecycle.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 社区管理员看板：暂存超期、双人交接提示、环保上门量、容量预警 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SealedBagRepository sealedBagRepository;
    private final StorageCabinetRepository cabinetRepository;
    private final CommunityPointRepository communityPointRepository;
    private final ReviewTicketRepository reviewTicketRepository;
    private final ExceptionRecordRepository exceptionRecordRepository;

    @Value("${app.storage-limit-days:14}")
    private int storageLimitDays;

    @Value("${app.cabinet-warn-ratio:0.8}")
    private double cabinetWarnRatio;

    public AdminDtos.DashboardView overview(Long communityPointId) {
        CommunityPoint point = communityPointRepository.findById(communityPointId)
                .orElseThrow(() -> new BizException("社区点不存在"));

        List<SealedBag> stored = sealedBagRepository
                .findByPointAndStatus(communityPointId, SealedBag.Status.STORED);
        long totalWeight = stored.stream().mapToLong(SealedBag::getWeightGrams).sum();

        // 超期袋：入柜超过配置天数仍未交接
        LocalDateTime deadline = LocalDateTime.now().minusDays(storageLimitDays);
        Map<Long, StorageCabinet> cabinets = cabinetRepository.findByCommunityPointId(communityPointId)
                .stream().collect(Collectors.toMap(StorageCabinet::getId, c -> c));
        List<AdminDtos.OverdueBag> overdue = new ArrayList<>();
        for (SealedBag bag : stored) {
            if (bag.getStoredAt() != null && bag.getStoredAt().isBefore(deadline)) {
                StorageCabinet cabinet = bag.getCabinetId() == null ? null : cabinets.get(bag.getCabinetId());
                overdue.add(new AdminDtos.OverdueBag(
                        bag.getSealBagNo(), bag.getCategory(), bag.getWeightGrams(),
                        cabinet == null ? null : cabinet.getCode(),
                        bag.getStoredAt().toString(),
                        Duration.between(bag.getStoredAt(), LocalDateTime.now()).toDays()));
            }
        }

        // 暂存柜容量预警
        List<AdminDtos.CabinetStatus> cabinetStatuses = new ArrayList<>();
        for (StorageCabinet cabinet : cabinets.values()) {
            int used = (int) stored.stream()
                    .filter(b -> cabinet.getId().equals(b.getCabinetId())).count();
            boolean warn = cabinet.getCapacityBags() > 0
                    && (double) used / cabinet.getCapacityBags() >= cabinetWarnRatio;
            cabinetStatuses.add(new AdminDtos.CabinetStatus(cabinet.getId(), cabinet.getCode(),
                    cabinet.getCapacityBags(), used, warn));
        }

        // 双人交接提示：暂存袋中含精神类/注射器
        boolean dualHint = stored.stream()
                .anyMatch(b -> DrugCategory.DUAL_HANDOVER.contains(b.getCategory()));

        return new AdminDtos.DashboardView(
                communityPointId,
                stored.size(),
                totalWeight,
                point.getPickupThresholdGrams(),
                totalWeight >= point.getPickupThresholdGrams(),
                overdue.size(),
                overdue,
                cabinetStatuses,
                reviewTicketRepository.countByStatus(ReviewTicket.Status.PENDING),
                exceptionRecordRepository.countByStatus(ExceptionRecord.Status.OPEN),
                dualHint);
    }
}
