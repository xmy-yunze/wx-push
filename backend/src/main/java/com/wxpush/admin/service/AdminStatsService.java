package com.wxpush.admin.service;

import com.wxpush.admin.dto.OverviewVO;
import com.wxpush.repository.dto.DailyCount;
import com.wxpush.repository.dto.TypeCount;
import com.wxpush.repository.mapper.WxMessageStatsMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * admin 统计服务，为数据看板提供指标。
 */
@Service
public class AdminStatsService {

    private static final int DEFAULT_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 90;

    /** 事件名常量 —— 散落的字符串字面量是拼错的重灾区 */
    private static final String EVENT_SUBSCRIBE = "subscribe";
    private static final String EVENT_UNSUBSCRIBE = "unsubscribe";

    private final WxMessageStatsMapper statsMapper;

    public AdminStatsService(WxMessageStatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    /** 概览卡片：一次查齐 6 个指标 */
    public OverviewVO overview() {
        long total = statsMapper.countAll();
        long today = statsMapper.countSince(LocalDate.now().atStartOfDay());
        long subscribe = statsMapper.countByEvent(EVENT_SUBSCRIBE);
        long unsubscribe = statsMapper.countByEvent(EVENT_UNSUBSCRIBE);
        long users = statsMapper.countDistinctUsers();

        return new OverviewVO(total, today, subscribe, unsubscribe, subscribe - unsubscribe, users);
    }

    /**
     * 按天趋势，用于折线图。
     *
     * <p><b>为什么要在这里补零？</b> SQL 的 {@code GROUP BY} 只会返回「有数据的日期」。
     * 如果 9月18日 没有任何消息，结果里就根本没有这一天，
     * ECharts 拿到会直接把这天的点跳过 —— 折线看起来是连着的，实际时间轴被压缩了，
     * 视觉上会严重失真。所以在 Service 层把缺失的日期补成 0。</p>
     *
     * @param days 统计最近多少天（含今天）
     */
    public List<DailyCount> trend(int days) {
        int span = days <= 0 ? DEFAULT_TREND_DAYS : Math.min(days, MAX_TREND_DAYS);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(span - 1L);

        Map<String, DailyCount> found = statsMapper.selectDailyCount(start, end).stream()
                .collect(Collectors.toMap(DailyCount::getDate, Function.identity()));

        List<DailyCount> result = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            String date = start.plusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            result.add(found.getOrDefault(date, emptyDay(date)));
        }
        return result;
    }

    /** 消息类型分布，用于饼图 */
    public List<TypeCount> typeDistribution() {
        return statsMapper.selectTypeDistribution();
    }

    private static DailyCount emptyDay(String date) {
        DailyCount day = new DailyCount();
        day.setDate(date);
        day.setTotal(0L);
        day.setSubscribe(0L);
        day.setUnsubscribe(0L);
        return day;
    }
}
