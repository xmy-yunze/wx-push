package com.wxpush.admin.dto;

import java.util.List;

/**
 * 分页查询结果的统一结构。
 *
 * <p>前端分页组件需要「总数」和「当前页数据」两样东西，
 * 所以这里把 {@code total} 和 {@code list} 放在同一个对象里返回，
 * 避免前端为了拿总数再发一次请求。</p>
 *
 * @param total 满足条件的<b>总记录数</b>（不是当前页条数）
 * @param page  当前页码，从 1 开始
 * @param size  每页条数
 * @param list  当前页数据
 */
public record PageResult<T>(long total, int page, int size, List<T> list) {
}
