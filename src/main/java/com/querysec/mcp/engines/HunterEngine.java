package com.querysec.mcp.engines;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.querysec.mcp.model.Asset;
import com.querysec.mcp.model.SearchResult;
import com.querysec.mcp.utils.ProxyHelper;
import okhttp3.*;

import java.util.*;

public class HunterEngine implements SearchEngine {
    private static final String API_URL = "https://hunter.qianxin.com/openApi/search";
    private OkHttpClient client;
    private final Gson gson;

    public HunterEngine() {
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
        result.setEngine("Hunter");

        try {
            String query = (String) params.get("query");
            String apiKey = (String) params.get("api_key");
            int page = params.containsKey("page") ?
                ((Number) params.get("page")).intValue() : 1;
            int pageSize = params.containsKey("page_size") ?
                ((Number) params.get("page_size")).intValue() : 10;

            String url = String.format("%s?api-key=%s&search=%s&page=%d&page_size=%d",
                    API_URL, apiKey, query, page, pageSize);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    result.setError("Hunter API error: " + response.code());
                    return result;
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
                    result.setError(message);
                }
            }
        } catch (Exception e) {
            result.setError("Hunter search failed: " + e.getMessage());
        }

        return result;
    }
}
