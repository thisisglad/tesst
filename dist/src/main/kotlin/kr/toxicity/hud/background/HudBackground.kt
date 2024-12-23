package kr.toxicity.hud.background

import kr.toxicity.hud.configuration.HudConfiguration
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.image.LoadedImage

//TODO replace it to proper background in the future.
class HudBackground(
    override val id: String,

    val left: LoadedImage,
    val right: LoadedImage,
    val body: LoadedImage,

    val location: PixelLocation,
) : HudConfiguration {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HudBackground

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}