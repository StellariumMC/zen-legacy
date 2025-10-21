package xyz.meowing.zen

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import xyz.meowing.zen.compat.OldConfig
import xyz.meowing.zen.config.ZenConfig
import xyz.meowing.zen.config.ui.ConfigUI
import xyz.meowing.zen.events.*
import xyz.meowing.zen.features.Debug
import xyz.meowing.zen.features.Feature
import xyz.meowing.zen.features.FeatureLoader
import xyz.meowing.zen.features.general.ContributorColor
import xyz.meowing.zen.utils.ChatUtils
import xyz.meowing.zen.utils.DataUtils
import xyz.meowing.zen.utils.LoopUtils
import xyz.meowing.zen.utils.NetworkUtils
import xyz.meowing.zen.utils.TickUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.event.ClickEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import org.apache.logging.log4j.LogManager
import xyz.meowing.knit.api.loader.KnitModInfo
import xyz.meowing.vexel.Vexel

@Mod(modid = "zen", name = "Zen", version = "1.8.9", useMetadata = true, clientSideOnly = true)
class Zen {
    data class PersistentData (val isFirstInstall: Boolean = true)
    private var eventCall: EventBus.EventCall? = null
    private lateinit var FirstInstall: DataUtils<PersistentData>

    @Target(AnnotationTarget.CLASS)
    annotation class Module

    @Target(AnnotationTarget.CLASS)
    annotation class Command

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        Vexel.init()

        EventBus.post(GameEvent.Load())

        OldConfig.convertConfig(mc.mcDataDir)
        configUI = ZenConfig()
        FeatureLoader.init()
        initializeFeatures()
        executePending()

        mc.renderManager.skinMap.let {
            it["slim"]?.run { addLayer(ContributorColor.CosmeticRendering()) }
            it["default"]?.run { addLayer(ContributorColor.CosmeticRendering()) }
        }

        FirstInstall = DataUtils("zen-data", PersistentData())

        eventCall = EventBus.register<EntityEvent.Join> ({ event ->
            if (event.entity == mc.thePlayer) {
                ChatUtils.addMessage(
                    "$prefix §fMod loaded.",
                    "§c${FeatureLoader.getFeatCount()} modules §8- §c${FeatureLoader.getLoadtime()}ms §8- §c${FeatureLoader.getCommandCount()} commands"
                )
                val data = FirstInstall.getData()
                if (data.isFirstInstall) {
                    ChatUtils.addMessage("$prefix §fThanks for installing Zen!")
                    ChatUtils.addMessage("§7> §fUse §c/zen §fto open the config or §c/zenhud §fto edit HUD elements")
                    ChatUtils.addMessage("§7> §cDiscord:§b [Discord]", "Discord server", ClickEvent.Action.OPEN_URL, "https://discord.gg/KPmHQUC97G")
                    FirstInstall.setData(data.copy(isFirstInstall = false))
                    FirstInstall.save()
                }
                if (Debug.debugmode) ChatUtils.addMessage("$prefix §fYou have debug mode enabled, restart the game if this was not intentional.")

                LoopUtils.setTimeout(5000) {
                    UpdateChecker.checkForUpdates()
                }

                eventCall?.unregister()
                eventCall = null
            }
        })

        EventBus.register<GuiEvent.Open> ({ event ->
            if (event.screen is GuiInventory) isInInventory = true
        })

        EventBus.register<GuiEvent.Close> ({
            isInInventory = false
        })

        EventBus.register<AreaEvent.Main> ({
            TickUtils.scheduleServer(1) {
                areaFeatures.forEach { it.update() }
            }
        })

        EventBus.register<AreaEvent.Sub> ({
            TickUtils.scheduleServer(1) {
                subareaFeatures.forEach { it.update() }
            }
        })

        EventBus.register<AreaEvent.Skyblock> ({
            TickUtils.scheduleServer(1) {
                skyblockFeatures.forEach { it.update() }
            }
        })

        NetworkUtils.getJson(
            "https://api.hypixel.net/v2/resources/skyblock/election",
            onSuccess = { jsonObject ->
                if (jsonObject.get("success")?.asBoolean != true) return@getJson
                val dataElement = jsonObject.get("data") ?: return@getJson

                mayorData = Gson().fromJson(dataElement, ApiMayor::class.java)
            },
            onError = { exception ->
                LOGGER.warn("Failed to fetch election data: ${exception.message}")
            }
        )
    }

    companion object {
        @JvmField val LOGGER = LogManager.getLogger("zen")
        private val pendingCallbacks = mutableListOf<Pair<String, (Any) -> Unit>>()
        private val pendingFeatures = mutableListOf<Feature>()
        private val areaFeatures = mutableListOf<Feature>()
        private val subareaFeatures = mutableListOf<Feature>()
        private val skyblockFeatures = mutableListOf<Feature>()
        lateinit var configUI: ConfigUI
        const val prefix = "§7[§bZen§7]"
        val features = mutableListOf<Feature>()
        val mc: Minecraft = Minecraft.getMinecraft()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val modInfo = KnitModInfo("@mod_id@", "@mod_name@", "@mod_version@")
        var isInInventory = false
        var mayorData: ApiMayor? = null

        private fun executePending() {
            pendingCallbacks.forEach { (configKey, callback) ->
                configUI.registerListener(configKey, callback)
            }
            pendingCallbacks.clear()
        }

        fun registerListener(configKey: String, instance: Any) {
            val callback: (Any) -> Unit = { _ ->
                if (instance is Feature) instance.update()
            }

            if (::configUI.isInitialized) configUI.registerListener(configKey, callback) else pendingCallbacks.add(configKey to callback)
        }

        fun initializeFeatures() {
            pendingFeatures.forEach { feature ->
                features.add(feature)
                if (feature.hasAreas()) areaFeatures.add(feature)
                if (feature.hasSubareas()) subareaFeatures.add(feature)
                if (feature.skyblockOnly) skyblockFeatures.add(feature)
                feature.addConfig(configUI)
                feature.initialize()
                feature.configKey?.let {
                    registerListener(it, feature)
                }
                feature.update()
            }
            pendingFeatures.clear()
        }

        fun addFeature(feature: Feature) = pendingFeatures.add(feature)
        fun openConfig() = mc.displayGuiScreen(configUI)
    }
}

data class ApiMayor(
    @SerializedName("mayor")
    val mayor: Candidate
) {
    data class Candidate(
        @SerializedName("name")
        val name: String,
        @SerializedName("perks")
        val perks: List<Perk> = emptyList(),
        @SerializedName("minister")
        val minister: Candidate? = null
    )

    data class Perk(
        @SerializedName("name")
        val name: String,
        @SerializedName("description")
        val description: String
    )
}