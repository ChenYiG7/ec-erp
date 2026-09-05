package com.own.erp.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.warehouse.entity.Warehouse;
import com.own.erp.warehouse.mapper.WarehouseMapper;
import com.own.erp.warehouse.request.query.WarehouseQuery;
import com.own.erp.warehouse.request.command.WarehouseSaveRequest;
import com.own.erp.warehouse.response.WarehouseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 仓库服务:warehouse 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出本层
 *     #7 收口(2026-09-04):删除前引用拦截(库存/采购,经 erp-contract WarehouseApi,实现收口 erp-api);
 *     名称唯一性未做(warehouse 无业务唯一键列,同 supplier 需业务确认后改表,TODO.md #7)
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseMapper warehouseMapper;
    private final WarehouseApi warehouseApi;

    /** 分页查询(默认按 id 倒序;过滤条件在 WarehouseQuery 加字段后在此补 Wrapper 条件) */
    public Page<WarehouseResponse> page(WarehouseQuery query) {
        Page<Warehouse> result = warehouseMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<Warehouse>().orderByDesc(Warehouse::getId));
        Page<WarehouseResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(WarehouseResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public WarehouseResponse getById(Long id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        return warehouse == null ? null : WarehouseResponse.from(warehouse);
    }

    /**
     * 仓库存在性(#10 经 erp-contract WarehouseApi 暴露):采购单/入库单引用 warehouse_id 前校验,
     * 防错误仓库ID经库存自动建行产出幻影库存(调用侧:erp-api WarehouseApiImpl)
     */
    public boolean existsWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return false;
        }
        Long count = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getId, warehouseId));
        return count != null && count > 0;
    }

    /** 新增,返回自增ID */
    public Long save(WarehouseSaveRequest request) {
        Warehouse warehouse = request.toEntity();
        warehouseMapper.insert(warehouse);
        return warehouse.getId();
    }

    /** 更新(MP 忽略 null 可部分更新;id 只认路径参数) */
    public void update(Long id, WarehouseSaveRequest request) {
        Warehouse warehouse = request.toEntity();
        warehouse.setId(id);
        warehouseMapper.updateById(warehouse);
    }

    /**
     * 删除(一期硬删)。#7 收口(2026-09-04,同 #10 遗留条目):库存/采购单任一引用即禁删——
     * 库存行有数量轨迹、采购单是业务凭证,删仓致账实无法追溯,引导改状态停用;
     * 计数经 erp-contract WarehouseApi(实现收口 erp-api,本域禁横向依赖 inventory/purchase,铁律 2)
     */
    public void delete(Long id) {
        long refs = warehouseApi.countWarehouseRefs(id);
        if (refs > 0) {
            throw new BusinessException("仓库被库存/采购单引用共 " + refs + " 条,禁删,可改状态停用");
        }
        warehouseMapper.deleteById(id);
    }
}
