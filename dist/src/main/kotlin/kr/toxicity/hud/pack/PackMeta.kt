package kr.toxicity.hud.pack

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kr.toxicity.hud.util.BOOTSTRAP
import java.io.File
import java.lang.reflect.Type
import java.util.zip.ZipEntry
import kotlin.math.max

data class PackMeta(
    val pack: Pack,
    val overlays: Overlay? = Overlay()
) {
    companion object {
        private val gson = GsonBuilder()
            .registerTypeAdapter(VersionRange::class.java, object : JsonDeserializer<VersionRange> {
                override fun deserialize(p0: JsonElement, p1: Type, p2: JsonDeserializationContext): VersionRange? {
                    return when (p0) {
                        is JsonObject -> VersionRange(
                            p0.getAsJsonPrimitive("min_inclusive").asInt,
                            p0.getAsJsonPrimitive("max_inclusive").asInt
                        )
                        is JsonArray -> VersionRange(
                            p0.get(0).asInt,
                            p0.get(1).asInt
                        )
                        else -> throw RuntimeException("VersionRage must be json array or json object.")
                    }
                }
            })
            .create()

        val default by lazy {
            PackMeta(
                Pack(
                    BOOTSTRAP.mcmetaVersion(),
                    "BetterHud's default resource pack.",
                    VersionRange(9, 55)
                )
            )
        }
        val zipEntry = ZipEntry("pack.mcmeta")

        fun fromFile(file: File): PackMeta = file.bufferedReader().use {
            gson.fromJson(it, PackMeta::class.java)
        }
    }

    operator fun plus(other: PackMeta): PackMeta {
        val o1 = overlays
        val o2 = other.overlays
        return PackMeta(
            pack + other.pack,
            when {
                o1 != null && o2 != null -> o1 + o2
                o1 != null -> o1
                o2 != null -> o2
                else -> null
            }
        )
    }

    fun toByteArray() = gson.toJson(this).toByteArray()

    data class Pack(
        @SerializedName("pack_format") val packFormat: Int,
        val description: String,
        @SerializedName("supported_formats") val supportedFormats: VersionRange?
    ) {
        operator fun plus(other: Pack): Pack {
            return Pack(
                max(packFormat, other.packFormat),
                other.description,
                supportedFormats?.min(other.supportedFormats)
            )
        }
    }

    data class Overlay(
        val entries: List<OverlayEntry> = emptyList()
    ) {
        operator fun plus(other: Overlay): Overlay {
            return Overlay((entries + other.entries)
                .asSequence()
                .sorted()
                .distinctBy {
                    it.directory
                }
                .toList())
        }
    }

    data class OverlayEntry(
        val formats: VersionRange,
        val directory: String
    ) : Comparable<OverlayEntry> {
        override fun compareTo(other: OverlayEntry): Int {
            return formats.compareTo(other.formats)
        }
    }

    data class VersionRange(
        @SerializedName("min_inclusive") val min: Int,
        @SerializedName("max_inclusive") val max: Int
    ) : Comparable<VersionRange> {

        val range get() = max - min

        infix fun min(other: VersionRange?) = if (other != null) VersionRange(
            min.coerceAtMost(other.min),
            max.coerceAtMost(other.max)
        ) else this

        override fun compareTo(other: VersionRange): Int {
            return range.compareTo(other.range)
        }
    }
}