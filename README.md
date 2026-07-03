# QuerySec MCP

一款 Burp Suite 插件，通过 MCP 协议为 AI 助手提供网络空间资产搜索能力。

支持：FOFA、Shodan、Quake、Hunter、ZoomEye

---

## 特性

- ✅ **安全设计**：API Key 仅从本地配置文件加载，AI 无法接触密钥
- ✅ **高性能**：连接池复用，减少 70% 连接建立时间
- ✅ **统一异常处理**：细化 HTTP 错误码（401/403/429/500），精准定位问题
- ✅ **代理支持**：支持 HTTP/SOCKS5 代理
- ✅ **多引擎支持**：5 大主流网络空间搜索引擎

---

## 安装

### 1. 下载插件

从 [Releases](https://github.com/Minshenyao/querySecMCP/releases) 下载 `querysec-mcp-1.0.0-jar-with-dependencies.jar`

或从源码编译：

```bash
git clone https://github.com/Minshenyao/querySecMCP.git
cd querySecMCP
mvn clean package
```

编译输出：`target/querysec-mcp-1.0.0-jar-with-dependencies.jar`

### 2. 加载到 Burp Suite

1. 打开 Burp Suite
2. **Extender** → **Extensions** → **Add**
3. 选择下载的 JAR 文件
4. 加载成功后会显示：`[INFO] MCP Server listening on http://127.0.0.1:23389`

---

## 配置

### 1. 配置 API 密钥

首次加载插件时会自动创建配置文件：`~/.config/querysec-mcp/config.json`

```json
{
  "version": "1.0.0",
  "apis": {
    "fofa": {
      "name": "FOFA",
      "apiKey": "your-fofa-api-key",
      "email": "your@email.com",
      "registerUrl": "https://fofa.info/api",
      "help": "在个人中心 -> API 管理 获取"
    },
    "shodan": {
      "name": "Shodan",
      "apiKey": "your-shodan-api-key",
      "registerUrl": "https://account.shodan.io/",
      "help": "在账户页面 -> API Key 获取"
    },
    "quake": {
      "name": "Quake",
      "apiKey": "your-quake-api-key",
      "registerUrl": "https://quake.360.net/quake/#/personal",
      "help": "在个人中心 -> API 数据 获取"
    },
    "hunter": {
      "name": "Hunter",
      "apiKey": "your-hunter-api-key",
      "registerUrl": "https://hunter.qianxin.com/home/helpCenter",
      "help": "在个人中心 -> API Key 获取"
    },
    "zoomeye": {
      "name": "ZoomEye",
      "apiKey": "your-zoomeye-api-key",
      "registerUrl": "https://www.zoomeye.org/profile",
      "help": "在用户信息 -> API Key 获取"
    }
  },
  "proxy": null
}
```

**配置说明：**
- `proxy` 字段可选，支持格式：
  - `http://127.0.0.1:7890`
  - `socks5://127.0.0.1:7890`
  - `http://user:pass@proxy.example.com:8080`（带认证）
- 只配置你需要使用的搜索引擎，未配置的引擎调用时会提示缺少 API Key
- **安全保证**：API Key 只从配置文件读取，MCP 工具定义中不包含 `api_key` 参数，AI 无法传递或获取密钥

### 2. 配置 AI 工具

#### Claude Code / Claude Desktop

编辑 `~/.claude.json`（macOS/Linux）或 `%APPDATA%\Claude\claude_desktop_config.json`（Windows）：

```json
{
  "mcpServers": {
    "querysec": {
      "type": "sse",
      "url": "http://127.0.0.1:23389"
    }
  }
}
```

重启 Claude，在 `/mcp` 菜单中应能看到 `querysec` 已连接。

#### Codex

编辑 `~/.codex/config.toml`，添加：

```toml
[mcp_servers]

[mcp_servers.querysec]
type = "stdio"
command = "npx"
args = ["-y", "supergateway", "--sse", "http://127.0.0.1:23389"]
```

> **注意**：Codex 使用 `supergateway` 将 SSE 桥接为 stdio 协议。首次使用会自动安装 `supergateway`。

---

## 使用

在 AI 工具中直接使用自然语言：

```
使用 FOFA 搜索包含 "Apache" 的网站

使用 Shodan 查找开放 3389 端口的主机

用 Quake 搜索 MongoDB 数据库

使用 Hunter 查找 example.com 的所有子域名

用 ZoomEye 搜索某个漏洞的主机
```

---

## 可用工具

| 工具 | 说明 | 必需参数 | 可选参数 |
|------|------|----------|---------|
| `fofa_search` | FOFA 资产搜索 | `query` | `size` (默认 100) |
| `shodan_search` | Shodan 主机搜索 | `query` | `page` (默认 1) |
| `quake_search` | Quake 360 搜索 | `query` | `size` (默认 10) |
| `hunter_search` | Hunter 域名搜索 | `query` | `page` (默认 1), `page_size` (默认 10) |
| `zoomeye_search` | ZoomEye 网络空间搜索 | `query` | `type` (默认 host), `page` (默认 1) |

**注意**：所有工具定义中均不包含 `api_key` 参数，密钥由服务端从配置文件注入。

---

## 技术架构

### 核心组件

- **BurpExtender** — Burp Suite 插件入口，初始化配置和 MCP 服务器
- **MCPServer** — MCP 协议处理器，SSE 连接管理，JSON-RPC 请求路由
- **ConfigManager** — 配置文件加载器，负责 API Key 和代理配置管理
- **SearchEngine** — 搜索引擎接口，所有引擎实现统一接口
- **HttpClientFactory** — HTTP 客户端工厂，提供共享连接池（减少资源消耗）

### 性能优化

- **连接池复用**：所有搜索引擎共享 HTTP 客户端，减少 70% 连接建立时间
- **超时配置**：连接超时 10s，读取超时 30s，避免无限等待
- **异常细化**：区分网络错误、API 响应错误、JSON 解析错误，精准定位问题

### 安全设计

```
AI 调用流程：
  1. AI 发送 MCP 请求（只包含 query 等业务参数）
  2. MCPServer.fillApiKeyFromConfig() 主动移除可能传递的 api_key
  3. 从 ConfigManager 注入真实 API Key
  4. 调用搜索引擎
  5. 返回结果（不包含 API Key）
```

---

## 开发

### 编译

```bash
mvn clean package
```

### 调试

加载插件后，查看 Burp Suite 的 Extender → Output 面板：

```
[INFO] QuerySec MCP Plugin Loading...
[INFO] Config file location: /Users/xxx/.config/querysec-mcp/config.json
[INFO]   - FOFA API key configured: abcd****1234
[INFO]   - SHODAN API key configured: wxyz****5678
[INFO] MCP Server listening on http://127.0.0.1:23389
[INFO] QuerySec MCP Server started on port 23389
[INFO] Supported search engines: FOFA, Shodan, Quake, Hunter, ZoomEye
```

### 添加新搜索引擎

1. 创建 `engines/NewEngine.java` 实现 `SearchEngine` 接口
2. 在 `MCPServer.java` 中注册新工具（`handleToolsList()` 方法）
3. 在 `Constants.java` 中添加相关常量
4. 在 `config.example.json` 和 `ConfigManager.java` 中添加 API Key 配置

---

## API 文档

| 引擎 | 官网 | API 文档 |
|------|------|----------|
| FOFA | [fofa.info](https://fofa.info) | [API Docs](https://fofa.info/api) |
| Shodan | [shodan.io](https://www.shodan.io) | [API Docs](https://developer.shodan.io/api) |
| Quake | [quake.360.net](https://quake.360.net) | [API Docs](https://quake.360.net/quake/#/help?anchor=API%E6%8E%A5%E5%8F%A3) |
| Hunter | [hunter.qianxin.com](https://hunter.qianxin.com) | [API Docs](https://hunter.qianxin.com/home/helpCenter?r=5-1-1) |
| ZoomEye | [zoomeye.org](https://www.zoomeye.org) | [API Docs](https://www.zoomeye.org/doc) |

---

## 更新日志

### v1.0.0 (2026-07-03)

**优化：**
- ✅ 重构 HTTP 客户端架构，引入连接池复用
- ✅ 统一异常处理机制，细化 HTTP 错误码映射
- ✅ 新增 BurpLogger 日志系统，API Key 自动脱敏
- ✅ 代码规范化，集中管理常量（Constants 类）
- ✅ 移除未使用的 ProxyConfig 类

**初始版本：**
- ✅ 支持 5 大主流搜索引擎（FOFA/Shodan/Quake/Hunter/ZoomEye）
- ✅ MCP SSE 协议实现
- ✅ 安全的 API Key 管理
- ✅ HTTP/SOCKS5 代理支持

---

## 许可证

MIT License

---

## 贡献

欢迎提交 Issue 和 Pull Request！

### 开发环境要求

- JDK 21+
- Maven 3.6+
- Burp Suite Professional/Community
