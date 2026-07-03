package com.querysec.mcp.utils;

import com.querysec.mcp.common.Constants;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * HTTP 客户端工厂，提供共享连接池
 */
public class HttpClientFactory {
    private static volatile OkHttpClient sharedClient;
    private static volatile OkHttpClient proxyClient;
    private static String currentProxyUrl;

    /**
     * 获取默认的 HTTP 客户端（无代理）
     */
    public static OkHttpClient getDefaultClient() {
        if (sharedClient == null) {
            synchronized (HttpClientFactory.class) {
                if (sharedClient == null) {
                    sharedClient = createBaseClient(null);
                }
            }
        }
        return sharedClient;
    }

    /**
     * 获取带代理的 HTTP 客户端
     */
    public static OkHttpClient getProxyClient(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isEmpty()) {
            return getDefaultClient();
        }

        // 如果代理配置变更，重建客户端
        if (proxyClient == null || !proxyUrl.equals(currentProxyUrl)) {
            synchronized (HttpClientFactory.class) {
                if (proxyClient == null || !proxyUrl.equals(currentProxyUrl)) {
                    proxyClient = createBaseClient(proxyUrl);
                    currentProxyUrl = proxyUrl;
                }
            }
        }
        return proxyClient;
    }

    /**
     * 创建基础 HTTP 客户端
     */
    private static OkHttpClient createBaseClient(String proxyUrl) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Constants.HTTP_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.HTTP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Constants.HTTP_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(
                        Constants.HTTP_CONNECTION_POOL_MAX_IDLE,
                        Constants.HTTP_CONNECTION_POOL_KEEP_ALIVE_MINUTES,
                        TimeUnit.MINUTES
                ));

        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            builder.proxy(ProxyHelper.parseProxy(proxyUrl));
        }

        return builder.build();
    }

    /**
     * 清理资源（插件卸载时调用）
     */
    public static void cleanup() {
        if (sharedClient != null) {
            sharedClient.dispatcher().executorService().shutdown();
            sharedClient.connectionPool().evictAll();
        }
        if (proxyClient != null) {
            proxyClient.dispatcher().executorService().shutdown();
            proxyClient.connectionPool().evictAll();
        }
        sharedClient = null;
        proxyClient = null;
        currentProxyUrl = null;
    }
}
