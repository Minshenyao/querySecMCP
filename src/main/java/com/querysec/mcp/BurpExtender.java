package com.querysec.mcp;

import burp.IBurpExtender;
import burp.IBurpExtenderCallbacks;
import burp.IExtensionStateListener;
import com.querysec.mcp.common.BurpLogger;
import com.querysec.mcp.common.Constants;
import com.querysec.mcp.config.ConfigManager;
import com.querysec.mcp.utils.HttpClientFactory;

public class BurpExtender implements IBurpExtender, IExtensionStateListener {
    private static IBurpExtenderCallbacks callbacks;
    private static ConfigManager configManager;
    private MCPServer mcpServer;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        BurpExtender.callbacks = callbacks;
        callbacks.setExtensionName("QuerySec MCP");

        // 初始化日志系统
        BurpLogger.init(callbacks);

        // 注册扩展状态监听器，用于清理资源
        callbacks.registerExtensionStateListener(this);

        BurpLogger.info("QuerySec MCP Plugin Loading...");

        try {
            // 初始化配置管理器
            configManager = new ConfigManager();
            BurpLogger.info("Config file location: " + configManager.getConfigFilePath());

            // 检查配置
            boolean hasAnyConfig = false;
            String[] engines = {
                Constants.ENGINE_FOFA,
                Constants.ENGINE_SHODAN,
                Constants.ENGINE_QUAKE,
                Constants.ENGINE_HUNTER,
                Constants.ENGINE_ZOOMEYE
            };

            for (String engine : engines) {
                if (configManager.hasValidConfig(engine)) {
                    hasAnyConfig = true;
                    String apiKey = configManager.getAPIKey(engine);
                    BurpLogger.info("  - " + engine.toUpperCase() + " API key configured: " +
                        BurpLogger.maskApiKey(apiKey));
                }
            }

            if (!hasAnyConfig) {
                BurpLogger.warn("No API keys configured!");
                BurpLogger.warn("Please edit: " + configManager.getConfigFilePath());
            }

            // 启动 MCP Server
            mcpServer = new MCPServer(Constants.MCP_SERVER_PORT, callbacks, configManager);
            mcpServer.start();

            BurpLogger.info("QuerySec MCP Server started on port " + Constants.MCP_SERVER_PORT);
            BurpLogger.info("Supported search engines: FOFA, Shodan, Quake, Hunter, ZoomEye");
        } catch (Exception e) {
            BurpLogger.error("Failed to start MCP Server", e);
        }
    }

    @Override
    public void extensionUnloaded() {
        BurpLogger.info("QuerySec MCP Plugin Unloading...");

        // 停止 MCP Server，释放端口
        if (mcpServer != null) {
            try {
                mcpServer.stop();
                BurpLogger.info("MCP Server stopped successfully");
            } catch (Exception e) {
                BurpLogger.error("Error stopping MCP Server", e);
            }
        }

        // 清理 HTTP 客户端资源
        try {
            HttpClientFactory.cleanup();
            BurpLogger.info("HTTP client resources cleaned up");
        } catch (Exception e) {
            BurpLogger.error("Error cleaning up HTTP client", e);
        }

        BurpLogger.info("QuerySec MCP Plugin Unloaded");
    }

    public static IBurpExtenderCallbacks getCallbacks() {
        return callbacks;
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }
}
