package com.querysec.mcp.model;

import java.util.Map;

public class MCPResponse {
    private String jsonrpc;
    private Object id;
    private String method;
    private Map<String, Object> params;
    private Object result;
    private MCPError error;

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public MCPError getError() {
        return error;
    }

    public void setError(MCPError error) {
        this.error = error;
    }
}
