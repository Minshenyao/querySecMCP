package com.querysec.mcp.engines;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.querysec.mcp.model.Asset;
import com.querysec.mcp.model.SearchResult;
import com.querysec.mcp.utils.ProxyHelper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.*;

public class FOFAEngine implements SearchEngine {
    private static final String API_URL = "https://fofa.info/api/v1/search/all";
    private OkHttpClient client;
    private final Gson gson;

    public FOFAEngine() {
        this.gson = new Gson();
        this.client = ProxyHelper.createClient(null);
    }

    @Override
    public void setProxy(String proxyUrl) {
        this.client = ProxyHelper.createClient(proxyUrl);
    }

    @Override
    public SearchResult search(Map<String, Object> params) {
        SearchResult result = new SearchResult();
        result.setEngine("FOFA");

        try {
            String query = (String) params.get("query");
            String apiKey = (String) params.get("api_key");
            String email = (String) params.get("email");
            int size = params.containsKey("size") ?
                ((Number) params.get("size")).intValue() : 100;

            // Base64 编码查询
            String encodedQuery = Base64.getEncoder().encodeToString(query.getBytes());

            String url = String.format("%s?email=%s&key=%s&qbase64=%s&size=%d&fields=host,ip,port,protocol,title,country,city,domain",
                    API_URL, email, apiKey, encodedQuery, size);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    result.setError("FOFA API error: " + response.code());
                    return result;
                }

                String body = response.body().string();
                JsonObject jsonResponse = gson.fromJson(body, JsonObject.class);

                if (!jsonResponse.get("error").getAsBoolean()) {
                    int total = jsonResponse.get("size").getAsInt();
                    result.setTotal(total);

                    List<Asset> assets = new ArrayList<>();
                    JsonArray results = jsonResponse.getAsJsonArray("results");

                    for (int i = 0; i < results.size(); i++) {
                        JsonArray item = results.get(i).getAsJsonArray();
                        Asset asset = new Asset();

                        asset.setHost(item.get(0).getAsString());
                        asset.setIp(item.get(1).getAsString());
                        asset.setPort(item.get(2).getAsInt());
                        asset.setProtocol(item.get(3).getAsString());
                        asset.setTitle(item.get(4).getAsString());
                        asset.setCountry(item.get(5).getAsString());
                        asset.setCity(item.get(6).getAsString());

                        if (item.size() > 7 && !item.get(7).isJsonNull()) {
                            asset.setDomains(Arrays.asList(item.get(7).getAsString()));
                        }

                        assets.add(asset);
                    }

                    result.setAssets(assets);
                } else {
                    result.setError(jsonResponse.get("errmsg").getAsString());
                }
            }
        } catch (Exception e) {
            result.setError("FOFA search failed: " + e.getMessage());
        }

        return result;
    }
}
