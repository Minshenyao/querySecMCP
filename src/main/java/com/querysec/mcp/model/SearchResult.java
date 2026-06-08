package com.querysec.mcp.model;

import java.util.List;

public class SearchResult {
    private String engine;
    private int total;
    private List<Asset> assets;
    private String error;

    public SearchResult() {
    }

    public SearchResult(String engine, int total, List<Asset> assets) {
        this.engine = engine;
        this.total = total;
        this.assets = assets;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<Asset> getAssets() {
        return assets;
    }

    public void setAssets(List<Asset> assets) {
        this.assets = assets;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
