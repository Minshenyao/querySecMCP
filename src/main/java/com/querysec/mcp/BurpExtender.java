package com.querysec.mcp;

import burp.IBurpExtender;
import burp.IBurpExtenderCallbacks;
import burp.IExtensionStateListener;
import com.querysec.mcp.config.ConfigManager;

import java.io.PrintStream;

public class BurpExtender implements IBurpExtender, IExtensionStateListener {
    private static IBurpExtenderCallbacks callbacks;
    private static ConfigManager configManager;
    private MCPServer mcpServer;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        BurpExtender.callbacks = callbacks;
        callbacks.setExtensionName("QuerySec MCP");

        // 注册扩展状态监听器，用于清理资源
        callbacks.registerExtensionStateListener(this);

        callbacks.printOutput("QuerySec MCP Plugin Loading...");

        try {
            // 初始化配置管理器
            configManager = new ConfigManager();
            callbacks.printOutput("Config file location: " + configManager.getConfigFilePath());

            // 检查配置
            boolean hasAnyConfig = false;
            String[] engines = {"fofa", "shodan", "quake", "hunter", "zoomeye"};
            for (String engine : engines) {
                if (configManager.hasValidConfig(engine)) {
                    hasAnyConfig = true;
                    callbacks.printOutput("  - " + engine.toUpperCase() + " API key configured");
                }
            }

            if (!hasAnyConfig) {
                callbacks.printOutput("WARNING: No API keys configured!");
                callbacks.printOutput("Please edit: " + configManager.getConfigFilePath());
            }

            // 启动 MCP Server
            mcpServer = new MCPServer(23389, callbacks, configManager);
            mcpServer.start();

            callbacks.printOutput("QuerySec MCP Server started on port 23389");
            callbacks.printOutput("Supported search engines: FOFA, Shodan, Quake, Hunter, ZoomEye");
        } catch (Exception e) {
            callbacks.printError("Failed to start MCP Server: " + e.getMessage());
            e.printStackTrace(new PrintStream(callbacks.getStderr()));
        }
    }

    @Override
    public void extensionUnloaded() {
        callbacks.printOutput("QuerySec MCP Plugin Unloading...");

        // 停止 MCP Server，释放端口
        if (mcpServer != null) {
            try {
                mcpServer.stop();
                callbacks.printOutput("MCP Server stopped successfully");
            } catch (Exception e) {
                callbacks.printError("Error stopping MCP Server: " + e.getMessage());
                e.printStackTrace(new PrintStream(callbacks.getStderr()));
            }
        }

        callbacks.printOutput("QuerySec MCP Plugin Unloaded");
    }

    public static IBurpExtenderCallbacks getCallbacks() {
        return callbacks;
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }
}
