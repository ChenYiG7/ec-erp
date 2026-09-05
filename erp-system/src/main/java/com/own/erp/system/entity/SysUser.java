package com.own.erp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 系统用户(sys_user)。
 *     密码为 BCrypt 哈希,加解密入口在 SysUserService(创建/重置/本人改密),见 TODO.md #1(已完成)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名,唯一 */
    private String username;

    /** BCrypt 哈希,只经 SysUserService 写入;任何返回路径须清空本字段 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 1=启用 0=禁用 */
    private Integer status;

    /** created_at/updated_at 由数据库 DEFAULT CURRENT_TIMESTAMP / ON UPDATE 维护,实体不填 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
