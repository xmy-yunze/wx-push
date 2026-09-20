package com.wxpush.admin;

import com.wxpush.admin.controller.AdminMessageController;
import com.wxpush.admin.controller.AdminStatsController;
import com.wxpush.admin.service.AdminMessageService;
import com.wxpush.admin.service.AdminStatsService;
import com.wxpush.admin.support.GlobalExceptionHandler;
import com.wxpush.repository.dto.DailyCount;
import com.wxpush.repository.dto.TypeCount;
import com.wxpush.repository.entity.WxMessageLog;
import com.wxpush.repository.mapper.WxMessageLogMapper;
import com.wxpush.repository.mapper.WxMessageStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * admin 接口的 HTTP 契约测试 —— 守住「前后端约定」不被悄悄改坏。
 *
 * <p>测的是<b>契约本身</b>：路径对不对、响应外壳字段名对不对、
 * 时间和 null 的序列化形态对不对。这些一旦改了，前端就会静默拿到 undefined，
 * 所以必须由测试盯住。</p>
 *
 * <p>用 standalone 模式的 MockMvc：不需要启动 Spring 容器、不需要数据库，
 * 只装配这三个 controller 和全局异常处理器，跑起来是毫秒级的。</p>
 */
@DisplayName("admin 接口契约")
class AdminApiContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AdminMessageController(new AdminMessageService(new StubMessageMapper())),
                        new AdminStatsController(new AdminStatsService(new StubStatsMapper())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 消息列表 ====================

    @Test
    @DisplayName("GET /api/messages → 统一响应外壳，code=0 且 message=ok")
    void list_响应外壳() throws Exception {
        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /api/messages → 分页字段名为 total / page / size / list")
    void list_分页字段名() throws Exception {
        mockMvc.perform(get("/api/messages"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @DisplayName("GET /api/messages → 列表元素含全部 8 个字段")
    void list_元素字段齐全() throws Exception {
        mockMvc.perform(get("/api/messages"))
                .andExpect(jsonPath("$.data.list[0].id").value(1))
                .andExpect(jsonPath("$.data.list[0].msgId").value("1234567890123456"))
                .andExpect(jsonPath("$.data.list[0].fromUser").value("oUserOpenid"))
                .andExpect(jsonPath("$.data.list[0].toUser").value("gh_abc123"))
                .andExpect(jsonPath("$.data.list[0].msgType").value("text"))
                .andExpect(jsonPath("$.data.list[0].event").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].content").value("你好"));
    }

    @Test
    @DisplayName("GET /api/messages → 时间序列化为 yyyy-MM-dd HH:mm:ss（中间是空格不是 T）")
    void list_时间格式() throws Exception {
        mockMvc.perform(get("/api/messages"))
                .andExpect(jsonPath("$.data.list[0].createdAt").value("2026-09-20 21:55:09"));
    }

    @Test
    @DisplayName("GET /api/messages?page=0&size=9999 → 后端静默校正，不报错")
    void list_非法分页参数被校正() throws Exception {
        mockMvc.perform(get("/api/messages").param("page", "0").param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    // ==================== 消息详情 ====================

    @Test
    @DisplayName("GET /api/messages/1 → 返回裸对象（不是分页结构）")
    void detail_返回裸对象() throws Exception {
        mockMvc.perform(get("/api/messages/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.content").value("你好"));
    }

    @Test
    @DisplayName("GET /api/messages/999 → HTTP 404 且 code=404，message 含主键")
    void detail_不存在时返回404() throws Exception {
        mockMvc.perform(get("/api/messages/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message", containsString("999")));
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("GET /api/stats/overview → 6 个指标字段齐全")
    void overview_字段齐全() throws Exception {
        mockMvc.perform(get("/api/stats/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalMessages").value(5))
                .andExpect(jsonPath("$.data.todayMessages").value(5))
                .andExpect(jsonPath("$.data.totalSubscribe").value(2))
                .andExpect(jsonPath("$.data.totalUnsubscribe").value(2))
                .andExpect(jsonPath("$.data.netGrowth").value(0))
                .andExpect(jsonPath("$.data.activeUsers").value(1));
    }

    @Test
    @DisplayName("GET /api/stats/trend?days=7 → 返回 7 个点，字段为 date/total/subscribe/unsubscribe")
    void trend_字段与补零() throws Exception {
        mockMvc.perform(get("/api/stats/trend").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[0].date").exists())
                .andExpect(jsonPath("$.data[0].total").exists())
                .andExpect(jsonPath("$.data[0].subscribe").exists())
                .andExpect(jsonPath("$.data[0].unsubscribe").exists());
    }

    @Test
    @DisplayName("GET /api/stats/type-distribution → 字段名为 name / value（可直接喂 ECharts）")
    void typeDistribution_字段可直喂图表() throws Exception {
        mockMvc.perform(get("/api/stats/type-distribution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("event"))
                .andExpect(jsonPath("$.data[0].value").value(4));
    }

    // ==================== 测试替身 ====================

    /** 与数据库第 1 行真实数据一致的固定返回 */
    private static class StubMessageMapper implements WxMessageLogMapper {

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

        @Override
        public int insertIgnore(WxMessageLog entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WxMessageLog selectByMsgId(String msgId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WxMessageLog selectById(Long id) {
            return id != null && id == 1L ? sample() : null;
        }

        @Override
        public List<WxMessageLog> selectPage(String msgType, String event, LocalDateTime startTime,
                                             LocalDateTime endTime, String keyword, int offset, int limit) {
            return List.of(sample());
        }

        @Override
        public long countByCondition(String msgType, String event, LocalDateTime startTime,
                                     LocalDateTime endTime, String keyword) {
            return 1L;
        }
    }

    /** 与数据库当前 5 行真实数据口径一致的固定返回 */
    private static class StubStatsMapper implements WxMessageStatsMapper {

        @Override
        public long countAll() {
            return 5L;
        }

        @Override
        public long countSince(LocalDateTime since) {
            return 5L;
        }

        @Override
        public long countByEvent(String event) {
            return 2L;
        }

        @Override
        public long countDistinctUsers() {
            return 1L;
        }

        @Override
        public List<DailyCount> selectDailyCount(LocalDate startDate, LocalDate endDate) {
            return List.of();
        }

        @Override
        public List<TypeCount> selectTypeDistribution() {
            TypeCount event = new TypeCount();
            event.setName("event");
            event.setValue(4L);
            return List.of(event);
        }
    }
}
