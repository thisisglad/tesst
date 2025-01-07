package kr.toxicity.hud.image.enums

import kr.toxicity.command.BetterCommandSource
import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.listener.HudListener
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.element.ImageElement
import kr.toxicity.hud.image.ImageComponent
import kr.toxicity.hud.util.*
import kr.toxicity.hud.yaml.YamlObjectImpl
import java.io.File
import java.util.regex.Pattern
import kotlin.math.roundToInt

enum class ImageType {
    SINGLE {
        override fun getComponent(listener: HudListener, frame: Long, component: ImageComponent, player: HudPlayer): PixelComponent {
            val get = listener.getValue(player).run {
                if (isNaN()) 0 else (this * component.images.lastIndex).roundToInt()
            }
            return if (component.images.isNotEmpty()) {
                if (get >= 0) component.images[get
                    .coerceAtLeast(0)
                    .coerceAtMost(component.images.lastIndex)] else component.images[0]
            } else EMPTY_PIXEL_COMPONENT
        }

        override fun createElement(
            assets: File,
            sender: BetterCommandSource,
            file: File,
            s: String,
            yamlObject: YamlObject
        ): ImageElement {
            val targetFile = File(
                assets,
                yamlObject["file"]?.asString().ifNull("file value not set.")
                    .replace('/', File.separatorChar)
            )
            return ImageElement(
                s,
                listOf(
                    targetFile
                        .toImage()
                        .removeEmptySide()
                        .ifNull("Invalid image.")
                        .toNamed(targetFile.name),
                ),
                this,
                yamlObject["setting"]?.asObject() ?: emptySetting
            )
        }

    },
    LISTENER {
        override fun getComponent(listener: HudListener, frame: Long, component: ImageComponent, player: HudPlayer): PixelComponent {
            val get = listener.getValue(player).run {
                if (isNaN()) 0 else (this * component.images.lastIndex).roundToInt()
            }
            return if (get >= 0) component.images[get
                .coerceAtLeast(0)
                .coerceAtMost(component.images.lastIndex)] else component.images[(frame % component.images.size).toInt()]
        }

        override fun createElement(
            assets: File,
            sender: BetterCommandSource,
            file: File,
            s: String,
            yamlObject: YamlObject
        ): ImageElement {
            val splitType = yamlObject["split-type"]?.asString()?.let { splitType ->
                runWithExceptionHandling(sender, "Unable to find that split-type: $splitType") {
                    SplitType.valueOf(splitType.uppercase())
                }.getOrNull()
            } ?: SplitType.LEFT
            val split = yamlObject.getAsInt("split", 25).coerceAtLeast(1)
            val getFile = File(
                assets,
                yamlObject["file"]?.asString().ifNull("file value not set.")
                    .replace('/', File.separatorChar)
            )
            return ImageElement(
                s,
                splitType.split(
                    getFile
                        .toImage()
                        .removeEmptySide()
                        .ifNull("Invalid image.")
                        .toNamed("${getFile.nameWithoutExtension}_${splitType.name.lowercase()}_$split.png"), split
                ),
                this,
                yamlObject["setting"]?.asObject()
                    .ifNull("setting configuration not found.")
            )
        }
    },
    SEQUENCE {
        override fun getComponent(listener: HudListener, frame: Long, component: ImageComponent, player: HudPlayer): PixelComponent {
            val get = listener.getValue(player).run {
                if (isNaN()) 0 else (this * component.images.lastIndex).roundToInt()
            }
            return if (get >= 0) component.images[get
                .coerceAtLeast(0)
                .coerceAtMost(component.images.lastIndex)] else component.images[(frame % component.images.size).toInt()]
        }

        override fun createElement(
            assets: File,
            sender: BetterCommandSource,
            file: File,
            s: String,
            yamlObject: YamlObject
        ): ImageElement {
            val globalFrame = yamlObject.getAsInt("frame", 1).coerceAtLeast(1)
            return ImageElement(
                s,
                (yamlObject["files"]?.asArray()?.map {
                    it.asString()
                } ?: emptyList()).ifEmpty {
                    throw RuntimeException("files is empty.")
                }.map { string ->
                    val matcher = multiFrameRegex.matcher(string)
                    var fileName = string
                    var frame = 1
                    if (matcher.find()) {
                        fileName = matcher.group("name")
                        frame = matcher.group("frame").toInt()
                    }
                    val targetFile = File(assets, fileName.replace('/', File.separatorChar))
                    val targetImage = targetFile
                        .toImage()
                        .removeEmptyWidth()
                        .ifNull("Invalid image: $string")
                        .toNamed(targetFile.name)
                    (0..<(frame * globalFrame).coerceAtLeast(1)).map {
                        targetImage
                    }
                }.sum(),
                this,
                yamlObject["setting"]?.asObject() ?: emptySetting
            )
        }
    }
    ;
    companion object {
        val emptySetting = YamlObjectImpl("", mutableMapOf<String, Any>())
        private val multiFrameRegex = Pattern.compile("(?<name>(([a-zA-Z]|/|.|(_))+)):(?<frame>([0-9]+))")
    }

    abstract fun getComponent(listener: HudListener, frame: Long, component: ImageComponent, player: HudPlayer): PixelComponent
    abstract fun createElement(assets: File, sender: BetterCommandSource, file: File, s: String, yamlObject: YamlObject): ImageElement
}