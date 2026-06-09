package com.querysec.mcp.utils;

import com.querysec.mcp.config.ConfigManager.ProxyConfig;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class ProxyHelper {

    /**
     * 从 ProxyConfig 对象创建 OkHttpClient
     */
    public static OkHttpClient createClient(ProxyConfig proxyConfig) {
        if (proxyConfig == null || !proxyConfig.isEnabled()) {
            return createClient((String) null);
        }

        // 构造代理 URL
        StringBuilder proxyUrl = new StringBuilder();
        proxyUrl.append(proxyConfig.getType()).append("://");

        if (proxyConfig.hasAuth()) {
            proxyUrl.append(proxyConfig.getUsername())
                    .append(":")
                    .append(proxyConfig.getPassword())
                    .append("@");
        }

        proxyUrl.append(proxyConfig.getHost())
                .append(":")
                .append(proxyConfig.getPort());

        return createClient(proxyUrl.toString());
    }

    /**
     * 从代理 URL 创建 OkHttpClient
     * 支持格式:
     *   - http://127.0.0.1:7890
     *   - socks5://127.0.0.1:7890
     *   - http://user:pass@proxy.example.com:8080
     */
    public static OkHttpClient createClient(String proxyUrl) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        // 如果启用代理
        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            try {
                ProxyInfo proxyInfo = parseProxyUrl(proxyUrl);
                if (proxyInfo != null) {
                    builder.proxy(proxyInfo.proxy);

                    // 如果有认证信息
                    if (proxyInfo.username != null && proxyInfo.password != null) {
                        builder.proxyAuthenticator(new Authenticator() {
                            @Override
                            public Request authenticate(Route route, Response response) throws IOException {
                                String credential = Credentials.basic(
                                        proxyInfo.username,
                                        proxyInfo.password
                                );
                                return response.request().newBuilder()
                                        .header("Proxy-Authorization", credential)
                                        .build();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse proxy URL: " + proxyUrl + " - " + e.getMessage());
            }
        }

        return builder.build();
    }

    /**
     * 解析代理 URL
     * 支持格式:
     *   - http://127.0.0.1:7890
     *   - socks5://127.0.0.1:7890
     *   - http://user:pass@proxy.example.com:8080
     */
    private static ProxyInfo parseProxyUrl(String proxyUrl) {
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
            System.err.println("Invalid proxy URL: " + proxyUrl + " - " + e.getMessage());
            return null;
        }
    }

    private static class ProxyInfo {
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
