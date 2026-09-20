package com.wxpush.admin.service;

import com.wxpush.admin.dto.AdminMessageQuery;
import com.wxpush.admin.dto.MessageVO;
import com.wxpush.admin.dto.PageResult;
import com.wxpush.admin.support.ResourceNotFoundException;
import com.wxpush.repository.entity.WxMessageLog;
import com.wxpush.repository.mapper.WxMessageLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * admin 消息查询服务。
 *
 * <p>职责边界：<b>只做「入参校正 + 组装结果」</b>。SQL 交给 Mapper，
 * HTTP 细节交给 Controller，谁也不越界。</p>
 */
@Service
public class AdminMessageService {

    /** 每页默认条数 */
    private static final int DEFAULT_SIZE = 20;

    /** 每页条数上限 —— 不设上限的话，前端一个 size=999999 就能把整张表拉出来 */
    private static final int MAX_SIZE = 100;

    private final WxMessageLogMapper messageLogMapper;

    public AdminMessageService(WxMessageLogMapper messageLogMapper) {
        this.messageLogMapper = messageLogMapper;
    }

    /**
     * 条件分页查询。
     *
     * <p>入参校正全部集中在方法开头，这样后面两个 SQL 用的是同一套「干净」条件，
     * 不会出现列表按 size=100 查、计数按 size=0 算这种自相矛盾。</p>
     */
    public PageResult<MessageVO> page(AdminMessageQuery query) {
        int page = Math.max(query.getPage(), 1);
        int size = query.getSize() <= 0 ? DEFAULT_SIZE : Math.min(query.getSize(), MAX_SIZE);

        // 日期 → 时间区间。结束日期要 +1 天并取左闭右开，
        // 否则「9月20日」当天 00:00 之后的数据会全部被漏掉。
        LocalDateTime startTime = query.getStartDate() == null
                ? null : query.getStartDate().atStartOfDay();
        LocalDateTime endTime = query.getEndDate() == null
                ? null : query.getEndDate().plusDays(1).atStartOfDay();

        long total = messageLogMapper.countByCondition(
                query.getMsgType(), query.getEvent(), startTime, endTime, query.getKeyword());

        // 总数为 0 时直接返回空列表，省掉一次无意义的查询
        if (total == 0) {
            return new PageResult<>(0, page, size, List.of());
        }

        int offset = (page - 1) * size;
        List<MessageVO> list = messageLogMapper
                .selectPage(query.getMsgType(), query.getEvent(), startTime, endTime,
                        query.getKeyword(), offset, size)
                .stream()
                .map(AdminMessageService::toVO)
                .toList();

        return new PageResult<>(total, page, size, list);
    }

    /**
     * 消息详情。
     *
     * @throws ResourceNotFoundException 主键不存在时抛出，由全局处理器转成 404
     */
    public MessageVO detail(Long id) {
        WxMessageLog entity = messageLogMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("消息不存在：id=" + id);
        }
        return toVO(entity);
    }

    /** 实体 → 视图对象。字段一一对应，刻意不做字段裁剪，便于前端展示调试 */
    private static MessageVO toVO(WxMessageLog e) {
        return new MessageVO(
                e.getId(),
                e.getMsgId(),
                e.getFromUser(),
                e.getToUser(),
                e.getMsgType(),
                e.getEvent(),
                e.getContent(),
                e.getCreatedAt());
    }
}
