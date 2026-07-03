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
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HunterEngine implements SearchEngine {
    private static final String API_URL = "https://hunter.qianxin.com/openApi/search";
    private final Gson gson;
    private String proxyUrl;

    public HunterEngine() {
        this.gson = new Gson();
    }

    @Override
    public void setProxy(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    @Override
    public SearchResult search(Map<String, Object> params) {
        SearchResult result = new SearchResult();
        result.setEngine("Hunter");

        try {
            String query = (String) params.get(Constants.PARAM_QUERY);
            String apiKey = (String) params.get(Constants.PARAM_API_KEY);
            int page = params.containsKey(Constants.PARAM_PAGE) ?
                ((Number) params.get(Constants.PARAM_PAGE)).intValue() : Constants.DEFAULT_PAGE;
            int pageSize = params.containsKey(Constants.PARAM_PAGE_SIZE) ?
                ((Number) params.get(Constants.PARAM_PAGE_SIZE)).intValue() : Constants.DEFAULT_HUNTER_PAGE_SIZE;

            // Hunter API 要求 page_size 在 10-100 之间
            if (pageSize < 10) {
                pageSize = 10;
            } else if (pageSize > 100) {
                pageSize = 100;
            }

            // Hunter API 要求 search 参数使用 Base64 编码
            String encodedQuery = Base64.getEncoder().encodeToString(query.getBytes(StandardCharsets.UTF_8));

            HttpUrl url = Objects.requireNonNull(HttpUrl.parse(API_URL)).newBuilder()
                    .addQueryParameter("api-key", apiKey)
                    .addQueryParameter("search", encodedQuery)
                    .addQueryParameter("page", String.valueOf(page))
                    .addQueryParameter("page_size", String.valueOf(pageSize))
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
                    throw new SearchEngineException("Hunter", response.code(), getHttpErrorMessage(response.code()));
                }

                String body = response.body().string();
                JsonObject jsonResponse = gson.fromJson(body, JsonObject.class);

                if (jsonResponse.get("code").getAsInt() == 200) {
                    JsonObject data = jsonResponse.getAsJsonObject("data");
                    int total = data.get("total").getAsInt();
                    result.setTotal(total);

                    List<Asset> assets = new ArrayList<>();
                    JsonArray arr = data.getAsJsonArray("arr");

                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject item = arr.get(i).getAsJsonObject();
                        Asset asset = new Asset();

                        if (item.has("ip")) {
                            asset.setIp(item.get("ip").getAsString());
                        }

                        if (item.has("port")) {
                            asset.setPort(item.get("port").getAsInt());
                        }

                        if (item.has("domain")) {
                            String domain = item.get("domain").getAsString();
                            asset.setHost(domain);
                            asset.setDomains(Arrays.asList(domain));
                        }

                        if (item.has("protocol")) {
                            asset.setProtocol(item.get("protocol").getAsString());
                        }

                        if (item.has("web_title")) {
                            asset.setTitle(item.get("web_title").getAsString());
                        }

                        if (item.has("country")) {
                            asset.setCountry(item.get("country").getAsString());
                        }

                        if (item.has("city")) {
                            asset.setCity(item.get("city").getAsString());
                        }

                        if (item.has("banner")) {
                            asset.setBanner(item.get("banner").getAsString());
                        }

                        Map<String, Object> extra = new HashMap<>();
                        if (item.has("company")) {
                            extra.put("company", item.get("company").getAsString());
                        }
                        if (item.has("isp")) {
                            extra.put("isp", item.get("isp").getAsString());
                        }
                        asset.setExtra(extra);

                        assets.add(asset);
                    }

                    result.setAssets(assets);
                } else {
                    String message = jsonResponse.has("message") ?
                            jsonResponse.get("message").getAsString() : "Unknown error";
                    throw new SearchEngineException("Hunter", message);
                }
            }
        } catch (SearchEngineException e) {
            result.setError(e.getUserFriendlyMessage());
        } catch (IOException e) {
            result.setError("Hunter network error: " + e.getMessage());
        } catch (JsonSyntaxException e) {
            result.setError("Hunter invalid response format");
        } catch (Exception e) {
            result.setError("Hunter unexpected error: " + e.getMessage());
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
