package com.own.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.PageQuery;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysRole;
import com.own.erp.system.mapper.SysRoleMapper;
import com.own.erp.system.mapper.SysRoleMenuMapper;
import com.own.erp.system.mapper.SysUserRoleMapper;
import com.own.erp.system.request.command.SysRoleSaveRequest;
import com.own.erp.system.response.SysRoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 角色服务(TODO#1 RBAC):CRUD + 删角色前置校验与绑定清理。
 *     角色域整域收口(2026-09-03)——域内已有校验规则,全部接口走 Service,Controller 不直连 Mapper(docs/07 §2.1);
 *     API 模型收口(docs/07 §1):写入参 command/SysRoleSaveRequest,出参 response/SysRoleResponse,entity 不出本层
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    /** 分页查询(按 id 升序);无过滤条件,分页参数直接用 PageQuery */
    public Page<SysRoleResponse> pageRoles(PageQuery query) {
        Page<SysRole> result = roleMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
        Page<SysRoleResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(SysRoleResponse::from).toList());
        return responsePage;
    }

    /** 新增角色 */
    public Long createRole(SysRoleSaveRequest request) {
        SysRole role = request.toEntity();
        roleMapper.insert(role);
        return role.getId();
    }

    /** 更新角色(MP 忽略 null 可部分更新) */
    public void updateRole(Long id, SysRoleSaveRequest request) {
        SysRole role = request.toEntity();
        role.setId(id);
        roleMapper.updateById(role);
    }

    /** 删除角色:仍绑定用户则拒绝;同事务清理角色-菜单绑定 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        Long boundUsers = userRoleMapper.countByRoleId(roleId);
        if (boundUsers != null && boundUsers > 0) {
            throw new BusinessException("角色仍绑定 " + boundUsers + " 个用户,请先解绑后再删除");
        }
        roleMapper.deleteById(roleId);
        roleMenuMapper.deleteByRoleId(roleId);
    }
}
