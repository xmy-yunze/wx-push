package com.wxpush.admin.dto;

/**
 * 数据看板顶部概览卡片的数据。
 *
 * <p>一次请求把看板需要的总量指标全带回去，避免前端为了 6 个数字发 6 个请求。</p>
 */
public record OverviewVO(
        /** 消息总条数 */
        long totalMessages,
        /** 今日消息条数 */
        long todayMessages,
        /** 累计关注（subscribe）次数 */
        long totalSubscribe,
        /** 累计取关（unsubscribe）次数 */
        long totalUnsubscribe,
        /** 净增关注 = 累计关注 - 累计取关（可能为负） */
        long netGrowth,
        /** 去重后的活跃用户数（按 openid 去重） */
        long activeUsers) {
}
