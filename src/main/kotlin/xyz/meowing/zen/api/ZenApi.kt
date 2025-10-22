package xyz.meowing.zen.api

import okhttp3.*
import xyz.meowing.zen.Zen
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Zen.Module
object ZenAPI {
    private var ws: WebSocket? = null

    init {
        try {
            val uuid = Zen.mc.session.profile.id.toString()
            val hashedUUID = hashUUID(uuid)

            val debug = mapOf(
                "version" to Zen.modInfo.version,
                "gameVersion" to Zen.modInfo.mcVersion,
                "hashedUUID" to hashedUUID,
                "jarName" to zenJarName
            )

            val debugOptions = debug.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }

            val url = "ws://zen.mrfast-developer.com:1515/ws?$debugOptions"

            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .build()

            ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Zen.LOGGER.info("WebSocket opened")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Zen.LOGGER.info("Received message: $text")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Zen.LOGGER.info("WebSocket error: ${t.message}")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Zen.LOGGER.info("WebSocket closing: $code $reason")
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Zen.LOGGER.info("WebSocket closed: $code $reason")
                }
            })
        } catch (e: Exception) {
            Zen.LOGGER.error("Error initializing ZenAPI", e)
        }
    }

    @Throws(Exception::class)
    private fun hashUUID(uuid: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(uuid.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private val zenJarName: String
        get() {
            val modsDir = File(Zen.mc.mcDataDir, "mods")
            val files = modsDir.listFiles { _, name ->
                name.lowercase().endsWith(".jar") && name.lowercase().contains("zen")
            }
            return files?.joinToString(",") { it.name } ?: "Zen-DevAuthClient.jar"
        }
}