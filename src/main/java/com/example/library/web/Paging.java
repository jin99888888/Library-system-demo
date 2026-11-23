package com.example.library.web;

import java.util.function.IntFunction;

import org.springframework.data.domain.Page;

/**
 * 页码处理的公共工具：越界页码钳到最后一页（跳转输入框同样适用）。
 */
public final class Paging {

    private Paging() {
    }

    /**
     * 按请求页码取一页数据；页码超出范围时回到最后一页。
     *
     * @param loader 根据 0 基页码加载一页
     * @param requested 请求的 0 基页码（可以是负数或超大值）
     */
    public static <T> Page<T> fetch(IntFunction<Page<T>> loader, int requested) {
        Page<T> page = loader.apply(Math.max(requested, 0));
        int last = page.getTotalPages() - 1;
        if (last >= 0 && requested > last) {
            page = loader.apply(last);
        }
        return page;
    }
}
