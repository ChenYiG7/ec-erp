package com.own.erp.shop.security;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 加解密失败载体(TODO#2):AES-GCM 解密失败(密钥不匹配/数据损坏/密文非法)统一包成此异常,
 *     由调用方(ShopService)转译为带业务语境的 BusinessException;消息不得携带任何密文/明文
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
