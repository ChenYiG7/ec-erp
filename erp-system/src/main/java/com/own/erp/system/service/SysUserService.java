package com.own.erp.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysUser;
import com.own.erp.system.mapper.SysUserMapper;
import com.own.erp.system.mapper.SysUserRoleMapper;
import com.own.erp.system.request.command.SysUserSaveRequest;
import com.own.erp.system.request.query.SysUserQuery;
import com.own.erp.system.response.SysUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 用户服务(TODO#1):创建(BCrypt 加密)/改密/删除。
 *     API 模型收口(docs/07 §1):读入参 query/SysUserQuery、写入参 command/SysUserSaveRequest,
 *     出参 response/SysUserResponse(password 无字段即物理隔离),entity 不出本层。
 *     注:MyBatis-Plus 3.5.9+ 已移除旧 IService/ServiceImpl,本项目统一用
 *     "Mapper 做通用 CRUD + Service 只装业务逻辑" 的写法,不受 MP 版本演进影响
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    /** sys_user.status:1 启用 */
    private static final int STATUS_ENABLED = 1;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /** 创建用户:用户名唯一 + 密码 BCrypt 加密落库(禁明文) */
    public Long createUser(SysUserSaveRequest request) {
        if (StrUtil.isBlank(request.password())) {
            throw new BusinessException("初始密码不能为空");
        }
        checkUsernameUnique(request.username(), null);
        SysUser user = request.toEntity();
        user.setPassword(passwordEncoder.encode(request.password()));
        userMapper.insert(user);
        return user.getId();
    }

    /** 更新用户基础信息:改用户名时校验唯一;password 不在 toEntity 映射内,只能走专用改密方法 */
    public void updateUser(Long id, SysUserSaveRequest request) {
        checkUsernameUnique(request.username(), id);
        SysUser user = request.toEntity();
        user.setId(id);
        userMapper.updateById(user);
    }

    /** 分页查询(用户域整域走 Service):出参走 SysUserResponse,password 无出参字段即物理隔离 */
    public Page<SysUserResponse> pageUsers(SysUserQuery query) {
        Page<SysUser> result = userMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<SysUser>()
                        .like(StrUtil.isNotBlank(query.getUsername()), SysUser::getUsername, query.getUsername())
                        .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                        .orderByDesc(SysUser::getId));
        Page<SysUserResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(SysUserResponse::from).toList());
        return responsePage;
    }

    /** 详情查询;不存在返回 null(沿用原语义) */
    public SysUserResponse getUserById(Long id) {
        SysUser user = userMapper.selectById(id);
        return user == null ? null : SysUserResponse.from(user);
    }

    /** 重置密码(管理员操作):直接覆盖为新密码哈希 */
    public void resetPassword(Long userId, String newPassword) {
        checkNewPassword(newPassword);
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    /** 本人改密:校验原密码后再覆盖 */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        checkNewPassword(newPassword);
        SysUser user = userMapper.selectById(userId);
        if (user == null || !passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(401, "原密码错误");
        }
        resetPassword(userId, newPassword);
    }

    /** 删除用户:同事务清理用户-角色绑定,防孤儿关联 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        userMapper.deleteById(userId);
        userRoleMapper.deleteByUserId(userId);
    }

    /** 启用用户ID列表(#14 站内通知扇出等系统广播场景用);不改密码等敏感列,全列读取即可 */
    public List<Long> listEnabledUserIds() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, STATUS_ENABLED))
                .stream()
                .map(SysUser::getId)
                .toList();
    }

    /** 启用用户邮箱列表(#14 邮件渠道收件人扇出):去空白去重;邮箱未填的用户自然不出现在收件人里 */
    public List<String> listEnabledUserEmails() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, STATUS_ENABLED)
                        .isNotNull(SysUser::getEmail))
                .stream()
                .map(SysUser::getEmail)
                .map(StrUtil::trimToNull)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在: " + username);
        }
    }

    private void checkNewPassword(String newPassword) {
        if (StrUtil.isBlank(newPassword)) {
            throw new BusinessException("新密码不能为空");
        }
    }
}
