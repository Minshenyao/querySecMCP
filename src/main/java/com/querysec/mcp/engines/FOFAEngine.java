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
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FOFAEngine implements SearchEngine {
    private static final String API_URL = "https://fofa.info/api/v1/search/all";
    private final Gson gson;
    private String proxyUrl;

    public FOFAEngine() {
        this.gson = new Gson();
    }

    @Override
    public void setProxy(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    @Override
    public SearchResult search(Map<String, Object> params) {
        SearchResult result = new SearchResult();
        result.setEngine("FOFA");

        try {
            String query = (String) params.get(Constants.PARAM_QUERY);
            String apiKey = (String) params.get(Constants.PARAM_API_KEY);
            int size = params.containsKey(Constants.PARAM_SIZE) ?
                ((Number) params.get(Constants.PARAM_SIZE)).intValue() : Constants.DEFAULT_FOFA_SIZE;

            // Base64 编码查询
            String encodedQuery = Base64.getEncoder().encodeToString(query.getBytes(StandardCharsets.UTF_8));

            HttpUrl url = Objects.requireNonNull(HttpUrl.parse(API_URL)).newBuilder()
                    .addQueryParameter("key", apiKey)
                    .addQueryParameter("qbase64", encodedQuery)
                    .addQueryParameter("size", String.valueOf(size))
                    .addQueryParameter("fields", "host,ip,port,protocol,title,country,city,domain")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            OkHttpClient client = proxyUrl != null ?
                HttpClientFactory.getProxyClient(proxyUrl) :
                HttpClientFactory.getDefaultClient();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new SearchEngineException("FOFA", response.code(), getHttpErrorMessage(response.code()));
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
                    throw new SearchEngineException("FOFA", jsonResponse.get("errmsg").getAsString());
                }
            }
        } catch (SearchEngineException e) {
            result.setError(e.getUserFriendlyMessage());
        } catch (IOException e) {
            result.setError("FOFA network error: " + e.getMessage());
        } catch (JsonSyntaxException e) {
            result.setError("FOFA invalid response format");
        } catch (Exception e) {
            result.setError("FOFA unexpected error: " + e.getMessage());
        }

        return result;
    }

    private String getHttpErrorMessage(int code) {
        switch (code) {
            case 401: return "Invalid API key";
            case 403: return "API access denied";
            case 429: return "Rate limit exceeded";
            case 500: return "API server error";
            default: return "HTTP error " + code;
        }
    }
}
