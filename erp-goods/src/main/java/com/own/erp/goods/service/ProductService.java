package com.own.erp.goods.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.GoodsReferenceApi;
import com.own.erp.goods.entity.Product;
import com.own.erp.goods.entity.ProductSku;
import com.own.erp.goods.mapper.ProductMapper;
import com.own.erp.goods.mapper.ProductSkuMapper;
import com.own.erp.goods.request.command.ProductSaveRequest;
import com.own.erp.goods.request.command.ProductSkuSaveRequest;
import com.own.erp.goods.request.query.ProductQuery;
import com.own.erp.goods.response.ProductResponse;
import com.own.erp.goods.response.ProductSkuResponse;
import com.own.erp.goods.response.SkuOptionResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品服务:SPU+SKU 同域逻辑(域内有业务规则即整域收口——Controller 不直连 Mapper,docs/07 §2.1)。
 *     API 模型收口(docs/07 §1):入参 query/ProductQuery、command/ProductSaveRequest 等,出参 response/*Response,
 *     entity 不出本层。SKU 与平台 seller_sku 的绑定/匹配是系统心脏(#5),逻辑会持续在本类生长
 */
@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final GoodsReferenceApi referenceApi;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service,急切装配成环(docs/07 §2.2) */
    public ProductService(ProductMapper productMapper, ProductSkuMapper skuMapper, @Lazy GoodsReferenceApi referenceApi) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.referenceApi = referenceApi;
    }

    /** 分页查询:keyword 模糊匹配商品名,categoryId 精确过滤 */
    public Page<ProductResponse> pageProducts(ProductQuery query) {
        Page<Product> result = productMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<Product>()
                        .like(StrUtil.isNotBlank(query.getKeyword()), Product::getName, query.getKeyword())
                        .eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId())
                        .orderByDesc(Product::getId));
        Page<ProductResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(ProductResponse::from).toList());
        return responsePage;
    }

    /** SPU 详情 + SKU 列表一次带回(双表组装属 Service;product 不存在时 product 为 null) */
    public Map<String, Object> getProductDetail(Long id) {
        List<ProductSkuResponse> skus = listSkusByProductId(id);
        Product product = productMapper.selectById(id);
        return Map.of("product", product == null ? null : ProductResponse.from(product), "skus", skus);
    }

    /** 更新 SPU 基础信息(MP 忽略 null 可部分更新) */
    public void updateProduct(Long id, ProductSaveRequest request) {
        Product product = request.toEntity();
        product.setId(id);
        productMapper.updateById(product);
    }

    /**
     * 删除 SPU:一期硬删。删除前引用校验先拦截(#5,2026-09-04 接口模块方案收口):
     * listing 绑定(shop_product.product_id)+ 子 SKU 的绑定/库存/订单/采购引用,任一存在即禁删;
     * 计数经 erp-contract 接口模块(实现收口 erp-api,本域禁横向依赖引用侧模块,铁律 2)
     */
    public void deleteProduct(Long id) {
        List<Long> skuIds = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getProductId, id))
                .stream().map(ProductSku::getId).toList();
        long refs = referenceApi.countSkuRefs(skuIds) + referenceApi.countProductListingRefs(List.of(id));
        if (refs > 0) {
            throw new BusinessException("SPU被listing或其SKU引用(绑定/库存/订单/采购)共 " + refs + " 条,禁删,先解除引用");
        }
        productMapper.deleteById(id);
    }

    /** 某 SPU 下的 SKU 列表 */
    public List<ProductSkuResponse> listSkusByProductId(Long productId) {
        return skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getProductId, productId))
                .stream().map(ProductSkuResponse::from).toList();
    }

    /**
     * 批量按 ID 查 SKU 选项(#7 专条 2026-09-06 收口,前端跨页 skuId 列翻译数据源):
     * 两步组装(product_sku → product 名称,双表组装属 Service,同 getProductDetail 口径);
     * selectByIds 走 BaseMapper 内建,规避 LambdaWrapper.in() 急切解析列元数据坑(docs/07 §10,单测可直测);
     * 查无的 ID 不在结果中,前端回落显示裸 ID;不过滤 status——禁用 SKU 的历史单据仍需翻译
     */
    public List<SkuOptionResponse> listSkuOptions(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        List<ProductSku> skus = skuMapper.selectByIds(ids);
        if (skus.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = skus.stream().map(ProductSku::getProductId).distinct().toList();
        Map<Long, Product> products = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        return skus.stream().map(sku -> SkuOptionResponse.from(sku, products.get(sku.getProductId()))).toList();
    }

    /**
     * 按 SKU 编码精确查全量(#6 三期 AI 地基,GoodsQueryApi.findSkuByCode 委托落点):
     * uk_sku_code 唯一键,selectOne 无多行风险;不存在返回 null。不过滤 status——禁用 SKU 的历史引用仍需可查
     */
    public ProductSkuResponse getSkuByCode(String skuCode) {
        if (StrUtil.isBlank(skuCode)) {
            return null;
        }
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSkuCode, skuCode));
        return sku == null ? null : ProductSkuResponse.from(sku);
    }

    /**
     * 内部SKU编码 → ID 映射(#5 自动匹配编排用,erp-api 调用):
     * seller_sku == sku_code 精确匹配的 goods 侧查询;in 分批 ≤1000(docs/07 §5);
     * 同码重复取先到(uk_sku 唯一,理论不重复,防御兜底)。不过滤 status:启停由人工决策,历史映射保留
     */
    public Map<String, Long> mapSkuCodesToIds(Collection<String> skuCodes) {
        if (CollUtil.isEmpty(skuCodes)) {
            return Map.of();
        }
        Map<String, Long> result = new HashMap<>();
        for (List<String> batch : CollUtil.split(new ArrayList<>(skuCodes), 1000)) {
            for (ProductSku sku : skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                    .in(ProductSku::getSkuCode, batch))) {
                result.putIfAbsent(sku.getSkuCode(), sku.getId());
            }
        }
        return result;
    }

    /** 新增 SKU:skuCode 全局唯一(#5 收口)——前置查重给友好报错,并发窗口由 uk_sku 兜底捕 DuplicateKeyException 转业务异常 */
    public Long createSku(ProductSkuSaveRequest request) {
        requireSkuCodeFree(request.skuCode(), null);
        ProductSku sku = request.toEntity();
        try {
            skuMapper.insert(sku);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("SKU编码已存在: " + request.skuCode());
        }
        return sku.getId();
    }

    /** 更新 SKU(MP 忽略 null 可部分更新):skuCode 同受唯一约束,查重排除自身 */
    public void updateSku(Long id, ProductSkuSaveRequest request) {
        requireSkuCodeFree(request.skuCode(), id);
        ProductSku sku = request.toEntity();
        sku.setId(id);
        try {
            skuMapper.updateById(sku);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("SKU编码已存在: " + request.skuCode());
        }
    }

    /** skuCode 唯一前置查重(#5):excludeId 非空排除自身(更新场景);只查不锁,并发窗口由 uk_sku 兜底 */
    private void requireSkuCodeFree(String skuCode, Long excludeId) {
        Long count = skuMapper.selectCount(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSkuCode, skuCode)
                .ne(excludeId != null, ProductSku::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("SKU编码已存在: " + skuCode);
        }
    }

    /**
     * 删除 SKU:一期硬删。删除前引用校验先拦截(#5,2026-09-04 接口模块方案收口):
     * shop_product_sku 绑定 / inventory / shop_order_item / purchase_order_item,任一存在即禁删;
     * 计数经 erp-contract 接口模块(实现收口 erp-api,本域禁横向依赖引用侧模块,铁律 2)
     */
    public void deleteSku(Long id) {
        long refs = referenceApi.countSkuRefs(List.of(id));
        if (refs > 0) {
            throw new BusinessException("SKU被绑定/库存/订单/采购引用共 " + refs + " 条,禁删,先解除引用");
        }
        skuMapper.deleteById(id);
    }

    /** SKU 存在性(#5 bind 接口校验用):经 erp-contract GoodsSkuApi 暴露给 erp-shop,实现收口 erp-api */
    public boolean existsSku(Long skuId) {
        return skuId != null && skuMapper.selectById(skuId) != null;
    }

    /**
     * 新增 SPU 并批量保存 SKU(同一事务);spuCode 撞 uk_spu、skuCode 唯一(#5 收口)冲突均报 BusinessException
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(ProductSaveRequest request, List<ProductSkuSaveRequest> skus) {
        Long dup = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getSpuCode, request.spuCode()));
        if (dup != null && dup > 0) {
            throw new BusinessException("SPU编码已存在: " + request.spuCode());
        }
        requireSkuCodesUsable(skus);
        Product product = request.toEntity();
        productMapper.insert(product);
        if (skus != null) {
            for (ProductSkuSaveRequest skuRequest : skus) {
                ProductSku sku = skuRequest.toEntity();
                sku.setProductId(product.getId());
                try {
                    skuMapper.insert(sku);
                } catch (DuplicateKeyException e) {
                    throw new BusinessException("SKU编码已存在: " + skuRequest.skuCode());
                }
            }
        }
        return product.getId();
    }

    /**
     * 批量 skuCode 可用性(#5,createProduct 前置):请求内重复直接拒;再逐码查库给友好报错。
     * 逐码 eq 查而不用 in 聚合:Lambda wrapper 的 in 急切解析列元数据,纯 Mockito 单测不可直测(docs/07);
     * SPU 下 SKU 个位数,开销可忽略。并发窗口由 uk_sku 兜底(insert 侧捕 DuplicateKeyException)
     */
    private void requireSkuCodesUsable(List<ProductSkuSaveRequest> skus) {
        if (CollUtil.isEmpty(skus)) {
            return;
        }
        List<String> codes = skus.stream().map(ProductSkuSaveRequest::skuCode).toList();
        Set<String> seen = new HashSet<>();
        List<String> inRequestDup = codes.stream().filter(c -> !seen.add(c)).distinct().toList();
        if (CollUtil.isNotEmpty(inRequestDup)) {
            throw new BusinessException("SKU编码在请求内重复: " + String.join(",", inRequestDup));
        }
        for (String code : codes) {
            requireSkuCodeFree(code, null);
        }
    }
}
