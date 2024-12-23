package kr.toxicity.hud.bootstrap.bukkit.compatibility.mmoitems

import io.lumine.mythic.lib.MythicLib
import io.lumine.mythic.lib.api.item.ItemTag
import io.lumine.mythic.lib.api.item.SupportedNBTTagValues
import io.lumine.mythic.lib.skill.handler.SkillHandler
import io.lumine.mythic.lib.skill.result.SkillResult
import io.lumine.mythic.lib.skill.trigger.TriggerType
import net.Indyuce.mmoitems.api.Type
import kr.toxicity.hud.api.listener.HudListener
import kr.toxicity.hud.api.placeholder.HudPlaceholder
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.trigger.HudTrigger
import kr.toxicity.hud.api.update.UpdateEvent
import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.bootstrap.bukkit.compatibility.Compatibility
import kr.toxicity.hud.bootstrap.bukkit.util.bukkitPlayer
import kr.toxicity.hud.util.ifNull
import net.Indyuce.mmoitems.ItemStats
import net.Indyuce.mmoitems.MMOItems
import net.Indyuce.mmoitems.stat.data.AbilityData
import org.bukkit.inventory.ItemStack
import java.util.function.Function

class MMOItemsCompatibility : Compatibility {

    override val website: String = "https://www.spigotmc.org/resources/39267/"

    override val triggers: Map<String, (YamlObject) -> HudTrigger<*>>
        get() = mapOf()
    override val listeners: Map<String, (YamlObject) -> (UpdateEvent) -> HudListener>
        get() = mapOf()
    override val numbers: Map<String, HudPlaceholder<Number>>
        get() = mapOf(
            "total_amount" to object : HudPlaceholder<Number> {
                override fun getRequiredArgsLength(): Int = 2
                override fun invoke(args: MutableList<String>, reason: UpdateEvent): Function<HudPlayer, Number> {
                    val item = mmoItem(Type.get(args[0]).ifNull("Unable to find this MMOItems type: ${args[0]}"), args[1]).id
                    return Function { p ->
                        p.bukkitPlayer.inventory.sumOf {
                            if (MMOItems.getID(it) == item) it.amount else 0
                        }
                    }
                }
            }
        )
    override val strings: Map<String, HudPlaceholder<String>>
        get() = mapOf(
            "mainhand_skill" to object : HudPlaceholder<String> {
                override fun getRequiredArgsLength(): Int = 1
                override fun invoke(args: MutableList<String>, reason: UpdateEvent): Function<HudPlayer, String> {
                    val name = TriggerType.valueOf(args[0])
                    return Function {
                        getAbility(it.bukkitPlayer.inventory.itemInMainHand)[name]?.id ?: "<none>"
                    }
                }
            }
        )
    override val booleans: Map<String, HudPlaceholder<Boolean>>
        get() = mapOf()


    private fun mmoItem(type: Type, name: String) = MMOItems.plugin.getMMOItem(type, name).ifNull("Unable to find this MMOItem: $name in ${type.id}")

    private fun getAbility(itemStack: ItemStack): Map<TriggerType, SkillHandler<SkillResult>> {
        val item = MythicLib.inst().version.wrapper.getNBTItem(itemStack)
        val tags = ArrayList<ItemTag>()
        if (item.hasTag(ItemStats.ABILITIES.nbtPath)) {
            tags.add(ItemTag.getTagAtPath(ItemStats.ABILITIES.nbtPath, item, SupportedNBTTagValues.STRING) ?: return emptyMap())
        }
        val compact = ItemTag.getTagAtPath(ItemStats.ABILITIES.nbtPath, tags)?.value as? String ?: return emptyMap()
        return runCatching {
            val map = HashMap<TriggerType, SkillHandler<SkillResult>>()
            io.lumine.mythic.lib.gson.JsonParser.parseString(compact).asJsonArray.forEach {
                if (it.isJsonObject) {
                    val data = AbilityData(it.asJsonObject)
                    map[data.trigger] = data.handler
                }
            }
            map
        }.getOrElse {
            emptyMap()
        }
    }
}