package com.own.erp.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.purchase.entity.Supplier;
import com.own.erp.purchase.mapper.SupplierMapper;
import com.own.erp.purchase.request.query.SupplierQuery;
import com.own.erp.purchase.request.command.SupplierSaveRequest;
import com.own.erp.purchase.response.SupplierResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 供应商服务:supplier 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出本层。
 *     名称唯一性(2026-09-07 拍板收口):友好校验在 Service(查重上抛业务异常),硬约束收口表 uk_name
 *     (并发窗口漏网由唯一键兜底);启用校验等随业务确认补齐
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierMapper supplierMapper;
    private final PurchaseOrderService purchaseOrderService;

    /** 分页查询(默认按 id 倒序;过滤条件在 SupplierQuery 加字段后在此补 Wrapper 条件) */
    public Page<SupplierResponse> page(SupplierQuery query) {
        Page<Supplier> result = supplierMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<Supplier>().orderByDesc(Supplier::getId));
        Page<SupplierResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(SupplierResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public SupplierResponse getById(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        return supplier == null ? null : SupplierResponse.from(supplier);
    }

    /** 新增,返回自增ID;名称查重(uk_name 硬约束兜底) */
    public Long save(SupplierSaveRequest request) {
        requireNameFree(request.name(), null);
        Supplier supplier = request.toEntity();
        supplierMapper.insert(supplier);
        return supplier.getId();
    }

    /** 更新(MP 忽略 null 可部分更新;id 只认路径参数);名称查重排除自身 */
    public void update(Long id, SupplierSaveRequest request) {
        requireNameFree(request.name(), id);
        Supplier supplier = request.toEntity();
        supplier.setId(id);
        supplierMapper.updateById(supplier);
    }

    /** 名称查重:name 非空才校验(部分更新允许不改名);excludeId 非空时排除自身(更新场景) */
    private void requireNameFree(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            return;
        }
        Long count = supplierMapper.selectCount(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getName, name)
                .ne(excludeId != null, Supplier::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("供应商名称已存在:" + name);
        }
    }

    /** 删除(一期硬删):已发生采购业务禁删(#10 删除校验),引导改状态停用 */
    public void delete(Long id) {
        Supplier exist = supplierMapper.selectById(id);
        if (exist == null) {
            return;
        }
        if (purchaseOrderService.countBySupplierId(id) > 0) {
            throw new BusinessException("供应商已存在采购单,禁删除,可改状态为禁用:" + id);
        }
        supplierMapper.deleteById(id);
    }
}
