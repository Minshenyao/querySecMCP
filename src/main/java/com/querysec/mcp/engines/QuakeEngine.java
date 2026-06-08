package com.querysec.mcp.engines;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.querysec.mcp.model.Asset;
import com.querysec.mcp.model.SearchResult;
import com.querysec.mcp.utils.ProxyHelper;
import okhttp3.*;

import java.util.*;

public class QuakeEngine implements SearchEngine {
    private static final String API_URL = "https://quake.360.net/api/v3/search/quake_service";
    private OkHttpClient client;
    private final Gson gson;

    public QuakeEngine() {
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
        result.setEngine("Quake");

        try {
            String query = (String) params.get("query");
            String apiKey = (String) params.get("api_key");
            int size = params.containsKey("size") ?
                ((Number) params.get("size")).intValue() : 10;

            // 构造请求 JSON
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("query", query);
            requestBody.addProperty("start", 0);
            requestBody.addProperty("size", size);

            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("X-QuakeToken", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    result.setError("Quake API error: " + response.code());
                    return result;
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                if (jsonResponse.has("code") && jsonResponse.get("code").getAsInt() == 0) {
                    JsonObject data = jsonResponse.getAsJsonObject("data");
                    int total = data.get("total").getAsInt();
                    result.setTotal(total);

                    List<Asset> assets = new ArrayList<>();
                    JsonArray items = data.getAsJsonArray("data");

                    for (int i = 0; i < items.size(); i++) {
                        JsonObject item = items.get(i).getAsJsonObject();
                        Asset asset = new Asset();

                        if (item.has("ip")) {
                            asset.setIp(item.get("ip").getAsString());
                        }

                        if (item.has("port")) {
                            asset.setPort(item.get("port").getAsInt());
                        }

                        if (item.has("hostname")) {
                            String hostname = item.get("hostname").getAsString();
                            asset.setHost(hostname);
                            if (!hostname.isEmpty()) {
                                asset.setDomains(Arrays.asList(hostname));
                            }
                        }

                        if (item.has("transport")) {
                            asset.setProtocol(item.get("transport").getAsString());
                        }

                        if (item.has("location")) {
                            JsonObject location = item.getAsJsonObject("location");
                            if (location.has("country_cn")) {
                                asset.setCountry(location.get("country_cn").getAsString());
                            }
                            if (location.has("city_cn")) {
                                asset.setCity(location.get("city_cn").getAsString());
                            }
                        }

                        if (item.has("service")) {
                            JsonObject service = item.getAsJsonObject("service");
                            if (service.has("http")) {
                                JsonObject http = service.getAsJsonObject("http");
                                if (http.has("title")) {
                                    asset.setTitle(http.get("title").getAsString());
                                }
                            }
                            if (service.has("response")) {
                                asset.setBanner(service.get("response").getAsString());
                            }
                        }

                        assets.add(asset);
                    }

                    result.setAssets(assets);
                } else {
                    String message = jsonResponse.has("message") ?
                            jsonResponse.get("message").getAsString() : "Unknown error";
                    result.setError(message);
                }
            }
        } catch (Exception e) {
            result.setError("Quake search failed: " + e.getMessage());
        }

        return result;
    }
}
