package com.own.erp.shop.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.shop.request.query.ShopQuery;
import com.own.erp.shop.request.command.ShopSaveRequest;
import com.own.erp.shop.response.ShopResponse;
import com.own.erp.shop.service.ShopService;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 店铺管理 CRUD:API 模型收口(docs/07 §1)——入参 ShopQuery/ShopSaveRequest,出参 ShopResponse,entity 不出 Service 层;
 *     凭证表例外(docs/07 §2.1)——读写一律走 ShopService,本类不注入 ShopMapper;
 *     脱敏在 Service 读出口统一处理(accessToken 只留密文前 6 位掩码,appSecret/refreshToken 无出参字段);
 *     解密装配见 ShopService.getShopSession(TODO#2 已完成);
 *     OAuth(#3 已落地):GET /{id}/auth-url 生成授权跳转地址,GET /oauth/callback 接收平台重定向
 *     (SecurityConfig 放行该路径——浏览器直跳无 JWT,防伪造靠加密 state,TTL 10 分钟)
 */
@Slf4j
@Tag(name = "店铺管理", description = "店铺 CRUD 与平台授权凭证;凭证字段脱敏返回,授权流程见 TODO.md #3")
@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @Operation(summary = "分页查询店铺", description = "platform/status 过滤(可选);"
            + "accessToken 只回密文前 6 位掩码,appSecret/refreshToken 不在出参模型内")
    @GetMapping
    public Result<Page<ShopResponse>> page(ShopQuery query) {
        return Result.ok(shopService.pageShops(query));
    }

    @Operation(summary = "店铺详情", description = "凭证字段脱敏;不存在返回 null data")
    @GetMapping("/{id}")
    public Result<ShopResponse> get(@PathVariable Long id) {
        return Result.ok(shopService.getShopById(id));
    }

    @Operation(summary = "新增店铺", description = "平台编码校验;appSecret/accessToken/refreshToken AES-GCM 加密落库")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ShopSaveRequest request) {
        return Result.ok(shopService.createShop(request));
    }

    @Operation(summary = "更新店铺", description = "凭证三字段:null/空串=不改,掩码回传=忽略并记日志,真实新值才加密覆盖;"
            + "MP 忽略 null 可部分更新")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ShopSaveRequest request) {
        shopService.updateShop(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除店铺", description = "一期硬删;TODO#3 后增加 listing/订单/pull_log 引用校验")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        shopService.deleteShop(id);
        return Result.ok();
    }

    @Operation(summary = "生成平台授权跳转地址", description = "OAuth 授权入口:平台 adapter 签发跳转 URL,"
            + "state 加密签发 10 分钟有效;需先配置店铺 appKey/appSecret")
    @GetMapping("/{id}/auth-url")
    public Result<String> authUrl(@PathVariable Long id,
                                  @RequestParam(defaultValue = "http://localhost:8088/api/shops/oauth/callback") String redirectUri) {
        return Result.ok(shopService.buildAuthUrl(id, redirectUri));
    }

    /**
     * 平台授权回调(#3):平台授权页把卖家浏览器重定向回本接口,无 JWT,SecurityConfig 放行;
     * 参数名是平台报文(selling_partner_id/spapi_oauth_code 为 Amazon 约定),平台差异留在 Controller 入口翻译,
     * Service 只收中性命名;redirect_uri 取实际回调地址(Seller Central 应用登记同源)。
     * 浏览器直跳返回极简 HTML 结果页(非 JSON),异常消息均由 Service 构造、不含凭证
     */
    @Operation(summary = "平台授权回调(OAuth)", description = "平台授权页重定向回本接口:state 校验 → 授权码换 Token →"
            + "加密入库并记 tokenExpireAt;该路径免登录(加密 state 防伪造),仅平台重定向调用")
    @GetMapping("/oauth/callback")
    public ResponseEntity<String> oauthCallback(
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "spapi_oauth_code", required = false) String authCode,
            @RequestParam(value = "selling_partner_id", required = false) String sellerId,
            HttpServletRequest request) {
        if (StrUtil.isBlank(state) || StrUtil.isBlank(authCode)) {
            return htmlPage(HttpStatus.BAD_REQUEST, "授权回调参数缺失(state/授权码),请重新发起授权");
        }
        try {
            Long shopId = shopService.handleOAuthCallback(state, sellerId, authCode,
                    request.getRequestURL().toString());
            return htmlPage(HttpStatus.OK, "授权成功:店铺 " + shopId + " 凭证已更新,请返回 ERP 系统继续操作");
        } catch (Exception e) {
            log.warn("OAuth 回调处理失败:{}", e.getMessage());
            return htmlPage(HttpStatus.BAD_REQUEST, "授权失败:" + e.getMessage());
        }
    }

    /** 授权结果极简页:浏览器直跳可见,无脚本无样式,消息文本由本类拼装(禁含凭证/密文) */
    private static ResponseEntity<String> htmlPage(HttpStatus status, String message) {
        String body = "<!doctype html><html lang=\"zh\"><head><meta charset=\"utf-8\"><title>ERP 授权结果</title></head>"
                + "<body><p>" + message + "</p></body></html>";
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(body);
    }
}
