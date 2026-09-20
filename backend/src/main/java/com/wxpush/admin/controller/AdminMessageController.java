package com.wxpush.admin.controller;

import com.wxpush.admin.dto.AdminMessageQuery;
import com.wxpush.admin.dto.ApiResponse;
import com.wxpush.admin.dto.MessageVO;
import com.wxpush.admin.dto.PageResult;
import com.wxpush.admin.service.AdminMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 —— 消息记录接口。
 *
 * <p>与 {@code /wx} 的区别：这里是给<b>浏览器</b>用的 JSON 接口，
 * 运营者直连，不经过微信，所以可以放心用 {@code @RequestBody} / 对象映射这套常规玩法。</p>
 *
 * <p>Controller 只做「收参数 → 调 Service → 包响应」三件事，一行业务判断都不写。</p>
 */
@RestController
@RequestMapping("/api/messages")
public class AdminMessageController {

    private final AdminMessageService messageService;

    public AdminMessageController(AdminMessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 消息分页列表。
     *
     * <p>查询条件对象 {@link AdminMessageQuery} 由 Spring 从查询参数自动绑定；
     * 所有字段都是可选的，不传即「不限」。</p>
     *
     * <p>示例：{@code GET /api/messages?page=1&size=20&msgType=event&event=subscribe}</p>
     */
    @GetMapping
    public ApiResponse<PageResult<MessageVO>> page(AdminMessageQuery query) {
        return ApiResponse.ok(messageService.page(query));
    }

    /**
     * 消息详情。
     *
     * <p>主键不存在时 Service 抛 {@code ResourceNotFoundException}，
     * 由全局处理器统一转成 404 + 错误码 404。</p>
     */
    @GetMapping("/{id}")
    public ApiResponse<MessageVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(messageService.detail(id));
    }
}
