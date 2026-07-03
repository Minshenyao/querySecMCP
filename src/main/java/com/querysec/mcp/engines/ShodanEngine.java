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
import java.util.*;

public class ShodanEngine implements SearchEngine {
    private static final String API_URL = "https://api.shodan.io/shodan/host/search";
    private final Gson gson;
    private String proxyUrl;

    public ShodanEngine() {
        this.gson = new Gson();
    }

    @Override
    public void setProxy(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    @Override
    public SearchResult search(Map<String, Object> params) {
        SearchResult result = new SearchResult();
        result.setEngine("Shodan");

        try {
            String query = (String) params.get(Constants.PARAM_QUERY);
            String apiKey = (String) params.get(Constants.PARAM_API_KEY);
            int page = params.containsKey(Constants.PARAM_PAGE) ?
                ((Number) params.get(Constants.PARAM_PAGE)).intValue() : Constants.DEFAULT_PAGE;

            HttpUrl url = Objects.requireNonNull(HttpUrl.parse(API_URL)).newBuilder()
                    .addQueryParameter("key", apiKey)
                    .addQueryParameter("query", query)
                    .addQueryParameter("page", String.valueOf(page))
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
                    throw new SearchEngineException("Shodan", response.code(), getHttpErrorMessage(response.code()));
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

                    // 必填字段
                    if (match.has("ip_str") && !match.get("ip_str").isJsonNull()) {
                        asset.setIp(match.get("ip_str").getAsString());
                    }
                    if (match.has("port") && !match.get("port").isJsonNull()) {
                        asset.setPort(match.get("port").getAsInt());
                    }

                    // 可选字段：hostnames
                    if (match.has("hostnames") && !match.get("hostnames").isJsonNull()) {
                        JsonArray hostnames = match.getAsJsonArray("hostnames");
                        List<String> domains = new ArrayList<>();
                        for (int j = 0; j < hostnames.size(); j++) {
                            if (!hostnames.get(j).isJsonNull()) {
                                domains.add(hostnames.get(j).getAsString());
                            }
                        }
                        asset.setDomains(domains);
                        if (!domains.isEmpty()) {
                            asset.setHost(domains.get(0));
                        }
                    }

                    // 可选字段：transport
                    if (match.has("transport") && !match.get("transport").isJsonNull()) {
                        asset.setProtocol(match.get("transport").getAsString());
                    }

                    // 可选字段：data（banner）
                    if (match.has("data") && !match.get("data").isJsonNull()) {
                        asset.setBanner(match.get("data").getAsString());
                    }

                    // 可选字段：location
                    if (match.has("location") && !match.get("location").isJsonNull()) {
                        JsonObject location = match.getAsJsonObject("location");
                        if (location.has("country_name") && !location.get("country_name").isJsonNull()) {
                            asset.setCountry(location.get("country_name").getAsString());
                        }
                        if (location.has("city") && !location.get("city").isJsonNull()) {
                            asset.setCity(location.get("city").getAsString());
                        }
                    }

                    // 可选字段：org 和 isp
                    Map<String, Object> extra = new HashMap<>();
                    if (match.has("org") && !match.get("org").isJsonNull()) {
                        extra.put("org", match.get("org").getAsString());
                    }
                    if (match.has("isp") && !match.get("isp").isJsonNull()) {
                        extra.put("isp", match.get("isp").getAsString());
                    }
                    if (!extra.isEmpty()) {
                        asset.setExtra(extra);
                    }

                    assets.add(asset);
                }

                result.setAssets(assets);
            }
        } catch (SearchEngineException e) {
            result.setError(e.getUserFriendlyMessage());
        } catch (IOException e) {
            result.setError("Shodan network error: " + e.getMessage());
        } catch (JsonSyntaxException e) {
            result.setError("Shodan invalid response format");
        } catch (Exception e) {
            result.setError("Shodan unexpected error: " + e.getMessage());
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
