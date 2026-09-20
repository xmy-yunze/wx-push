package com.wxpush.admin;

import com.wxpush.admin.dto.AdminMessageQuery;
import com.wxpush.admin.dto.MessageVO;
import com.wxpush.admin.dto.OverviewVO;
import com.wxpush.admin.dto.PageResult;
import com.wxpush.admin.service.AdminMessageService;
import com.wxpush.admin.service.AdminStatsService;
import com.wxpush.admin.support.ResourceNotFoundException;
import com.wxpush.repository.dto.DailyCount;
import com.wxpush.repository.dto.TypeCount;
import com.wxpush.repository.entity.WxMessageLog;
import com.wxpush.repository.mapper.WxMessageLogMapper;
import com.wxpush.repository.mapper.WxMessageStatsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * admin 服务层的单元测试。
 *
 * <p>刻意<b>不用 Mockito</b>：这两个 Service 的依赖都是自己写的接口，
 * 手写一个「记账用的假 Mapper」既能返回预设数据，又能把 Service 实际传下来的参数记下来，
 * 反过来断言「Service 到底算了什么」—— 这比 mock 的 verify 更直观，也不用多引一个依赖。</p>
 *
 * <p>这些测试<b>完全不碰数据库</b>，所以任何环境都能跑。</p>
 */
@DisplayName("admin 服务层")
class AdminServiceTest {

    // ==================== AdminMessageService ====================

    @Test
    @DisplayName("页码小于 1 时校正为 1，offset 归零")
    void page_页码非法时校正为1() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        AdminMessageService service = new AdminMessageService(mapper);

        AdminMessageQuery query = new AdminMessageQuery();
        query.setPage(0);
        query.setSize(20);

        service.page(query);

