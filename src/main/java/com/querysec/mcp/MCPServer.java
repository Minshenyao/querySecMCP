package com.querysec.mcp;

import burp.IBurpExtenderCallbacks;
import com.google.gson.Gson;
import com.querysec.mcp.config.ConfigManager;
import com.querysec.mcp.engines.*;
import com.querysec.mcp.model.Asset;
import com.querysec.mcp.model.SearchResult;
import com.querysec.mcp.model.MCPError;
import com.querysec.mcp.model.MCPRequest;
import com.querysec.mcp.model.MCPResponse;
import com.querysec.mcp.server.SseAcceptFilter;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.servlet.FilterHolder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MCPServer {
    private static final Gson gson = new Gson();
    private static volatile Javalin globalApp = null; // 全局单例，防止重复启动
    private final int port;
    private final IBurpExtenderCallbacks callbacks;
    private final ConfigManager configManager;
    private Javalin app;

    // sessionId -> SSE 客户端
    private final Map<String, SseClient> sseSessions = new ConcurrentHashMap<>();

    // Search engines
    private final FOFAEngine fofaEngine;
    private final ShodanEngine shodanEngine;
    private final QuakeEngine quakeEngine;
    private final HunterEngine hunterEngine;
    private final ZoomEyeEngine zoomEyeEngine;

    public MCPServer(int port, IBurpExtenderCallbacks callbacks, ConfigManager configManager) {
        this.port = port;
        this.callbacks = callbacks;
        this.configManager = configManager;

        // Initialize engines
        this.fofaEngine = new FOFAEngine();
        this.shodanEngine = new ShodanEngine();
        this.quakeEngine = new QuakeEngine();
        this.hunterEngine = new HunterEngine();
        this.zoomEyeEngine = new ZoomEyeEngine();

        // Set proxy if configured
        String proxyUrl = configManager.getProxyUrl();
        if (proxyUrl != null) {
            fofaEngine.setProxy(proxyUrl);
            shodanEngine.setProxy(proxyUrl);
            quakeEngine.setProxy(proxyUrl);
            hunterEngine.setProxy(proxyUrl);
            zoomEyeEngine.setProxy(proxyUrl);
        }
    }

    public void start() throws Exception {
        // 检查全局实例是否已启动
        if (globalApp != null) {
            callbacks.printOutput("MCP Server already running on port " + port);
            app = globalApp;
            return;
        }

        synchronized (MCPServer.class) {
            // 双重检查
            if (globalApp != null) {
                callbacks.printOutput("MCP Server already running on port " + port);
                app = globalApp;
                return;
            }

            // 直接在 create 时启动，避免 Javalin 实例被复用
            globalApp = Javalin.create(config -> {
                config.showJavalinBanner = false;
                // 添加 SSE Accept Filter，确保 Codex/supergateway 能正常连接
                config.jetty.contextHandlerConfig(handler ->
                    handler.addFilter(new FilterHolder(new SseAcceptFilter()), "/*",
                        EnumSet.of(DispatcherType.REQUEST)));
            }).start("127.0.0.1", port);

            app = globalApp;

            // 注册路由
            app.get("/health", ctx -> ctx.result("OK"));
            app.sse("/", this::handleSseConnection);
            app.sse("/sse", this::handleSseConnection);
            app.post("/messages", this::handleMessage);
        }

        callbacks.printOutput("MCP Server listening on http://127.0.0.1:" + port);
        callbacks.printOutput("Claude Desktop config: \"url\": \"http://127.0.0.1:" + port + "\"");
    }

    public void stop() throws Exception {
        if (app != null) {
            app.stop();
            callbacks.printOutput("MCP Server stopped");
        }
        globalApp = null; // 清除全局引用
    }

    /**
     * 建立 SSE 连接
     */
    private void handleSseConnection(SseClient client) {
        String sessionId = UUID.randomUUID().toString();
        sseSessions.put(sessionId, client);
        client.keepAlive();
        client.onClose(() -> {
            sseSessions.remove(sessionId);
            callbacks.printOutput("SSE stream closed: " + sessionId);
        });

        // 发送 endpoint 事件
        client.sendEvent("endpoint", "/messages?sessionId=" + sessionId);
        callbacks.printOutput("SSE stream established: " + sessionId);
        callbacks.printOutput("Sent endpoint event: /messages?sessionId=" + sessionId);
    }

    /**
     * 处理 POST /messages
     */
    private void handleMessage(Context ctx) {
        String sessionId = ctx.queryParam("sessionId");
        SseClient client = sessionId != null ? sseSessions.get(sessionId) : null;

        if (client == null) {
            ctx.status(404).result("Session not found");
            return;
        }

        MCPRequest request;
        try {
            request = gson.fromJson(ctx.body(), MCPRequest.class);
        } catch (Exception e) {
            ctx.status(400).result("Invalid JSON-RPC message");
            return;
        }

        callbacks.printOutput("Received JSON-RPC via /messages: " + request.getMethod() + " (session: " + sessionId + ")");

        // 处理请求
        MCPResponse response = processRequest(request);

        // 如果是通知（返回 null），只返回 202
        if (response == null) {
            ctx.status(202).result("{}");
            return;
        }

        // 通过 SSE 发送响应
        try {
            client.sendEvent("message", gson.toJson(response));
            callbacks.printOutput("Sent response via SSE: " + request.getMethod());
        } catch (Exception e) {
            callbacks.printError("Failed to send SSE response: " + e.getMessage());
        }

        // 返回 202 Accepted
        ctx.status(202).result("{}");
    }

    /**
     * 处理 JSON-RPC 请求
     */
    private MCPResponse processRequest(MCPRequest request) {
        MCPResponse response = new MCPResponse();
        response.setJsonrpc("2.0");
        response.setId(request.getId());

        String method = request.getMethod();

        switch (method) {
            case "initialize":
                response.setResult(handleInitialize());
                break;
            case "notifications/initialized":
                callbacks.printOutput("Client initialized and ready");
                return null; // 通知不需要响应
            case "tools/list":
                response.setResult(handleToolsList());
                break;
            case "tools/call":
                response.setResult(handleToolsCall(request.getParams()));
                break;
            default:
                MCPError error = new MCPError(-32601, "Method not found: " + method);
                response.setError(error);
        }

        return response;
    }

    private Map<String, Object> handleInitialize() {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("serverInfo", Map.of(
            "name", "QuerySec MCP Server",
            "version", "1.0.0"
        ));
        result.put("capabilities", Map.of(
            "tools", Map.of()
        ));
        return result;
    }

    private Map<String, Object> handleToolsList() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // FOFA
        tools.add(createToolDefinition(
            "fofa_search",
            "Search assets using FOFA search engine",
            Map.of(
                "query", Map.of("type", "string", "description", "FOFA query syntax"),
                "api_key", Map.of("type", "string", "description", "FOFA API key"),
                "size", Map.of("type", "number", "description", "Number of results (default: 100)")
            ),
            List.of("query", "api_key")
        ));

        // Shodan
        tools.add(createToolDefinition(
            "shodan_search",
            "Search assets using Shodan search engine",
            Map.of(
                "query", Map.of("type", "string", "description", "Shodan query syntax"),
                "api_key", Map.of("type", "string", "description", "Shodan API key"),
                "page", Map.of("type", "number", "description", "Page number (default: 1)")
            ),
            List.of("query", "api_key")
        ));

        // Quake
        tools.add(createToolDefinition(
            "quake_search",
            "Search assets using Quake 360 search engine",
            Map.of(
                "query", Map.of("type", "string", "description", "Quake query syntax"),
                "api_key", Map.of("type", "string", "description", "Quake API key"),
                "size", Map.of("type", "number", "description", "Number of results (default: 10)")
            ),
            List.of("query", "api_key")
        ));

        // Hunter
        tools.add(createToolDefinition(
            "hunter_search",
            "Search assets using Hunter search engine",
            Map.of(
                "query", Map.of("type", "string", "description", "Hunter query syntax"),
                "api_key", Map.of("type", "string", "description", "Hunter API key"),
                "page", Map.of("type", "number", "description", "Page number (default: 1)"),
                "page_size", Map.of("type", "number", "description", "Page size (default: 10)")
            ),
            List.of("query", "api_key")
        ));

        // ZoomEye
        tools.add(createToolDefinition(
            "zoomeye_search",
            "Search assets using ZoomEye search engine",
            Map.of(
                "query", Map.of("type", "string", "description", "ZoomEye query syntax"),
                "api_key", Map.of("type", "string", "description", "ZoomEye API key"),
                "type", Map.of("type", "string", "description", "Search type: host or web (default: host)"),
                "page", Map.of("type", "number", "description", "Page number (default: 1)")
            ),
            List.of("query", "api_key")
        ));

        return Map.of("tools", tools);
    }

    private Map<String, Object> createToolDefinition(String name, String description,
                                                     Map<String, Map<String, String>> properties,
                                                     List<String> required) {
        return Map.of(
            "name", name,
            "description", description,
            "inputSchema", Map.of(
                "type", "object",
                "properties", properties,
                "required", required
            )
        );
    }

    private Map<String, Object> handleToolsCall(Map<String, Object> params) {
        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        try {
            // 从配置文件自动填充 API Key
            arguments = fillApiKeyFromConfig(toolName, arguments);

            SearchResult searchResult;
            switch (toolName) {
                case "fofa_search":
                    searchResult = fofaEngine.search(arguments);
                    break;
                case "shodan_search":
                    searchResult = shodanEngine.search(arguments);
                    break;
                case "quake_search":
                    searchResult = quakeEngine.search(arguments);
                    break;
                case "hunter_search":
                    searchResult = hunterEngine.search(arguments);
                    break;
                case "zoomeye_search":
                    searchResult = zoomEyeEngine.search(arguments);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown tool: " + toolName);
            }

            // 格式化结果
            String resultText = formatSearchResult(searchResult);

            return Map.of(
                "content", List.of(
                    Map.of(
                        "type", "text",
                        "text", resultText
                    )
                )
            );
        } catch (Exception e) {
            callbacks.printError("Tool call error: " + e.getMessage());
            return Map.of(
                "content", List.of(
                    Map.of(
                        "type", "text",
                        "text", "Error: " + e.getMessage()
                    )
                ),
                "isError", true
            );
        }
    }

    /**
     * 从配置文件自动填充 API Key
     */
    private Map<String, Object> fillApiKeyFromConfig(String toolName, Map<String, Object> arguments) {
        Map<String, Object> result = new HashMap<>(arguments);

        // 如果用户没有提供 api_key，从配置文件读取
        if (!result.containsKey("api_key") || "placeholder".equals(result.get("api_key"))) {
            String engineName = null;
            switch (toolName) {
                case "fofa_search":
                    engineName = "fofa";
                    break;
                case "shodan_search":
                    engineName = "shodan";
                    break;
                case "quake_search":
                    engineName = "quake";
                    break;
                case "hunter_search":
                    engineName = "hunter";
                    break;
                case "zoomeye_search":
                    engineName = "zoomeye";
                    break;
            }

            if (engineName != null && configManager.hasValidConfig(engineName)) {
                result.put("api_key", configManager.getAPIKey(engineName));
            }
        }

        return result;
    }

    private String formatSearchResult(SearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Search Engine: ").append(result.getEngine()).append("\n");
        sb.append("Total: ").append(result.getTotal()).append(" results\n\n");

        if (result.getAssets() != null && !result.getAssets().isEmpty()) {
            sb.append("Results:\n");
            for (int i = 0; i < result.getAssets().size(); i++) {
                Asset asset = result.getAssets().get(i);
                sb.append("\n[").append(i + 1).append("] ");
                if (asset.getIp() != null && !asset.getIp().isEmpty()) {
                    sb.append("IP: ").append(asset.getIp());
                }
                if (asset.getPort() > 0) {
                    sb.append(" Port: ").append(asset.getPort());
                }
                if (asset.getHost() != null && !asset.getHost().isEmpty()) {
                    sb.append(" Host: ").append(asset.getHost());
                }
                if (asset.getTitle() != null && !asset.getTitle().isEmpty()) {
                    sb.append("\n    Title: ").append(asset.getTitle());
                }
                if (asset.getProtocol() != null && !asset.getProtocol().isEmpty()) {
                    sb.append("\n    Protocol: ").append(asset.getProtocol());
                }
                sb.append("\n");
            }
        } else if (result.getError() != null && !result.getError().isEmpty()) {
            sb.append("Error: ").append(result.getError());
        } else {
            sb.append("No results found");
        }

        return sb.toString();
    }
}
