package com.subscheck.apk.parser

import android.util.Base64
import android.util.Log
import org.yaml.snakeyaml.Yaml
import com.subscheck.apk.model.Proxy
import java.net.URLDecoder

/**
 * Parses subscription URLs to extract proxy nodes.
 * Supports: Clash YAML, V2Ray/URI, base64 encoded URIs.
 */
object SubscriptionParser {

    private const val TAG = "SubscriptionParser"

    /**
     * Parse subscription content into proxy list.
     * Returns a pair of (proxies, debugMessage).
     */
    data class ParseResult(val proxies: List<Proxy>, val debugInfo: String)

    fun parse(content: String): ParseResult {
        val trimmed = content.trim()
        val debug = StringBuilder()
        debug.append("内容长度: ${trimmed.length}, 开头: ${trimmed.take(60).replace('\n', ' ')}\n")

        // Try YAML format FIRST (most common for GitHub subscriptions)
        if (trimmed.contains("proxies:") || trimmed.contains("type:")) {
            debug.append("→ 检测到 YAML 格式\n")
            val proxies = parseYamlSubscription(trimmed, debug)
            return ParseResult(proxies, debug.toString())
        }

        // Try to detect if it's base64 encoded
        if (looksLikeBase64(trimmed)) {
            debug.append("→ 尝试 Base64 解码\n")
            try {
                val decoded = String(Base64.decode(trimmed, Base64.DEFAULT))
                debug.append("  解码后长度: ${decoded.length}\n")
                if (decoded.contains("://")) {
                    debug.append("  → URI 格式\n")
                    return ParseResult(parseUriSubscription(decoded), debug.toString())
                }
                debug.append("  → YAML 格式\n")
                return ParseResult(parseYamlSubscription(decoded, debug), debug.toString())
            } catch (e: Exception) {
                debug.append("  Base64 解码失败: ${e.message}\n")
            }
        }

        // Try URI format
        if (trimmed.contains("://")) {
            debug.append("→ 检测到 URI 格式\n")
            return ParseResult(parseUriSubscription(trimmed), debug.toString())
        }

        debug.append("→ 未知格式，无法解析\n")
        return ParseResult(emptyList(), debug.toString())
    }

    private fun looksLikeBase64(content: String): Boolean {
        if (content.length < 10) return false
        return content.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' ||
            it == '+' || it == '/' || it == '=' }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseYamlSubscription(content: String, debug: StringBuilder): List<Proxy> {
        val proxies = mutableListOf<Proxy>()
        try {
            val yaml = Yaml()
            val data = yaml.load(content)
            debug.append("  YAML 根类型: ${data?.javaClass?.simpleName}\n")

            if (data !is Map<*, *>) {
                debug.append("   根节点不是 Map\n")
                return emptyList()
            }

            val proxyList = data["proxies"]
            debug.append("  proxies 类型: ${proxyList?.javaClass?.simpleName}, 数量: ${(proxyList as? List<*>)?.size}\n")

            if (proxyList !is List<*>) {
                debug.append("  ✗ 没有找到 proxies 列表\n")
                return emptyList()
            }

            var parsedCount = 0
            var skippedType = 0
            var skippedServer = 0
            var skippedPort = 0
            var skippedItem = 0

            for ((idx, item) in proxyList.withIndex()) {
                try {
                    if (item !is Map<*, *>) {
                        skippedItem++
                        continue
                    }
                    val type = (item["type"] as? String)?.lowercase()
                    if (type == null) {
                        skippedType++
                        continue
                    }
                    val server = item["server"] as? String
                    if (server == null) {
                        skippedServer++
                        continue
                    }
                    val port = parsePort(item["port"])
                    if (port == null) {
                        skippedPort++
                        continue
                    }

                    val name = item["name"] as? String ?: type
                    val rawData = item as Map<String, Any>
                    proxies.add(Proxy(type, name, server, port, rawData))
                    parsedCount++
                } catch (e: Exception) {
                    // skip
                }
            }

            debug.append("  解析结果: 总数=${proxyList.size}, 成功=$parsedCount, " +
                "无type=$skippedType, 无server=$skippedServer, 端口无效=$skippedPort, 非Map=$skippedItem\n")
        } catch (e: Exception) {
            debug.append("   YAML 解析异常: ${e.message}\n")
        }
        return proxies
    }

