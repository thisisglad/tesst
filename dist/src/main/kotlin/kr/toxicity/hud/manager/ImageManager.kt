package kr.toxicity.hud.manager

import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.api.manager.ConfigManager
import kr.toxicity.hud.element.ImageElement
import kr.toxicity.hud.image.enums.ImageType
import kr.toxicity.hud.image.enums.SplitType
import kr.toxicity.hud.layout.HudLayout
import kr.toxicity.hud.pack.PackGenerator
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.util.*
import kr.toxicity.hud.yaml.YamlObjectImpl
import net.kyori.adventure.audience.Audience
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object ImageManager : BetterHudManager {

    private val imageMap = HashMap<String, ImageElement>()
    private val emptySetting = YamlObjectImpl("", mutableMapOf<String, Any>())

    private val imageNameComponent = ConcurrentHashMap<HudLayout.Identifier, WidthComponent>()

    val allImage get() = imageMap.values

    @Synchronized
    fun getImage(group: HudLayout.Identifier) = imageNameComponent[group]
    @Synchronized
    fun setImage(group: HudLayout.Identifier, component: WidthComponent) {
        imageNameComponent[group] = component
    }

    override fun start() {
    }

    fun getImage(name: String) = synchronized(imageMap) {
        imageMap[name]
    }

    private val multiFrameRegex = Pattern.compile("(?<name>(([a-zA-Z]|/|.|(_))+)):(?<frame>([0-9]+))")

    override fun reload(sender: Audience, resource: GlobalResource) {
        synchronized(imageMap) {
            imageMap.clear()
            imageNameComponent.clear()
        }
        val assets = DATA_FOLDER.subFolder("assets")
        DATA_FOLDER.subFolder("images").forEachAllYamlAsync(sender) { file, s, yamlObject ->
            runWithExceptionHandling(sender, "Unable to load this image: $s in ${file.name}") {
                val image = when (val type = ImageType.valueOf(
                    yamlObject["type"]?.asString().ifNull("type value not set.").uppercase()
                )) {
                    ImageType.SINGLE -> {
                        val targetFile = File(
                            assets,
                            yamlObject["file"]?.asString().ifNull("file value not set.")
                                .replace('/', File.separatorChar)
                        )
                        ImageElement(
                            file.path,
                            s,
                            listOf(
                                targetFile
                                    .toImage()
                                    .removeEmptySide()
                                    .ifNull("Invalid image.")
                                    .toNamed(targetFile.name),
                            ),
                            type,
                            yamlObject["setting"]?.asObject() ?: emptySetting
                        )
                    }

                    ImageType.LISTENER -> {
                        val splitType = yamlObject["split-type"]?.asString()?.let { splitType ->
                            runWithExceptionHandling(sender, "Unable to find that split-type: $splitType") {
                                SplitType.valueOf(splitType.uppercase())
                            }.getOrNull()
                        } ?: SplitType.LEFT
                        val getFile = File(
                            assets,
                            yamlObject["file"]?.asString().ifNull("file value not set.")
                                .replace('/', File.separatorChar)
                        )
                        ImageElement(
                            file.path,
                            s,
                            splitType.split(
                                getFile
                                    .toImage()
                                    .removeEmptySide()
                                    .ifNull("Invalid image.")
                                    .toNamed(getFile.name), yamlObject.getAsInt("split", 25).coerceAtLeast(1)
                            ),
                            type,
                            yamlObject["setting"]?.asObject()
                                .ifNull("setting configuration not found.")
                        )
                    }

                    ImageType.SEQUENCE -> {
                        val globalFrame = yamlObject.getAsInt("frame", 1).coerceAtLeast(1)
                        ImageElement(
                            file.path,
                            s,
                            (yamlObject["files"]?.asArray()?.map {
                                it.asString()
                            } ?: emptyList()).ifEmpty {
                                warn("files is empty.")
                                return@runWithExceptionHandling
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
                            type,
                            yamlObject["setting"]?.asObject() ?: emptySetting
                        )
                    }
                }
                debug(ConfigManager.DebugLevel.ASSETS, "Generating image $s...")
                imageMap.putSync("image", s) {
                    image
                }
            }
        }
        imageMap.values.forEach { value ->
            val list = value.image
            if (list.isNotEmpty()) {
                list.distinctBy {
                    it.name
                }.forEach {
                    PackGenerator.addTask(resource.textures + it.name) {
                        it.image.image.toByteArray()
                    }
                }
            }
        }
    }

    override fun postReload() {
        imageNameComponent.clear()
    }

    override fun end() {
    }
}