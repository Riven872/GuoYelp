package com.Guo.GuoYelp.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页返回值
 */
@Data
public class ScrollResult {
    private List<?> list;//要返回的数据
    private Long minTime;//最小时间
    private Integer offset;//偏移量
}
