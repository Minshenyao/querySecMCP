package com.querysec.mcp.engines;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.querysec.mcp.common.Constants;
import com.querysec.mcp.exception.SearchEngineException;
import com.querysec.mcp.model.Asset;
import com.querysec.mcp.model.SearchResult;
import com.querysec.mcp.utils.HttpClientFactory;
import okhttp3.*;

import java.io.IOException;
import java.util.*;

public class QuakeEngine implements SearchEngine {
    private static final String API_URL = "https://quake.360.net/api/v3/search/quake_service";
    private final Gson gson;
    private String proxyUrl;

    public QuakeEngine() {
        this.gson = new Gson();
    }

    @Override
    public void setProxy(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    @Override
    public SearchResult search(Map<String, Object> params) {
        SearchResult result = new SearchResult();
        result.setEngine("Quake");

        try {
            String query = (String) params.get(Constants.PARAM_QUERY);
            String apiKey = (String) params.get(Constants.PARAM_API_KEY);
            int size = params.containsKey(Constants.PARAM_SIZE) ?
                ((Number) params.get(Constants.PARAM_SIZE)).intValue() : Constants.DEFAULT_QUAKE_SIZE;

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

            OkHttpClient client = proxyUrl != null ?
                HttpClientFactory.getProxyClient(proxyUrl) :
                HttpClientFactory.getDefaultClient();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new SearchEngineException("Quake", response.code(), getHttpErrorMessage(response.code()));
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                if (jsonResponse.has("code") && jsonResponse.get("code").getAsInt() == 0) {
                    // Quake API v3 返回格式：data 是数组，total 在 meta.pagination.total
                    List<Asset> assets = new ArrayList<>();

                    // 获取总数
                    if (jsonResponse.has("meta")) {
                        JsonObject meta = jsonResponse.getAsJsonObject("meta");
                        if (meta.has("pagination")) {
                            JsonObject pagination = meta.getAsJsonObject("pagination");
                            if (pagination.has("total")) {
                                // Quake 的 total 可能超过 int 范围，使用 long 然后转换
                                long total = pagination.get("total").getAsLong();
                                // 如果超过 int 最大值，设为 int 最大值
                                result.setTotal(total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total);
                            }
                        }
                    }

                    // data 直接是数组
                    if (jsonResponse.has("data") && jsonResponse.get("data").isJsonArray()) {
                        JsonArray items = jsonResponse.getAsJsonArray("data");

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
                    }

                    result.setAssets(assets);
                } else {
                    String message = jsonResponse.has("message") ?
                            jsonResponse.get("message").getAsString() : "Unknown error";
                    throw new SearchEngineException("Quake", message);
                }
            }
        } catch (SearchEngineException e) {
            result.setError(e.getUserFriendlyMessage());
        } catch (IOException e) {
            result.setError("Quake network error: " + e.getMessage());
        } catch (JsonSyntaxException e) {
            result.setError("Quake invalid response format");
        } catch (Exception e) {
            result.setError("Quake unexpected error: " + e.getMessage());
        }

        return result;
    }

    private String getHttpErrorMessage(int code) {
        switch (code) {
            case 401: return "Invalid API key";
            case 403: return "API access denied or insufficient credit";
            case 429: return "Rate limit exceeded";
            case 500: return "API server error";
            default: return "HTTP error " + code;
        }
    }
}
