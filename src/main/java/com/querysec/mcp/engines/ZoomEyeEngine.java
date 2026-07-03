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

public class ZoomEyeEngine implements SearchEngine {
    // 使用中国区 API
    private static final String API_URL_HOST = "https://api.zoomeye.org/host/search";
    private static final String API_URL_WEB = "https://api.zoomeye.org/web/search";
    private final Gson gson;
    private String proxyUrl;

    public ZoomEyeEngine() {
        this.gson = new Gson();
    }

    @Override
    public void setProxy(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    @Override
    public SearchResult search(Map<String, Object> params) {
        SearchResult result = new SearchResult();
        result.setEngine("ZoomEye");

        try {
            String query = (String) params.get(Constants.PARAM_QUERY);
            String apiKey = (String) params.get(Constants.PARAM_API_KEY);
            int page = params.containsKey(Constants.PARAM_PAGE) ?
                ((Number) params.get(Constants.PARAM_PAGE)).intValue() : Constants.DEFAULT_PAGE;
            String type = params.containsKey(Constants.PARAM_TYPE) ?
                (String) params.get(Constants.PARAM_TYPE) : "host";

            String apiUrl = "web".equalsIgnoreCase(type) ? API_URL_WEB : API_URL_HOST;
            HttpUrl url = Objects.requireNonNull(HttpUrl.parse(apiUrl)).newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter("page", String.valueOf(page))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("API-KEY", apiKey)
                    .build();

            OkHttpClient client = proxyUrl != null ?
                HttpClientFactory.getProxyClient(proxyUrl) :
                HttpClientFactory.getDefaultClient();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new SearchEngineException("ZoomEye", response.code(), getHttpErrorMessage(response.code()));
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

                        // country 可能是对象或字符串
                        if (geoinfo.has("country")) {
                            if (geoinfo.get("country").isJsonObject()) {
                                JsonObject country = geoinfo.getAsJsonObject("country");
                                if (country.has("names")) {
                                    JsonObject names = country.getAsJsonObject("names");
                                    if (names.has("zh-CN")) {
                                        asset.setCountry(names.get("zh-CN").getAsString());
                                    } else if (names.has("en")) {
                                        asset.setCountry(names.get("en").getAsString());
                                    }
                                }
                            } else if (geoinfo.get("country").isJsonPrimitive()) {
                                asset.setCountry(geoinfo.get("country").getAsString());
                            }
                        }

                        // city 可能是对象或字符串
                        if (geoinfo.has("city")) {
                            if (geoinfo.get("city").isJsonObject()) {
                                JsonObject city = geoinfo.getAsJsonObject("city");
                                if (city.has("names")) {
                                    JsonObject names = city.getAsJsonObject("names");
                                    if (names.has("zh-CN")) {
                                        asset.setCity(names.get("zh-CN").getAsString());
                                    } else if (names.has("en")) {
                                        asset.setCity(names.get("en").getAsString());
                                    }
                                }
                            } else if (geoinfo.get("city").isJsonPrimitive()) {
                                asset.setCity(geoinfo.get("city").getAsString());
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
        } catch (SearchEngineException e) {
            result.setError(e.getUserFriendlyMessage());
        } catch (IOException e) {
            result.setError("ZoomEye network error: " + e.getMessage());
        } catch (JsonSyntaxException e) {
            result.setError("ZoomEye invalid response format");
        } catch (Exception e) {
            result.setError("ZoomEye unexpected error: " + e.getMessage());
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
