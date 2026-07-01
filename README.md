# QuerySec MCP

一款 Burp Suite 插件，通过 MCP 协议为 AI 助手提供网络空间资产搜索能力。

支持：FOFA、Shodan、Quake、Hunter、ZoomEye

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

### 2. 加载到 Burp Suite

1. 打开 Burp Suite
2. **Extender** → **Extensions** → **Add**
3. 选择下载的 JAR 文件
4. 加载成功后会显示：`MCP Server listening on http://127.0.0.1:23389`

---

## 配置

### 1. 配置 API 密钥

创建配置文件：`~/.config/querysec-mcp/config.json`

```json
{
  "apis": {
    "fofa": {
      "apiKey": "your-fofa-api-key"
    },
    "shodan": {
      "apiKey": "your-shodan-api-key"
    },
    "quake": {
      "apiKey": "your-quake-api-key"
    },
    "hunter": {
      "apiKey": "your-hunter-api-key"
    },
    "zoomeye": {
      "apiKey": "your-zoomeye-api-key"
    }
  },
  "proxy": "http://127.0.0.1:7890"
}
```

> **提示**：`proxy` 字段可选，不需要代理可删除。只配置你需要使用的搜索引擎。
> **安全说明**：API Key 只从本地配置文件读取，MCP 工具调用不接受 `api_key` 参数。

### 2. 配置 AI 工具

#### Claude Code / Claude Desktop

编辑 `~/.claude.json`：

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

```json
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

| 工具 | 说明 | 必需参数 |
|------|------|----------|
| `fofa_search` | FOFA 资产搜索 | `query` |
| `shodan_search` | Shodan 主机搜索 | `query` |
| `quake_search` | Quake 360 搜索 | `query` |
| `hunter_search` | Hunter 域名搜索 | `query` |
| `zoomeye_search` | ZoomEye 网络空间搜索 | `query` |

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

## 许可证

MIT License
