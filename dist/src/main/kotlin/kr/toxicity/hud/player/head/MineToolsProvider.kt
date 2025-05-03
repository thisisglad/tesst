package kr.toxicity.hud.player.head

import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.util.httpClient
import kr.toxicity.hud.util.parseJson
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class MineToolsProvider : PlayerSkinProvider {
    override fun provide(player: HudPlayer): String? {
        return provideFromUUID(player.uuid().toString())
    }

    override fun provide(playerName: String): String? {
        return getUUIDFromName(playerName)?.let {
            provideFromUUID(it)
        }
    }

    private fun getUUIDFromName(playerName: String): String? {
        return httpClient {
            InputStreamReader(
                send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("https://api.minetools.eu/uuid/${playerName}"))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofInputStream()
                ).body()
            ).buffered().use {
                parseJson(it)
            }.asJsonObject.getAsJsonPrimitive("id").asString
        }.getOrNull()
    }

    private fun provideFromUUID(uuid: String): String? {
        return httpClient {
            InputStreamReader(
                send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("https://api.minetools.eu/profile/$uuid"))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofInputStream()
                ).body()
            ).buffered().use {
                parseJson(it)
            }.asJsonObject
                .getAsJsonObject("raw")
                .getAsJsonArray("properties")
                .get(0)
                .asJsonObject
                .getAsJsonPrimitive("value")
                .asString
        }.getOrNull()
    }
}