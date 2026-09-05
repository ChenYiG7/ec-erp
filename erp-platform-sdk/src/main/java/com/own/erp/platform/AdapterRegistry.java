package com.own.erp.platform;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.own.erp.platform.gateway.PlatformGateway;
import com.own.erp.platform.gateway.PlatformRateGuard;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 平台适配器注册表:Spring 注入所有 PlatformClient 实现,按平台索引。
 *     新增平台 = 新增一个实现类,主系统零改动。
 *     注册前统一套 PlatformGateway 装饰(#3 限流横切收口在此,各 adapter 对限流零感知;
 *     容器无 PlatformRateGuard(极端运行形态)时退回裸客户端,限流整体直通)
 */
@Component
public class AdapterRegistry {

    private final Map<PlatformType, PlatformClient> clients = new EnumMap<>(PlatformType.class);

    public AdapterRegistry(List<PlatformClient> clientList, ObjectProvider<PlatformRateGuard> rateGuardProvider) {
        PlatformRateGuard rateGuard = rateGuardProvider.getIfAvailable();
        for (PlatformClient client : clientList) {
            clients.put(client.platform(), rateGuard == null ? client : new PlatformGateway(client, rateGuard));
        }
    }

    public Optional<PlatformClient> get(PlatformType platform) {
        return Optional.ofNullable(clients.get(platform));
    }
}
