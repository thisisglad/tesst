package kr.toxicity.hud.manager

import kr.toxicity.hud.api.manager.ShaderManager
import kr.toxicity.hud.api.manager.ShaderManager.*
import kr.toxicity.hud.api.plugin.ReloadInfo
import kr.toxicity.hud.configuration.PluginConfiguration
import kr.toxicity.hud.pack.PackGenerator
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.shader.HotBarShader
import kr.toxicity.hud.shader.HudShader
import kr.toxicity.hud.util.*
import net.kyori.adventure.bossbar.BossBar
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object ShaderManagerImpl : BetterHudManager, ShaderManager {
    var barColor = BossBar.Color.YELLOW
        private set

    private val tagPattern = Pattern.compile("#(?<name>[a-zA-Z]+)")
    private val tagBuilders: MutableMap<String, () -> List<String>> = mutableMapOf(
        "CreateConstant" to {
            constants.map {
                "#define ${it.key} ${it.value}"
            }
        },
        "CreateLayout" to {
            ArrayList<String>().apply {
                hudShaders.entries.forEachIndexed { index, entry ->
                    addAll(ArrayList<String>().apply {
                        val shader = entry.key
                        val id = index + 1
                        add("case ${id}:")
                        if (shader.property > 0) add("    property = ${shader.property};")
                        if (shader.opacity < 1.0) add("    opacity = ${shader.opacity.toFloat()};")
                        val static = shader.renderScale.scale.staticScale
                        fun applyScale(offset: Int, scale: Double, pos: String) {
                            if (scale != 1.0 || static) {
                                val scaleFloat = scale.toFloat()
                                add("    pos.$pos = (pos.$pos - (${offset})) * ${if (static) "$scaleFloat * uiScreen.$pos" else scaleFloat} + (${offset} * uiScreen.$pos);")
                            }
                        }
                        applyScale(shader.renderScale.relativeOffset.x, shader.renderScale.scale.x, "x")
                        applyScale(shader.renderScale.relativeOffset.y, shader.renderScale.scale.y, "y")
                        if (shader.gui.x != 0.0) add("    xGui = ui.x * ${shader.gui.x.toFloat()} / 100.0;")
                        if (shader.gui.y != 0.0) add("    yGui = ui.y * ${shader.gui.y.toFloat()} / 100.0;")
                        if (shader.layer != 0) add("    layer = ${shader.layer};")
                        if (shader.outline) add("    outline = true;")
                        add("    break;")
                        entry.value.forEach {
                            it(id)
                        }
                    })
                }
                hudShaders.clear()
            }
        },
    )

    private val hudShaders = TreeMap<HudShader, MutableList<(Int) -> Unit>>()

    private val tagSupplierMap = ConcurrentHashMap<ShaderType, ShaderTagSupplier>()


    @Synchronized
    fun addHudShader(shader: HudShader, consumer: (Int) -> Unit) {
        synchronized(hudShaders) {
            hudShaders.computeIfAbsent(shader) {
                ArrayList()
            }.add(consumer)
        }
    }

    override fun addTagSupplier(type: ShaderType, supplier: ShaderTagSupplier) {
        tagSupplierMap[type] = tagSupplierMap[type]?.let {
            it + supplier
        } ?: supplier
    }

    private val constants = mutableMapOf<String, String>()

    private val shaderConstants = mutableMapOf(
        "HEIGHT_BIT" to HUD_DEFAULT_BIT.toString(),
        "MAX_BIT" to HUD_MAX_BIT.toString(),
        "ADD_OFFSET" to HUD_ADD_HEIGHT.toString()
    )

    override fun addConstant(key: String, value: String) {
        shaderConstants[key] = value
    }

    override fun start() {
        ShaderType.entries.forEach {
            addTagSupplier(it, EMPTY_SUPPLIER)
        }
    }

    override fun reload(info: ReloadInfo, resource: GlobalResource) {
        constants.clear()
        runWithExceptionHandling(info.sender, "Unable to load shader.yml") {
            val shaders = ShaderType.entries.map {
                it to it.lines()
            }
            constants += shaderConstants
            constants["DEFAULT_OFFSET"] = "${10 + 17 * (ConfigManagerImpl.bossbarResourcePackLine - 1)}"
            val replaceList = mutableSetOf<String>()

            val yaml = PluginConfiguration.SHADER.create()
            barColor = yaml["bar-color"]?.asString()?.let {
                runCatching {
                    BossBar.Color.valueOf(it.uppercase())
                }.getOrNull()
            } ?: BossBar.Color.RED
            fun copy(suffix: String) {
                BOOTSTRAP.resource("background.png")?.buffered()?.use { input ->
                    val byte = input.readAllBytes()
                    PackGenerator.addTask(resource.bossBar + listOf("sprites", "boss_bar", "${barColor.name.lowercase()}_$suffix.png")) {
                        byte
                    }
                }
            }
            copy("background")
            copy("progress")
            BOOTSTRAP.resource("bars.png")?.buffered()?.use { target ->
                val oldImage = target.toImage()
                val yAxis = 10 * barColor.ordinal
                PackGenerator.addTask(resource.bossBar + "bars.png") {
                    BufferedImage(oldImage.width, oldImage.height, BufferedImage.TYPE_INT_ARGB).apply {
                        createGraphics().run {
                            if (barColor.ordinal > 0) drawImage(
                                oldImage.getSubimage(
                                    0,
                                    0,
                                    oldImage.width,
                                    yAxis
                                ), 0, 0, null
                            )
                            drawImage(
                                oldImage.getSubimage(
                                    0,
                                    yAxis + 10,
                                    oldImage.width,
                                    oldImage.height - yAxis - 10
                                ), 0, yAxis + 10, null
                            )
                            dispose()
                        }
                    }.toByteArray()
                }
            }

            if (yaml.getAsBoolean("disable-level-text", false)) replaceList.add("HideExp")

            yaml["hotbar"]?.asObject()?.let {
                if (it.getAsBoolean("disable", false)) {
                    replaceList.add("RemapHotBar")
                    val locations =
                        it.get("locations")?.asObject().ifNull("locations configuration not set.")
                    (1..10).map { index ->
                        locations.get(index.toString())?.asObject()?.let { shaderConfig ->
                            HotBarShader(
                                shaderConfig.get("gui")?.asObject()?.let { gui ->
                                    gui.getAsDouble("x", 0.0) to gui.getAsDouble("y", 0.0)
                                } ?: (0.0 to 0.0),
                                shaderConfig.get("pixel")?.asObject()?.let { pixel ->
                                    pixel.getAsInt("x", 0) to pixel.getAsInt("y", 0)
                                } ?: (0 to 0),
                            )
                        } ?: HotBarShader.empty
                    }.forEachIndexed { index, hotBarShader ->
                        val i = index + 1
                        constants["HOTBAR_${i}_GUI_X"] = hotBarShader.gui.first.toFloat().toString()
                        constants["HOTBAR_${i}_GUI_Y"] = hotBarShader.gui.second.toFloat().toString()
                        constants["HOTBAR_${i}_PIXEL_X"] = hotBarShader.pixel.first.toFloat().toString()
                        constants["HOTBAR_${i}_PIXEL_Y"] = hotBarShader.pixel.second.toFloat().toString()
                    }
                }
            }

            shaders.forEach { shader ->
                val tagSupplier = (tagSupplierMap[shader.first] ?: EMPTY_SUPPLIER).get()
                val byte = buildString {
                    shader.second.forEach write@{ string ->
                        var s = string
                        if (s.isEmpty() || s.startsWith("//")) return@write
                        val tagMatcher = tagPattern.matcher(s)
                        if (tagMatcher.find()) {
                            val group = tagMatcher.group("name")
                            (tagBuilders[group]?.invoke() ?: tagSupplier[group])?.let {
                                it.forEach apply@ { methodString ->
                                    if (methodString.isEmpty() || methodString.startsWith("//")) return@apply
                                    val appendEnter = methodString.first() == '#'
                                    if (appendEnter && (isEmpty() || last() != '\n')) append('\n')
                                    append(methodString.replace("  ", ""))
                                    if (appendEnter) append('\n')
                                }
                                return@write
                            }
                        }
                        if (isNotEmpty() && s.first() == '#') {
                            if (last() != '\n') s = '\n' + s
                            s += '\n'
                        }
                        append(s.substringBeforeLast("//").replace("  ", ""))
                    }
                }.toByteArray()
                PackGenerator.addTask(resource.core + shader.first.fileName) {
                    byte
                }
                //+1.21.2
                PackGenerator.addTask(resource.shaders + shader.first.fileName) {
                    byte
                }
            }
        }
    }

    override fun end() {
    }
}