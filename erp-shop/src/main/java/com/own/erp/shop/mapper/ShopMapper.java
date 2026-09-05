package com.own.erp.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.shop.entity.Shop;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : com.own.erp.shop.mapper
 */
public interface ShopMapper extends BaseMapper<Shop> {

    /**
     * Token 刷新 CAS(#3,条件更新即守卫):WHERE 命中旧 tokenExpireAt 才写入,防跨实例双刷新;
     * 命中返回 1,脱靶(他实例已刷新)返回 0。refreshToken 传 null 时保留原值(LWA 刷新不轮换)
     */
    int casRefreshToken(@Param("id") Long id,
                        @Param("expectedExpireAt") LocalDateTime expectedExpireAt,
                        @Param("accessToken") String accessToken,
                        @Param("refreshToken") String refreshToken,
                        @Param("tokenExpireAt") LocalDateTime tokenExpireAt);
}
