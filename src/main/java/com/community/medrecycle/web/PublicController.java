package com.community.medrecycle.web;

import com.community.medrecycle.common.ApiResponse;
import com.community.medrecycle.domain.ActivityDay;
import com.community.medrecycle.domain.CommunityPoint;
import com.community.medrecycle.domain.PointsRule;
import com.community.medrecycle.repo.ActivityDayRepository;
import com.community.medrecycle.repo.CommunityPointRepository;
import com.community.medrecycle.repo.PointsRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 公共基础数据：社区点、积分规则、活动日 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final CommunityPointRepository communityPointRepository;
    private final PointsRuleRepository pointsRuleRepository;
    private final ActivityDayRepository activityDayRepository;

    @GetMapping("/community-points")
    public ApiResponse<List<CommunityPoint>> communityPoints() {
        return ApiResponse.ok(communityPointRepository.findAll());
    }

    @GetMapping("/points-rules")
    public ApiResponse<List<PointsRule>> pointsRules() {
        return ApiResponse.ok(pointsRuleRepository.findAll());
    }

    @GetMapping("/activity-days")
    public ApiResponse<List<ActivityDay>> activityDays(@RequestParam Long communityPointId) {
        return ApiResponse.ok(activityDayRepository
                .findByCommunityPointIdOrderByActivityDateDesc(communityPointId));
    }
}