    private fun parsePort(value: Any?): Int? {
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    /**
     * Parse URI format subscription (v2ray format).
     * Supports: ss://, vmess://, trojan://, vless://, hysteria2://, hy2://, ssr://
     */
    private fun parseUriSubscription(content: String): List<Proxy> {
        val proxies = mutableListOf<Proxy>()
        val lines = content.split("\n", "\r\n").filter { it.isNotBlank() && it.contains("://") }

        for (line in lines) {
            try {
                val proxy = parseUriLine(line.trim())
                if (proxy != null) proxies.add(proxy)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse URI line: ${e.message}")
            }
        }
        return proxies
    }

    private fun parseUriLine(line: String): Proxy? {
        return when {
            line.startsWith("ss://") -> parseSS(line)
            line.startsWith("ssr://") -> parseSSR(line)
            line.startsWith("vmess://") -> parseVmess(line)
            line.startsWith("trojan://") -> parseTrojan(line)
            line.startsWith("vless://") -> parseVless(line)
            line.startsWith("hysteria2://") || line.startsWith("hy2://") -> parseHysteria2(line)
            line.startsWith("tuic://") -> parseTuic(line)
            else -> null
        }
    }

    private fun parseSS(line: String): Proxy? {
        try {
            val uri = line.removePrefix("ss://")
            // New format: ss://base64(label:method:password@host:port)#name
            // Old format: ss://method:password@host:port#name
            val (base64Part, fragment) = if (uri.contains("#")) {
                val parts = uri.split("#", limit = 2)
                Pair(parts[0], try { URLDecoder.decode(parts[1], "UTF-8") } catch (_: Exception) { parts[1] })
            } else {
                Pair(uri, "")
            }

            val decoded = try {
                String(Base64.decode(base64Part, Base64.DEFAULT))
            } catch (_: Exception) {
                base64Part
            }

            val atIndex = decoded.lastIndexOf("@")
            if (atIndex <= 0) return null

            val userInfo = decoded.substring(0, atIndex)
            val hostPort = decoded.substring(atIndex + 1)
            val colonIndex = hostPort.lastIndexOf(":")
            if (colonIndex <= 0) return null

            val server = hostPort.substring(0, colonIndex)
            val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: return null
            val methodPassword = userInfo.split(":", limit = 2)
            if (methodPassword.size != 2) return null

            return Proxy(
                type = "ss",
                name = fragment.ifBlank { "SS-$server" },
                server = server,
                port = port,
                rawData = mapOf(
                    "type" to "ss",
                    "cipher" to methodPassword[0],
                    "password" to methodPassword[1],
                    "name" to fragment
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse SS failed: ${e.message}")
            return null
        }
    }

    private fun parseSSR(line: String): Proxy? {
        try {
            val base64 = line.removePrefix("ssr://")
            val decoded = String(Base64.decode(base64, Base64.URL_SAFE or Base64.NO_WRAP))
            val parts = decoded.split("/?").first().split(":")
            if (parts.size < 6) return null

            val server = parts[0]
            val port = parts[1].toIntOrNull() ?: return null
            return Proxy(
                type = "ssr",
                name = "SSR-$server",
                server = server,
                port = port,
                rawData = mapOf("type" to "ssr", "server" to server, "port" to port)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse SSR failed: ${e.message}")
            return null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseVmess(line: String): Proxy? {
        try {
            val base64 = line.removePrefix("vmess://")
            val decoded = String(Base64.decode(base64, Base64.DEFAULT))
            val yaml = Yaml()
            val data = yaml.load<Map<String, Any>>(decoded)

            val server = data["add"] as? String ?: data["server"] as? String ?: return null
            val port = when (val p = data["port"]) {
                is Int -> p
                is Double -> p.toInt()
                is String -> p.toIntOrNull() ?: return null
                else -> return null
            }
            val name = (data["ps"] as? String)?.ifBlank { null }
                ?: (data["name"] as? String) ?: "VMESS-$server"

            return Proxy(
                type = "vmess",
                name = name,
                server = server,
                port = port,
                rawData = mapOf("type" to "vmess", "name" to name, "server" to server, "port" to port) + data
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse VMESS failed: ${e.message}")
            return null
        }
    }

    private fun parseTrojan(line: String): Proxy? {
        try {
            val uri = line.removePrefix("trojan://")
            val (userInfo, rest) = if (uri.contains("@")) {
                uri.split("@", limit = 2).let { it[0] to it[1] }
            } else return null

            val (hostPort, _) = if (rest.contains("#")) {
                rest.split("#", limit = 2).let { it[0] to it[1] }
            } else {
                rest to ""
            }

            val (server, portStr) = if (hostPort.contains(":")) {
                hostPort.split(":", limit = 2).let { it[0] to it[1] }
            } else {
                val queryIndex = hostPort.indexOf("?")
                if (queryIndex > 0) {
                    hostPort.substring(0, queryIndex).split(":", limit = 2).let { it[0] to it[1] }
                } else return null
            }

            val port = portStr.split("?")[0].toIntOrNull() ?: return null
            val name = try {
                rest.split("#").getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: "TROJAN-$server"
            } catch (_: Exception) {
                "TROJAN-$server"
            }

            return Proxy(
                type = "trojan",
                name = name,
                server = server,
                port = port,
                rawData = mapOf(
                    "type" to "trojan",
                    "password" to userInfo,
                    "name" to name,
                    "server" to server,
                    "port" to port
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse Trojan failed: ${e.message}")
            return null
        }
    }

    private fun parseVless(line: String): Proxy? {
        try {
            val uri = line.removePrefix("vless://")
            val (uuid, rest) = if (uri.contains("@")) {
                uri.split("@", limit = 2).let { it[0] to it[1] }
            } else return null

            val hostPortPart = rest.split("?", "#").first()
            val (server, portStr) = hostPortPart.split(":", limit = 2)
            val port = portStr.toIntOrNull() ?: return null

            val name = try {
                rest.split("#").getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: "VLESS-$server"
            } catch (_: Exception) {
                "VLESS-$server"
            }

            return Proxy(
                type = "vless",
                name = name,
                server = server,
                port = port,
                rawData = mapOf(
                    "type" to "vless",
                    "uuid" to uuid,
                    "name" to name,
                    "server" to server,
                    "port" to port
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse VLESS failed: ${e.message}")
            return null
        }
    }

    private fun parseHysteria2(line: String): Proxy? {
        try {
            val prefix = if (line.startsWith("hy2://")) "hy2://" else "hysteria2://"
            val uri = line.removePrefix(prefix)
            val (auth, rest) = if (uri.contains("@")) {
                uri.split("@", limit = 2).let { it[0] to it[1] }
            } else return null

            val hostPortPart = rest.split("?", "#").first()
            val (server, portStr) = hostPortPart.split(":", limit = 2)
            val port = portStr.toIntOrNull() ?: return null

            val name = try {
                rest.split("#").getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: "HY2-$server"
            } catch (_: Exception) {
                "HY2-$server"
            }

            return Proxy(
                type = "hysteria2",
                name = name,
                server = server,
                port = port,
                rawData = mapOf(
                    "type" to "hysteria2",
                    "password" to auth,
                    "name" to name,
                    "server" to server,
                    "port" to port
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse Hysteria2 failed: ${e.message}")
            return null
        }
    }

    private fun parseTuic(line: String): Proxy? {
        try {
            val uri = line.removePrefix("tuic://")
            val (userInfo, rest) = if (uri.contains("@")) {
                uri.split("@", limit = 2).let { it[0] to it[1] }
            } else return null

            val hostPortPart = rest.split("?", "#").first()
            val (server, portStr) = hostPortPart.split(":", limit = 2)
            val port = portStr.toIntOrNull() ?: return null

            val name = try {
                rest.split("#").getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: "TUIC-$server"
            } catch (_: Exception) {
                "TUIC-$server"
            }

            val userInfoParts = userInfo.split(":", limit = 2)
            val uuid = userInfoParts[0]
            val password = userInfoParts.getOrElse(1) { "" }

            return Proxy(
                type = "tuic",
                name = name,
                server = server,
                port = port,
                rawData = mapOf(
                    "type" to "tuic",
                    "uuid" to uuid,
                    "password" to password,
                    "name" to name,
                    "server" to server,
                    "port" to port
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse TUIC failed: ${e.message}")
            return null
        }
    }
}
