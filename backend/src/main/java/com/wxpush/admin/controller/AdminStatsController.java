package com.wxpush.admin.controller;

import com.wxpush.admin.dto.ApiResponse;
import com.wxpush.admin.dto.OverviewVO;
import com.wxpush.admin.service.AdminStatsService;
import com.wxpush.repository.dto.DailyCount;
import com.wxpush.repository.dto.TypeCount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 —— 数据看板统计接口。
 *
 * <p>三个接口返回的数据结构都是直接喂给 ECharts 的形态，
 * 前端不需要再做一次「后端字段 → 图表字段」的翻译。</p>
 */
@RestController
@RequestMapping("/api/stats")
public class AdminStatsController {

    private final AdminStatsService statsService;

    public AdminStatsController(AdminStatsService statsService) {
        this.statsService = statsService;
    }

    /** 概览指标：总数 / 今日 / 关注 / 取关 / 净增 / 活跃用户 */
    @GetMapping("/overview")
    public ApiResponse<OverviewVO> overview() {
        return ApiResponse.ok(statsService.overview());
    }

    /**
     * 按天趋势（折线图）。缺失的日期已在 Service 层补 0，前端直接画即可。
     *
     * @param days 统计最近多少天（含今天），默认 7，上限 90
     */
    @GetMapping("/trend")
    public ApiResponse<List<DailyCount>> trend(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(statsService.trend(days));
    }

    /** 消息类型分布（饼图）。返回的 {@code name} / {@code value} 就是 ECharts 的标准字段 */
    @GetMapping("/type-distribution")
    public ApiResponse<List<TypeCount>> typeDistribution() {
        return ApiResponse.ok(statsService.typeDistribution());
    }
}
