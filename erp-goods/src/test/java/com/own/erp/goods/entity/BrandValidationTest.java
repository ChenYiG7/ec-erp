package com.own.erp.goods.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brand 实体 Bean Validation 分组校验(#7 参数校验收口):
 * create 端点 = Default + Create 组(必填生效);update 端点 = 仅 Default(@Size 对 null 放行,保住部分更新语义)。
 * 纯 Validator 直测,不起 Spring(docs/07 §10 AIR)。
 */
class BrandValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Brand validBrand() {
        return Brand.builder().name("测试品牌").build();
    }

    @Test
    void createGroupRejectsBlankName() {
        Set<ConstraintViolation<Brand>> violations = validator.validate(
                Brand.builder().name(" ").build(), Default.class, Brand.Create.class);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("品牌名称不能为空");
    }

    @Test
    void createGroupRejectsOversizeName() {
        Set<ConstraintViolation<Brand>> violations = validator.validate(
                Brand.builder().name("长".repeat(129)).build(), Default.class, Brand.Create.class);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("品牌名称不能超过 128 字");
    }

    @Test
    void createGroupAcceptsValidBrand() {
        assertThat(validator.validate(validBrand(), Default.class, Brand.Create.class)).isEmpty();
    }

    @Test
    void defaultGroupAllowsPartialUpdateAllNull() {
        // update 部分更新语义:全字段 null(仅改 status 场景)不触发任何约束
        assertThat(validator.validate(new Brand(), Default.class)).isEmpty();
    }

    @Test
    void defaultGroupStillRejectsOversizeName() {
        // 部分更新但传了超长 name,仍要拦
        Set<ConstraintViolation<Brand>> violations = validator.validate(
                Brand.builder().name("长".repeat(129)).build(), Default.class);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("品牌名称不能超过 128 字");
    }

    @Test
    void defaultGroupDoesNotEnforceNotBlank() {
        // Default 组无 @NotBlank:空 name 走 update 不被分组校验拦(必填是 Create 组职责)
        assertThat(validator.validate(Brand.builder().name(" ").build(), Default.class)).isEmpty();
    }
}
