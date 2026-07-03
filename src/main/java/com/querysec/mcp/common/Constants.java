package com.querysec.mcp.common;

/**
 * 全局常量定义
 */
public class Constants {
    // MCP Server 配置
    public static final int MCP_SERVER_PORT = 23389;
    public static final String MCP_SERVER_HOST = "127.0.0.1";
    public static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    public static final String SERVER_NAME = "QuerySec MCP Server";
    public static final String SERVER_VERSION = "1.0.0";

    // 参数名称
    public static final String PARAM_QUERY = "query";
    public static final String PARAM_API_KEY = "api_key";
    public static final String PARAM_SIZE = "size";
    public static final String PARAM_PAGE = "page";
    public static final String PARAM_PAGE_SIZE = "page_size";
    public static final String PARAM_TYPE = "type";

    // 默认值
    public static final int DEFAULT_FOFA_SIZE = 100;
    public static final int DEFAULT_QUAKE_SIZE = 10;
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_HUNTER_PAGE_SIZE = 10;

    // HTTP 配置
    public static final int HTTP_CONNECT_TIMEOUT_SECONDS = 10;
    public static final int HTTP_READ_TIMEOUT_SECONDS = 30;
    public static final int HTTP_WRITE_TIMEOUT_SECONDS = 10;
    public static final int HTTP_CONNECTION_POOL_MAX_IDLE = 5;
    public static final int HTTP_CONNECTION_POOL_KEEP_ALIVE_MINUTES = 5;

    // 搜索引擎名称
    public static final String ENGINE_FOFA = "fofa";
    public static final String ENGINE_SHODAN = "shodan";
    public static final String ENGINE_QUAKE = "quake";
    public static final String ENGINE_HUNTER = "hunter";
    public static final String ENGINE_ZOOMEYE = "zoomeye";

    // 工具名称
    public static final String TOOL_FOFA_SEARCH = "fofa_search";
    public static final String TOOL_SHODAN_SEARCH = "shodan_search";
    public static final String TOOL_QUAKE_SEARCH = "quake_search";
    public static final String TOOL_HUNTER_SEARCH = "hunter_search";
    public static final String TOOL_ZOOMEYE_SEARCH = "zoomeye_search";

    private Constants() {
        // 工具类，禁止实例化
    }
}
