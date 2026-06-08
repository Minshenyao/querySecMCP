package com.querysec.mcp.engines;

import com.querysec.mcp.model.SearchResult;
import java.util.Map;

public interface SearchEngine {
    SearchResult search(Map<String, Object> params);
    void setProxy(String proxyUrl);
}
