package com.own.erp.config;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.own.erp.goods.entity.Brand;
import com.own.erp.goods.entity.Product;
import com.own.erp.goods.entity.ProductCategory;
import com.own.erp.goods.entity.ProductSku;
import com.own.erp.purchase.entity.Supplier;
import com.own.erp.shop.entity.Shop;
import com.own.erp.system.entity.SysDict;
import com.own.erp.system.entity.SysMenu;
import com.own.erp.system.entity.SysRole;
import com.own.erp.system.entity.SysUser;
import com.own.erp.warehouse.entity.Warehouse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : TODO#7 逻辑删除注解规约单测(反射直测,不起 Spring):
 *     11 张人工域实体必须带 @TableLogic(value="0", delval="id") + Long deleted 字段——
 *     delval=id 是唯一键含 deleted 形态的前提(删时置主键 id,0 与各已删行 id 互异),
 *     口径漂移(如改回 delval=1)会静默破坏"删后同键可重建",编译期无感,这里编译期钉死。
 *     SQL 侧行为(updateById 变 UPDATE deleted=id、select 自动滤已删)由迁移脚本自检 + 真库兜底,
 *     mock 单测验证不了 MP 内建方法的 SQL 拼接(docs/07 §10)。
 */
class LogicalDeleteAnnotationTest {

    /** 人工域 11 实体,与 docs/sql/01_schema_init.sql 带 deleted 列的表一一对应(TODO#7) */
    private static final List<Class<?>> LOGIC_DELETE_ENTITIES = List.of(
            SysUser.class, SysRole.class, SysMenu.class, SysDict.class,
            Brand.class, Product.class, ProductSku.class, ProductCategory.class,
            Shop.class, Warehouse.class, Supplier.class);

    @Test
    void logicDeleteEntitiesCarryTableLogicWithIdDelval() throws Exception {
        for (Class<?> entity : LOGIC_DELETE_ENTITIES) {
            Field deleted = entity.getDeclaredField("deleted");
            assertEquals(Long.class, deleted.getType(), entity.getSimpleName() + ".deleted 应为 Long(存被删行id)");
            TableLogic logic = deleted.getAnnotation(TableLogic.class);
            assertNotNull(logic, entity.getSimpleName() + " 缺 @TableLogic");
            assertEquals("0", logic.value(), entity.getSimpleName() + " @TableLogic.value 应为 0");
            assertEquals("id", logic.delval(), entity.getSimpleName()
                    + " @TableLogic.delval 应为 id(唯一键含 deleted 形态的前提,勿改回 1)");
        }
    }
}
