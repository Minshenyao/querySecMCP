package com.querysec.mcp.common;

import burp.IBurpExtenderCallbacks;

/**
 * 统一的日志工具类
 */
public class BurpLogger {
    private static IBurpExtenderCallbacks callbacks;

    public static void init(IBurpExtenderCallbacks callbacks) {
        BurpLogger.callbacks = callbacks;
    }

    public static void info(String message) {
        if (callbacks != null) {
            callbacks.printOutput("[INFO] " + message);
        }
    }

    public static void warn(String message) {
        if (callbacks != null) {
            callbacks.printOutput("[WARN] " + message);
        }
    }

    public static void error(String message) {
        if (callbacks != null) {
            callbacks.printError("[ERROR] " + message);
        }
    }

    public static void error(String message, Throwable throwable) {
        if (callbacks != null) {
            callbacks.printError("[ERROR] " + message + ": " + throwable.getMessage());
        }
    }

    /**
     * API Key 脱敏显示
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
