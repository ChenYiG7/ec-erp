package com.own.erp.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 拉单 worker 入口(独立进程形态,二期启用)。
 *     职责:按店铺轮询 PlatformClient 拉取订单/商品/售后 → 写入统一订单库。
 *     与主应用共享同一个 MySQL,通过 UNIQUE(shop_id, platform_order_id) 保证幂等。
 */
@SpringBootApplication
public class PullWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PullWorkerApplication.class, args);
    }
}
