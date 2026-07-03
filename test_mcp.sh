#!/bin/bash
# QuerySec MCP 测试脚本

echo "=== QuerySec MCP 可用性测试 ==="
echo ""

# 1. 健康检查
echo "1. 健康检查..."
HEALTH=$(curl -s http://127.0.0.1:23389/health)
if [ "$HEALTH" = "OK" ]; then
    echo "   ✅ 服务健康状态: OK"
else
    echo "   ❌ 服务未响应"
    exit 1
fi
echo ""

# 2. SSE 连接测试
echo "2. SSE 连接测试..."
timeout 2 curl -s -N http://127.0.0.1:23389/ -H "Accept: text/event-stream" > /tmp/sse_test.txt 2>&1
if grep -q "event: endpoint" /tmp/sse_test.txt; then
    SESSION_ID=$(grep "data: /messages" /tmp/sse_test.txt | sed 's/.*sessionId=\(.*\)/\1/')
    echo "   ✅ SSE 连接成功"
    echo "   Session ID: $SESSION_ID"
else
    echo "   ❌ SSE 连接失败"
    exit 1
fi
echo ""

# 3. 初始化请求
echo "3. MCP 初始化..."
INIT_RESPONSE=$(curl -s -X POST "http://127.0.0.1:23389/messages?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    }
  }')

if echo "$INIT_RESPONSE" | grep -q "202"; then
    echo "   ✅ 初始化请求已接受"
else
    echo "   ❌ 初始化失败"
    exit 1
fi
sleep 1
echo ""

# 4. 工具列表请求
echo "4. 获取工具列表..."
TOOLS_RESPONSE=$(curl -s -X POST "http://127.0.0.1:23389/messages?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }')

if echo "$TOOLS_RESPONSE" | grep -q "202"; then
    echo "   ✅ 工具列表请求已接受"
else
    echo "   ❌ 工具列表获取失败"
    exit 1
fi
sleep 1
echo ""

# 5. FOFA 搜索测试
echo "5. FOFA 搜索测试（title=\"test\"）..."
SEARCH_RESPONSE=$(curl -s -X POST "http://127.0.0.1:23389/messages?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "fofa_search",
      "arguments": {
        "query": "title=\"test\"",
        "size": 3
      }
    }
  }')

if echo "$SEARCH_RESPONSE" | grep -q "202"; then
    echo "   ✅ FOFA 搜索请求已接受"
    echo "   （结果通过 SSE 返回，需要在 Burp Suite 日志中查看）"
else
    echo "   ❌ FOFA 搜索失败"
    exit 1
fi
echo ""

echo "=== 测试完成 ==="
echo ""
echo "📊 测试结果总结："
echo "   ✅ 健康检查"
echo "   ✅ SSE 连接"
echo "   ✅ MCP 协议初始化"
echo "   ✅ 工具列表获取"
echo "   ✅ FOFA 搜索调用"
echo ""
echo "🔍 查看详细结果："
echo "   1. 打开 Burp Suite"
echo "   2. Extender → Extensions → QuerySec MCP → Output"
echo "   3. 查看搜索结果和日志"
echo ""
echo "💡 提示：如需在 Claude Code 中使用，请配置 MCP 服务器："
echo "   编辑 ~/.claude/settings.json 添加："
echo '   "mcpServers": {'
echo '     "querysec": {'
echo '       "type": "sse",'
echo '       "url": "http://127.0.0.1:23389"'
echo '     }'
echo '   }'
