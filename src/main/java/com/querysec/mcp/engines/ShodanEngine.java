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

public class ShodanEngine implements SearchEngine {
    private static final String API_URL = "https://api.shodan.io/shodan/host/search";
    private OkHttpClient client;
    private final Gson gson;

    public ShodanEngine() {
        this.gson = new Gson();
        this.client = ProxyHelper.createClient((String) null);
    }

    @Override
    public void setProxy(String proxyUrl) {
        this.client = ProxyHelper.createClient(proxyUrl);
    }

    @Override
    public SearchResult search(Map<String, Object> params) {
        SearchResult result = new SearchResult();
        result.setEngine("Shodan");

        try {
            String query = (String) params.get("query");
            String apiKey = (String) params.get("api_key");
            int page = params.containsKey("page") ?
                ((Number) params.get("page")).intValue() : 1;

            String url = String.format("%s?key=%s&query=%s&page=%d",
                    API_URL, apiKey, query, page);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    result.setError("Shodan API error: " + response.code());
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

                    asset.setIp(match.get("ip_str").getAsString());
                    asset.setPort(match.get("port").getAsInt());

                    if (match.has("hostnames")) {
                        JsonArray hostnames = match.getAsJsonArray("hostnames");
                        List<String> domains = new ArrayList<>();
                        for (int j = 0; j < hostnames.size(); j++) {
                            domains.add(hostnames.get(j).getAsString());
                        }
                        asset.setDomains(domains);
                        if (!domains.isEmpty()) {
                            asset.setHost(domains.get(0));
                        }
                    }

                    if (match.has("transport")) {
                        asset.setProtocol(match.get("transport").getAsString());
                    }

                    if (match.has("data")) {
                        asset.setBanner(match.get("data").getAsString());
                    }

                    if (match.has("location")) {
                        JsonObject location = match.getAsJsonObject("location");
                        if (location.has("country_name")) {
                            asset.setCountry(location.get("country_name").getAsString());
                        }
                        if (location.has("city")) {
                            asset.setCity(location.get("city").getAsString());
                        }
                    }

                    Map<String, Object> extra = new HashMap<>();
                    if (match.has("org")) {
                        extra.put("org", match.get("org").getAsString());
                    }
                    if (match.has("isp")) {
                        extra.put("isp", match.get("isp").getAsString());
                    }
                    asset.setExtra(extra);

                    assets.add(asset);
                }

                result.setAssets(assets);
            }
        } catch (Exception e) {
            result.setError("Shodan search failed: " + e.getMessage());
        }

        return result;
    }
}
