package com.own.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 主应用入口。模块化单体:所有业务模块同进程部署。
 *     一期定时拉单也在此进程内(@Scheduled);订单量上来后切换到 erp-worker 独立进程。
 */
@SpringBootApplication
public class ErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpApplication.class, args);
    }
}
