package com.querysec.mcp.exception;

/**
 * MCP 协议相关异常基类
 */
public class MCPException extends Exception {
    private final int code;

    public MCPException(int code, String message) {
        super(message);
        this.code = code;
    }

    public MCPException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
