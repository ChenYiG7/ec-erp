package com.own.erp.system.request.command;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * system 域请求 record Bean Validation(#7 参数校验收口):
 * LoginRequest/PasswordChange/PasswordReset 必填;绑定入参 @NotNull 允许空列表(全量重绑语义)。
 * 纯 Validator 直测,不起 Spring(docs/07 §10 AIR)。
 */
class SystemRequestValidationTest {

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

    @Test
    void loginRequestRejectsBlank() {
        assertThat(validator.validate(new LoginRequest(" ", "")))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("username", "password");
    }

    @Test
    void loginRequestAcceptsValid() {
        assertThat(validator.validate(new LoginRequest("admin", "x"))).isEmpty();
    }

    @Test
    void passwordChangeRejectsBlank() {
        assertThat(validator.validate(new PasswordChangeRequest(null, " ")))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("oldPassword", "newPassword");
    }

    @Test
    void passwordResetRejectsNull() {
        assertThat(validator.validate(new PasswordResetRequest(null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("newPassword");
    }

    @Test
    void roleMenuAssignAllowsEmptyListRejectsNull() {
        assertThat(validator.validate(new RoleMenuAssignRequest(List.of()))).isEmpty();
        assertThat(validator.validate(new RoleMenuAssignRequest(null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("menuIds");
    }

    @Test
    void userRoleAssignAllowsEmptyListRejectsNull() {
        assertThat(validator.validate(new UserRoleAssignRequest(List.of()))).isEmpty();
        assertThat(validator.validate(new UserRoleAssignRequest(null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("roleIds");
    }
}
