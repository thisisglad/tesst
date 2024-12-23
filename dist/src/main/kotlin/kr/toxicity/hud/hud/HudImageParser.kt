package kr.toxicity.hud.hud

import kr.toxicity.hud.api.component.PixelComponent
import kr.toxicity.hud.api.component.WidthComponent
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.update.UpdateEvent
import kr.toxicity.hud.element.ImageElement
import kr.toxicity.hud.image.ImageComponent
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.layout.ImageLayout
import kr.toxicity.hud.renderer.ImageRenderer
import kr.toxicity.hud.location.GuiLocation
import kr.toxicity.hud.shader.HudShader
import kr.toxicity.hud.util.*
import net.kyori.adventure.text.Component
import kotlin.math.roundToInt

class HudImageParser(parent: HudImpl, private val imageLayout: ImageLayout, gui: GuiLocation, pixel: PixelLocation) : HudSubParser {

    private val chars = run {
        val finalPixel = imageLayout.location + pixel

        val shader = HudShader(
            gui,
            imageLayout.renderScale + pixel,
            imageLayout.layer,
            imageLayout.outline,
            finalPixel.opacity,
            imageLayout.property
        )
        val negativeSpace = parent.getOrCreateSpace(-1)
        fun ImageElement.toComponent(parentComponent: ImageComponent? = null): ImageComponent {
            val list = ArrayList<PixelComponent>()
            if (listener != null) {
                list.add(EMPTY_PIXEL_COMPONENT)
            }
            image.forEach { pair ->
                val fileName = "$NAME_SPACE_ENCODED:${pair.name}"
                val height = (pair.image.image.height.toDouble() * imageLayout.scale * scale).roundToInt()
                val scale = height.toDouble() / pair.image.image.height
                val ascent = finalPixel.y.coerceAtLeast(-HUD_ADD_HEIGHT).coerceAtMost(HUD_ADD_HEIGHT)
                val component = image(imageLayout.identifier(shader, ascent, fileName)) {
                    val c = parent.newChar
                    val comp = Component.text()
                        .font(parent.imageKey)
                    val finalWidth = WidthComponent(
                        if (BOOTSTRAP.useLegacyFont()) comp.content(c).append(NEGATIVE_ONE_SPACE_COMPONENT.component) else comp.content("$c$negativeSpace"),
                        (pair.image.image.width.toDouble() * scale).roundToInt()
                    )
                    parent.jsonArray?.let { array ->
                        createAscent(shader, ascent) { y ->
                            array += jsonObjectOf(
                                "type" to "bitmap",
                                "file" to fileName,
                                "ascent" to y,
                                "height" to height,
                                "chars" to jsonArrayOf(c)
                            )
                        }
                    }
                    finalWidth
                }

                list.add(component.toPixelComponent(finalPixel.x + (pair.image.xOffset * scale).roundToInt()))
            }
            return ImageComponent(this, parentComponent, list, children.entries.associate {
                it.key to it.value.toComponent()
            })
        }
        val renderer = ImageRenderer(
            imageLayout,
            try {
                imageLayout.source.toComponent()
            } catch (_: StackOverflowError) {
                throw RuntimeException("circular reference found in ${imageLayout.source.id}")
            }
        )
        renderer.max() to renderer.render(UpdateEvent.EMPTY)
    }

    val max = chars.first

    override fun render(player: HudPlayer): (Long) -> PixelComponent = chars.second(player)

}