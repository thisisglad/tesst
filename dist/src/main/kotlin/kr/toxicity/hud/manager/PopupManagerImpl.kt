package kr.toxicity.hud.manager

import kr.toxicity.hud.api.manager.PopupManager
import kr.toxicity.hud.api.plugin.ReloadInfo
import kr.toxicity.hud.api.popup.Popup
import kr.toxicity.hud.popup.PopupImpl
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.util.*
import java.io.File
import java.util.*

object PopupManagerImpl : BetterHudManager, PopupManager {

    override val managerName: String = "Popup"
    override val supportExternalPacks: Boolean = true

    private val popupMap = HashMap<String, PopupImpl>()
    override fun start() {

    }

    override fun preReload() {
        popupMap.clear()
    }

    override fun reload(workingDirectory: File, info: ReloadInfo, resource: GlobalResource) {
        workingDirectory.subFolder("popups").forEachAllYaml(info.sender) { file, s, yamlObject ->
            runCatching {
                popupMap.putSync("popup") {
                    PopupImpl(s, resource, yamlObject)
                }
            }.handleFailure(info) {
                "Unable to load this popup: $s in ${file.name}"
            }
        }
    }

    override fun postReload() {
        popupMap.values.forEach {
            it.array = null
        }
    }

    override fun getAllNames(): MutableSet<String> = Collections.unmodifiableSet(popupMap.keys)
    override fun getPopup(name: String): Popup? = popupMap[name]
    override fun getAllPopups(): Set<Popup> = popupMap.values.toSet()
    override fun end() {
    }
}