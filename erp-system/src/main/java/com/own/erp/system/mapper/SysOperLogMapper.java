package com.own.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.system.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 操作审计日志 Mapper(#27②):仅内建 insert/selectPage,无自定义 SQL
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
