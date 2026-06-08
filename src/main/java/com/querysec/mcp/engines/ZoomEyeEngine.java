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

public class ZoomEyeEngine implements SearchEngine {
    private static final String API_URL_HOST = "https://api.zoomeye.org/host/search";
    private static final String API_URL_WEB = "https://api.zoomeye.org/web/search";
    private OkHttpClient client;
    private final Gson gson;

    public ZoomEyeEngine() {
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
        result.setEngine("ZoomEye");

        try {
            String query = (String) params.get("query");
            String apiKey = (String) params.get("api_key");
            int page = params.containsKey("page") ?
                ((Number) params.get("page")).intValue() : 1;
            String type = params.containsKey("type") ?
                (String) params.get("type") : "host";

            String apiUrl = "host".equals(type) ? API_URL_HOST : API_URL_WEB;
            String url = String.format("%s?query=%s&page=%d", apiUrl, query, page);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("API-KEY", apiKey)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    result.setError("ZoomEye API error: " + response.code());
                    return result;
                }

                String body = response.body().string();
                JsonObject jsonResponse = gson.fromJson(body, JsonObject.class);

                int total = jsonResponse.get("total").getAsInt();
                result.setTotal(total);

                List<Asset> assets = new ArrayList<>();
                JsonArray matches = jsonResponse.getAsJsonArray("matches");

                for (int i = 0; i < matches.size(); i++) {
                    JsonObject match = matches.get(i).getAsJsonObject();
                    Asset asset = new Asset();

                    if (match.has("ip")) {
                        asset.setIp(match.get("ip").getAsString());
                    }

                    if (match.has("portinfo")) {
                        JsonObject portinfo = match.getAsJsonObject("portinfo");
                        if (portinfo.has("port")) {
                            asset.setPort(portinfo.get("port").getAsInt());
                        }
                        if (portinfo.has("service")) {
                            asset.setProtocol(portinfo.get("service").getAsString());
                        }
                        if (portinfo.has("banner")) {
                            asset.setBanner(portinfo.get("banner").getAsString());
                        }
                        if (portinfo.has("title")) {
                            asset.setTitle(portinfo.get("title").getAsString());
                        }
                    }

                    if (match.has("geoinfo")) {
                        JsonObject geoinfo = match.getAsJsonObject("geoinfo");
                        if (geoinfo.has("country")) {
                            JsonObject country = geoinfo.getAsJsonObject("country");
                            if (country.has("names")) {
                                JsonObject names = country.getAsJsonObject("names");
                                if (names.has("zh-CN")) {
                                    asset.setCountry(names.get("zh-CN").getAsString());
                                }
                            }
                        }
                        if (geoinfo.has("city")) {
                            JsonObject city = geoinfo.getAsJsonObject("city");
                            if (city.has("names")) {
                                JsonObject names = city.getAsJsonObject("names");
                                if (names.has("zh-CN")) {
                                    asset.setCity(names.get("zh-CN").getAsString());
                                }
                            }
                        }
                    }

                    if (match.has("rdns")) {
                        String rdns = match.get("rdns").getAsString();
                        if (!rdns.isEmpty()) {
                            asset.setHost(rdns);
                            asset.setDomains(Arrays.asList(rdns));
                        }
                    }

                    assets.add(asset);
                }

                result.setAssets(assets);
            }
        } catch (Exception e) {
            result.setError("ZoomEye search failed: " + e.getMessage());
        }

        return result;
    }
}
