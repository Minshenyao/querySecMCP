package com.querysec.mcp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.config/querysec-mcp";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, APIConfig> apiConfigs;
    private String proxyUrl;

    public ConfigManager() {
        this.apiConfigs = new HashMap<>();
        this.proxyUrl = null;
        loadConfig();
    }

    public void loadConfig() {
        try {
            Path configPath = Paths.get(CONFIG_FILE);

            if (Files.exists(configPath)) {
                // 读取现有配置
                String content = Files.readString(configPath);
                Config config = gson.fromJson(content, Config.class);
                if (config != null) {
                    if (config.apis != null) {
                        this.apiConfigs = config.apis;
                    }
                    if (config.proxy != null) {
                        this.proxyUrl = config.proxy;
                    }
                }
            } else {
                // 创建默认配置
                createDefaultConfig();
            }
        } catch (Exception e) {
            System.err.println("Failed to load config: " + e.getMessage());
            createDefaultConfig();
        }
    }

    private void createDefaultConfig() {
        try {
            // 创建配置目录
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            // 创建默认配置
            apiConfigs = new HashMap<>();

            apiConfigs.put("fofa", new APIConfig(
                "FOFA",
                "",
                "your@email.com",
                "https://fofa.info/api",
                "在个人中心 -> API 管理 获取"
            ));

            apiConfigs.put("shodan", new APIConfig(
                "Shodan",
                "",
                null,
                "https://account.shodan.io/",
                "在账户页面 -> API Key 获取"
            ));

            apiConfigs.put("quake", new APIConfig(
                "Quake",
                "",
                null,
                "https://quake.360.net/quake/#/personal",
                "在个人中心 -> API 数据 获取"
            ));

            apiConfigs.put("hunter", new APIConfig(
                "Hunter",
                "",
                null,
                "https://hunter.qianxin.com/home/helpCenter",
                "在个人中心 -> API Key 获取"
            ));

            apiConfigs.put("zoomeye", new APIConfig(
                "ZoomEye",
                "",
                null,
                "https://www.zoomeye.org/profile",
                "在用户信息 -> API Key 获取"
            ));

            // 创建默认代理配置
            proxyUrl = null;  // 默认不使用代理，示例: "http://127.0.0.1:7890" 或 "socks5://127.0.0.1:7890"

            saveConfig();

            System.out.println("Created default config file at: " + CONFIG_FILE);
            System.out.println("Please edit the config file to add your API keys.");
        } catch (Exception e) {
            System.err.println("Failed to create default config: " + e.getMessage());
        }
    }

    public void saveConfig() {
        try {
            Config config = new Config();
            config.version = "1.0.0";
            config.proxy = proxyUrl;
            config.apis = apiConfigs;

            String json = gson.toJson(config);
            Files.writeString(Paths.get(CONFIG_FILE), json);
        } catch (Exception e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public APIConfig getAPIConfig(String engine) {
        return apiConfigs.get(engine.toLowerCase());
    }

    public String getAPIKey(String engine) {
        APIConfig config = getAPIConfig(engine);
        return config != null ? config.apiKey : null;
    }

    public String getEmail(String engine) {
        APIConfig config = getAPIConfig(engine);
        return config != null ? config.email : null;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    public boolean hasProxy() {
        return proxyUrl != null && !proxyUrl.isEmpty();
    }

    public boolean hasValidConfig(String engine) {
        APIConfig config = getAPIConfig(engine);
        return config != null && config.apiKey != null && !config.apiKey.isEmpty();
    }

    public String getConfigFilePath() {
        return CONFIG_FILE;
    }

    // 内部类
    private static class Config {
        String version;
        String proxy;
        Map<String, APIConfig> apis;
    }

    public static class APIConfig {
        String name;
        String apiKey;
        String email;
        String registerUrl;
        String help;

        public APIConfig() {
        }

        public APIConfig(String name, String apiKey, String email, String registerUrl, String help) {
            this.name = name;
            this.apiKey = apiKey;
            this.email = email;
            this.registerUrl = registerUrl;
            this.help = help;
        }

        public String getName() {
            return name;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getEmail() {
            return email;
        }

        public String getRegisterUrl() {
            return registerUrl;
        }

        public String getHelp() {
            return help;
        }
    }

    public static class ProxyConfig {
        public boolean enabled;
        public String type;  // "http", "socks5"
        public String host;
        public int port;
        public String username;
        public String password;

        public ProxyConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getType() {
            return type;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public boolean hasAuth() {
            return username != null && !username.isEmpty() &&
                   password != null && !password.isEmpty();
        }
    }
}
