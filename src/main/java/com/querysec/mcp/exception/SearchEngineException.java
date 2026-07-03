package com.querysec.mcp.exception;

/**
 * 搜索引擎调用异常
 */
public class SearchEngineException extends Exception {
    private final String engine;
    private final int httpCode;

    public SearchEngineException(String engine, String message) {
        super(message);
        this.engine = engine;
        this.httpCode = -1;
    }

    public SearchEngineException(String engine, int httpCode, String message) {
        super(message);
        this.engine = engine;
        this.httpCode = httpCode;
    }

    public SearchEngineException(String engine, String message, Throwable cause) {
        super(message, cause);
        this.engine = engine;
        this.httpCode = -1;
    }

    public String getEngine() {
        return engine;
    }

    public int getHttpCode() {
        return httpCode;
    }

    /**
     * 获取用户友好的错误信息
     */
    public String getUserFriendlyMessage() {
        if (httpCode > 0) {
            return String.format("%s API error (HTTP %d): %s", engine, httpCode, getMessage());
        }
        return String.format("%s error: %s", engine, getMessage());
    }
}