        assertEquals(0, mapper.capturedOffset, "页码 0 应被校正为 1，offset = (1-1)*20 = 0");
    }

    @Test
    @DisplayName("每页条数超上限时截断为 100")
    void page_每页条数超上限时截断() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        AdminMessageService service = new AdminMessageService(mapper);

        AdminMessageQuery query = new AdminMessageQuery();
        query.setPage(1);
        query.setSize(9999);

        service.page(query);

        assertEquals(100, mapper.capturedLimit, "size 应被截断到上限 100");
    }

    @Test
    @DisplayName("每页条数为 0 或负数时回落到默认值 20")
    void page_每页条数非法时回落默认值() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        AdminMessageService service = new AdminMessageService(mapper);

        AdminMessageQuery query = new AdminMessageQuery();
        query.setPage(1);
        query.setSize(-5);

        service.page(query);

        assertEquals(20, mapper.capturedLimit);
    }

    @Test
    @DisplayName("offset 按 (page-1)*size 计算")
    void page_计算offset() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        AdminMessageService service = new AdminMessageService(mapper);

        AdminMessageQuery query = new AdminMessageQuery();
        query.setPage(3);
        query.setSize(15);

        service.page(query);

        assertEquals(30, mapper.capturedOffset, "(3-1)*15 = 30");
    }

    @Test
    @DisplayName("结束日期左闭右开：endDate 当天要算进去，所以 +1 天")
    void page_结束日期多算一天() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        AdminMessageService service = new AdminMessageService(mapper);

        AdminMessageQuery query = new AdminMessageQuery();
        query.setStartDate(LocalDate.of(2026, 9, 20));
        query.setEndDate(LocalDate.of(2026, 9, 20));

        service.page(query);

        assertEquals(LocalDateTime.of(2026, 9, 20, 0, 0), mapper.capturedStartTime);
        assertEquals(LocalDateTime.of(2026, 9, 21, 0, 0), mapper.capturedEndTime,
                "结束日期是 9-20，区间上界应是 9-21 00:00，否则当天的数据全被漏掉");
    }

    @Test
    @DisplayName("不传日期时时间条件为 null（SQL 里不拼该条件）")
    void page_不传日期时时间为null() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        AdminMessageService service = new AdminMessageService(mapper);

        service.page(new AdminMessageQuery());

        assertNull(mapper.capturedStartTime);
        assertNull(mapper.capturedEndTime);
    }

    @Test
    @DisplayName("总数为 0 时不再查列表（省掉一次无意义查询）")
    void page_总数为0时跳过列表查询() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        mapper.totalToReturn = 0L;
        AdminMessageService service = new AdminMessageService(mapper);

        PageResult<MessageVO> result = service.page(new AdminMessageQuery());

        assertEquals(0, result.total());
        assertTrue(result.list().isEmpty());
        assertFalse(mapper.selectPageCalled, "总数为 0 时不应再执行 selectPage");
    }

    @Test
    @DisplayName("分页结果把实体的字段完整映射到 VO")
    void page_实体字段映射到VO() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        AdminMessageService service = new AdminMessageService(mapper);

        MessageVO vo = service.page(new AdminMessageQuery()).list().get(0);

        assertEquals(1L, vo.id());
        assertEquals("1234567890123456", vo.msgId());
        assertEquals("oUserOpenid", vo.fromUser());
        assertEquals("gh_abc123", vo.toUser());
        assertEquals("text", vo.msgType());
        assertNull(vo.event());
        assertEquals("你好", vo.content());
        assertEquals(LocalDateTime.of(2026, 9, 20, 21, 55, 9), vo.createdAt());
    }

    @Test
    @DisplayName("详情：主键不存在时抛 ResourceNotFoundException")
    void detail_不存在时抛异常() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        AdminMessageService service = new AdminMessageService(mapper);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.detail(999L));
        assertTrue(ex.getMessage().contains("999"));
    }

    @Test
    @DisplayName("详情：主键存在时返回单个对象")
    void detail_存在时返回对象() {
        CapturingMessageMapper mapper = new CapturingMessageMapper();
        AdminMessageService service = new AdminMessageService(mapper);

        MessageVO vo = service.detail(1L);

        assertEquals(1L, vo.id());
    }

    // ==================== AdminStatsService ====================

    @Test
    @DisplayName("概览：净增关注 = 关注 − 取关，可以为负")
    void overview_净增可能为负() {
        FakeStatsMapper mapper = new FakeStatsMapper();
        mapper.total = 10;
        mapper.today = 3;
        mapper.subscribe = 2;
        mapper.unsubscribe = 5;   // 取关多于关注
        mapper.users = 4;

        OverviewVO vo = new AdminStatsService(mapper).overview();

        assertEquals(10, vo.totalMessages());
        assertEquals(3, vo.todayMessages());
        assertEquals(2, vo.totalSubscribe());
        assertEquals(5, vo.totalUnsubscribe());
        assertEquals(-3, vo.netGrowth(), "2 - 5 应为 -3");
        assertEquals(4, vo.activeUsers());
    }

    @Test
    @DisplayName("趋势：缺失的日期要补 0，且天数严格等于请求值")
    void trend_缺失日期补0() {
        FakeStatsMapper mapper = new FakeStatsMapper();
        // 只返回「今天」一天的数据，其余 6 天缺失
        mapper.daily = List.of(day(LocalDate.now().toString(), 5L, 2L, 2L));

        List<DailyCount> result = new AdminStatsService(mapper).trend(7);

        assertEquals(7, result.size(), "请求 7 天就必须返回 7 个点，否则折线图时间轴会被压缩");
        assertEquals(LocalDate.now().toString(), result.get(6).getDate(), "最后一个点是今天");
        assertEquals(5L, result.get(6).getTotal());
        assertEquals(0L, result.get(0).getTotal(), "无数据的日期应补 0");
        assertEquals(0L, result.get(0).getSubscribe());
        assertEquals(0L, result.get(0).getUnsubscribe());
    }

    @Test
    @DisplayName("趋势：日期按升序连续排列")
    void trend_日期升序连续() {
        FakeStatsMapper mapper = new FakeStatsMapper();
        AdminStatsService service = new AdminStatsService(mapper);

        List<DailyCount> result = service.trend(7);

        LocalDate start = LocalDate.now().minusDays(6);
        for (int i = 0; i < 7; i++) {
            assertEquals(start.plusDays(i).toString(), result.get(i).getDate());
        }
    }

    @Test
    @DisplayName("趋势：天数非法时用默认值 7，超上限时截断为 90")
    void trend_天数边界校正() {
        FakeStatsMapper mapper = new FakeStatsMapper();
        AdminStatsService service = new AdminStatsService(mapper);

        assertEquals(7, service.trend(0).size(), "0 应回落默认 7 天");
        assertEquals(7, service.trend(-3).size());
        assertEquals(90, service.trend(9999).size(), "超上限应截断为 90 天");
    }

    @Test
    @DisplayName("类型分布：原样透传 Mapper 结果")
    void typeDistribution_透传() {
        FakeStatsMapper mapper = new FakeStatsMapper();
        mapper.types = List.of(type("event", 4L), type("text", 1L));

        List<TypeCount> result = new AdminStatsService(mapper).typeDistribution();

        assertEquals(2, result.size());
        assertEquals("event", result.get(0).getName());
        assertEquals(4L, result.get(0).getValue());
    }

    // ==================== 测试替身 ====================

    private static DailyCount day(String date, Long total, Long subscribe, Long unsubscribe) {
        DailyCount d = new DailyCount();
        d.setDate(date);
        d.setTotal(total);
        d.setSubscribe(subscribe);
        d.setUnsubscribe(unsubscribe);
        return d;
    }

    private static TypeCount type(String name, Long value) {
        TypeCount t = new TypeCount();
        t.setName(name);
        t.setValue(value);
        return t;
    }

    /** 「记账型」假 Mapper：返回预设数据，同时把 Service 传下来的参数记录下来供断言 */
    private static class CapturingMessageMapper implements WxMessageLogMapper {

        long totalToReturn = 1L;
        boolean selectPageCalled = false;
        int capturedOffset = -1;
        int capturedLimit = -1;
        LocalDateTime capturedStartTime;
        LocalDateTime capturedEndTime;

        @Override
        public int insertIgnore(WxMessageLog entity) {
            throw new UnsupportedOperationException("admin 测试不涉及写入");
        }

        @Override
        public WxMessageLog selectByMsgId(String msgId) {
            throw new UnsupportedOperationException("admin 测试不涉及该查询");
        }

        @Override
        public WxMessageLog selectById(Long id) {
            return id != null && id == 1L ? sample() : null;
        }

        @Override
        public List<WxMessageLog> selectPage(String msgType, String event, LocalDateTime startTime,
                                             LocalDateTime endTime, String keyword,
                                             int offset, int limit) {
            selectPageCalled = true;
            capturedOffset = offset;
            capturedLimit = limit;
            capturedStartTime = startTime;
            capturedEndTime = endTime;
            return new ArrayList<>(List.of(sample()));
        }

        @Override
        public long countByCondition(String msgType, String event, LocalDateTime startTime,
                                     LocalDateTime endTime, String keyword) {
            capturedStartTime = startTime;
            capturedEndTime = endTime;
            return totalToReturn;
        }

        /** 与数据库里第 1 行真实数据保持一致，便于肉眼对照 */
        private static WxMessageLog sample() {
            return WxMessageLog.builder()
                    .id(1L)
                    .msgId("1234567890123456")
                    .fromUser("oUserOpenid")
                    .toUser("gh_abc123")
                    .msgType("text")
                    .event(null)
                    .content("你好")
                    .createdAt(LocalDateTime.of(2026, 9, 20, 21, 55, 9))
                    .build();
        }
    }

    /** 假的统计 Mapper：所有查询返回构造时预设的值 */
    private static class FakeStatsMapper implements WxMessageStatsMapper {

        long total = 0;
        long today = 0;
        long subscribe = 0;
        long unsubscribe = 0;
        long users = 0;
        List<DailyCount> daily = List.of();
        List<TypeCount> types = List.of();

        @Override
        public long countAll() {
            return total;
        }

        @Override
        public long countSince(LocalDateTime since) {
            return today;
        }

        @Override
        public long countByEvent(String event) {
            return "subscribe".equals(event) ? subscribe : unsubscribe;
        }

        @Override
        public long countDistinctUsers() {
            return users;
        }

        @Override
        public List<DailyCount> selectDailyCount(LocalDate startDate, LocalDate endDate) {
            return daily;
        }

        @Override
        public List<TypeCount> selectTypeDistribution() {
            return types;
        }
    }
}
