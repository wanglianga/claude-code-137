package com.community.medrecycle.service;

import com.community.medrecycle.common.BizException;
import com.community.medrecycle.common.NoGenerator;
import com.community.medrecycle.domain.ActivityDay;
import com.community.medrecycle.domain.Appointment;
import com.community.medrecycle.domain.CommunityPoint;
import com.community.medrecycle.dto.AppointmentDtos;
import com.community.medrecycle.repo.ActivityDayRepository;
import com.community.medrecycle.repo.AppointmentRepository;
import com.community.medrecycle.repo.CommunityPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 居民预约服务 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CommunityPointRepository communityPointRepository;
    private final ActivityDayRepository activityDayRepository;
    private final NoGenerator noGenerator;

    @Transactional
    public AppointmentDtos.View create(Long residentId, AppointmentDtos.CreateRequest req) {
        CommunityPoint point = communityPointRepository.findById(req.communityPointId())
                .orElseThrow(() -> new BizException("社区点不存在"));
        if (req.activityDayId() != null) {
            ActivityDay day = activityDayRepository.findById(req.activityDayId())
                    .orElseThrow(() -> new BizException("活动日不存在"));
            if (!day.getCommunityPointId().equals(point.getId())) {
                throw new BizException("活动日不属于所选社区点");
            }
        }
        Appointment a = new Appointment();
        a.setAppointmentNo(noGenerator.next("APT"));
        a.setResidentId(residentId);
        a.setCommunityPointId(req.communityPointId());
        a.setActivityDayId(req.activityDayId());
        a.setDrugType(req.drugType());
        a.setQuantity(req.quantity());
        a.setHasPrescription(Boolean.TRUE.equals(req.hasPrescription()));
        a.setIsLiquid(Boolean.TRUE.equals(req.isLiquid()));
        a.setIsInjection(Boolean.TRUE.equals(req.isInjection()));
        a.setHasPersonalInfo(Boolean.TRUE.equals(req.hasPersonalInfo()));
        a.setNote(req.note());
        appointmentRepository.save(a);
        return AppointmentDtos.View.of(a);
    }

    public List<AppointmentDtos.View> myAppointments(Long residentId) {
        return appointmentRepository.findByResidentIdOrderByIdDesc(residentId).stream()
                .map(AppointmentDtos.View::of).toList();
    }

    @Transactional
    public void cancel(Long residentId, Long appointmentId) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BizException("预约不存在"));
        if (!a.getResidentId().equals(residentId)) {
            throw new BizException("只能取消本人预约");
        }
        if (a.getStatus() != Appointment.Status.PENDING) {
            throw new BizException("当前状态不可取消");
        }
        a.setStatus(Appointment.Status.CANCELLED);
        appointmentRepository.save(a);
    }
}
