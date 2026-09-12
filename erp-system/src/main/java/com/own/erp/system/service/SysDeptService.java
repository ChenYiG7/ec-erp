package com.own.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.entity.SysDept;
import com.own.erp.system.entity.SysUser;
import com.own.erp.system.mapper.SysDeptMapper;
import com.own.erp.system.mapper.SysUserMapper;
import com.own.erp.system.request.command.SysDeptSaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 部门服务(#27③):树组装 + CRUD(部门域整域收口——树组装属业务逻辑,
 *     Controller 不直连 Mapper,docs/07 §2.1)。API 模型收口(docs/07 §1):入参 command/SysDeptSaveRequest,
 *     出参 DeptNode(树节点,record),entity 不出本层。一次查全量内存组树,部门量级下足够。
 *     成环/删除引用校验仿 erp-goods 分类先例(#7 收口形态);数据权限(sys_user_shop 店铺轴)归 #27 第三会话
 */
@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    /** 部门树节点(对外出参):实体字段裁剪 + children 装配;record(docs/07 §1 模型可变性分级),叶子 children 为空列表 */
    public record DeptNode(Long id, Long parentId, String deptName, Integer sort, Integer status,
                           List<DeptNode> children) {
    }

    /** 全量部门树(按 sort 升序,parent_id=0 为根;含禁用节点,管理端用) */
    public List<DeptNode> tree() {
        List<SysDept> all = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSort));
        return buildTree(all, 0L);
    }

    /** 内存引用组树:子节点在父的 children 里递归装配;数据量级小,牺牲递归换可读 */
    private List<DeptNode> buildTree(List<SysDept> all, Long parentId) {
        List<DeptNode> nodes = new ArrayList<>();
        for (SysDept d : all) {
            if (parentId.equals(d.getParentId())) {
                nodes.add(new DeptNode(d.getId(), d.getParentId(), d.getDeptName(), d.getSort(), d.getStatus(),
                        buildTree(all, d.getId())));
            }
        }
        return nodes;
    }

    /** 新增部门:parentId 未传按根(parent_id=0)处理;非根父部门必须存在(防孤儿节点) */
    public Long create(SysDeptSaveRequest request) {
        SysDept dept = request.toEntity();
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        requireParentUsable(null, dept.getParentId());
        deptMapper.insert(dept);
        return dept.getId();
    }

    /**
     * 更新部门(MP 忽略 null 可部分更新):parentId 变更时父部门必须存在,
     * 且不得挂到自己或自己的子孙部门下(成环)
     */
    public void update(Long id, SysDeptSaveRequest request) {
        requireParentUsable(id, request.parentId());
        SysDept dept = request.toEntity();
        dept.setId(id);
        deptMapper.updateById(dept);
    }

    /**
     * 父部门可用性:根(0)放行;非根沿父链逐级上走——链上出现 excludeId 即成环(自己/自己子孙禁挂),
     * 部门不存在即拒;visited 集合兼防存量脏数据已成环时死循环。纯查询,部门量级小开销可忽略
     */
    private void requireParentUsable(Long excludeId, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0L) {
            if (!visited.add(cursor)) {
                throw new BusinessException("父部门链已存在环,请先修复部门数据");
            }
            if (cursor.equals(excludeId)) {
                throw new BusinessException("不能把部门挂到自己或自己的子孙部门下");
            }
            SysDept parent = deptMapper.selectById(cursor);
            if (parent == null) {
                throw new BusinessException("父部门不存在: " + cursor);
            }
            cursor = parent.getParentId();
        }
    }

    /**
     * 删除部门:有子部门或被用户引用(sys_user.dept_id)时禁止删除,先迁移子部门/用户
     */
    public void delete(Long id) {
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该部门下有 " + childCount + " 个子部门,禁删,先迁移子部门");
        }
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id));
        if (userCount != null && userCount > 0) {
            throw new BusinessException("该部门下挂有 " + userCount + " 个用户,禁删,先迁移用户归属");
        }
        deptMapper.deleteById(id);
    }
}
