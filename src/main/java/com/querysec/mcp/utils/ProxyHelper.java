package com.querysec.mcp.utils;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

/**
 * 代理工具类 - 只保留解析功能，HTTP 客户端创建交给 HttpClientFactory
 */
public class ProxyHelper {

    /**
     * 解析代理 URL 并返回 Proxy 对象
     * 支持格式:
     *   - http://127.0.0.1:7890
     *   - socks5://127.0.0.1:7890
     *   - http://user:pass@proxy.example.com:8080
     */
    public static Proxy parseProxy(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isEmpty()) {
            return null;
        }

        try {
            ProxyInfo proxyInfo = parseProxyUrl(proxyUrl);
            return proxyInfo != null ? proxyInfo.proxy : null;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid proxy URL: " + proxyUrl, e);
        }
    }

    /**
     * 创建代理认证器（如果需要认证）
     */
    public static Authenticator createProxyAuthenticator(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isEmpty()) {
            return null;
        }

        try {
            ProxyInfo proxyInfo = parseProxyUrl(proxyUrl);
            if (proxyInfo != null && proxyInfo.username != null && proxyInfo.password != null) {
                return new Authenticator() {
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        String credential = Credentials.basic(proxyInfo.username, proxyInfo.password);
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build();
                    }
                };
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return null;
    }

    /**
     * 解析代理 URL
     * 支持格式:
     *   - http://127.0.0.1:7890
     *   - socks5://127.0.0.1:7890
     *   - http://user:pass@proxy.example.com:8080
     */
    static ProxyInfo parseProxyUrl(String proxyUrl) {
        try {
            URI uri = new URI(proxyUrl);

            String scheme = uri.getScheme();
            if (scheme == null) {
                throw new IllegalArgumentException("Proxy URL must have a scheme (http:// or socks5://)");
            }

            Proxy.Type proxyType;
            switch (scheme.toLowerCase()) {
                case "http":
                case "https":
                    proxyType = Proxy.Type.HTTP;
                    break;
                case "socks":
                case "socks5":
                    proxyType = Proxy.Type.SOCKS;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported proxy scheme: " + scheme);
            }

            String host = uri.getHost();
            int port = uri.getPort();

            if (host == null) {
                throw new IllegalArgumentException("Proxy URL must have a host");
            }
            if (port == -1) {
                throw new IllegalArgumentException("Proxy URL must have a port");
            }

            Proxy proxy = new Proxy(proxyType, new InetSocketAddress(host, port));

            // 解析认证信息
            String username = null;
            String password = null;
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                if (parts.length > 1) {
                    password = parts[1];
                }
            }

            return new ProxyInfo(proxy, username, password);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid proxy URL: " + proxyUrl, e);
        }
    }

    static class ProxyInfo {
        Proxy proxy;
        String username;
        String password;

        ProxyInfo(Proxy proxy, String username, String password) {
            this.proxy = proxy;
            this.username = username;
            this.password = password;
        }
    }
}
