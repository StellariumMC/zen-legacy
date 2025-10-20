package xyz.meowing.zen.features.mining

import xyz.meowing.zen.Zen
import xyz.meowing.zen.config.ConfigDelegate
import xyz.meowing.zen.config.ui.ConfigUI
import xyz.meowing.zen.config.ui.types.ConfigElement
import xyz.meowing.zen.config.ui.types.ElementType
import xyz.meowing.zen.events.RenderEvent
import xyz.meowing.zen.events.TablistEvent
import xyz.meowing.zen.features.Feature
import xyz.meowing.zen.hud.HUDManager
import xyz.meowing.zen.utils.Render2D
import xyz.meowing.zen.utils.ScoreboardUtils
import xyz.meowing.zen.utils.TimeUtils
import xyz.meowing.zen.utils.TimeUtils.millis
import xyz.meowing.zen.utils.TitleUtils.showTitle
import xyz.meowing.zen.utils.Utils
import xyz.meowing.zen.utils.Utils.removeFormatting
import kotlin.math.max

@Zen.Module
object MiningAbility : Feature("miningability", skyblockOnly = true) {
    private const val name = "Mining Ability"
    private var abilityName: String = ""
    private var cooldownSeconds: Float = 0f
    private var lastUpdateTime = TimeUtils.zero
    private var hasWidget: Boolean = false
    private var wasOnCooldown: Boolean = false
    private val showTitle by ConfigDelegate<Boolean>("miningabilitytitle")

    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement("Mining", "Mining Ability", ConfigElement(
                "miningability",
                null,
                ElementType.Switch(false)
            ), isSectionToggle = true)
            .addElement("Mining", "Mining Ability", "Options", ConfigElement(
                "miningabilitytitle",
                "Show Title",
                ElementType.Switch(true)
            ))
    }

    override fun initialize() {
        HUDManager.register(name, "§9§lPickaxe Ability:\n§fMining Speed Boost: §aAvailable")

        register<TablistEvent> { parseTablist() }

        register<RenderEvent.Text> {
            if (HUDManager.isEnabled(name) && hasWidget) {
                val x = HUDManager.getX(name)
                val y = HUDManager.getY(name)
                val scale = HUDManager.getScale(name)

                getDisplayLines().forEachIndexed { index, line ->
                    Render2D.renderString(line, x, y + index * 10 * scale, scale)
                }
            }
        }
    }

    private fun parseTablist() {
        val entries = ScoreboardUtils.tabList
            .map { (_, displayName) -> displayName.removeFormatting().trim() }
            .filter { it.isNotEmpty() }

        val abilityIndex = entries.indexOfFirst { it.contains("Ability", ignoreCase = true) }

        if (abilityIndex == -1 || abilityIndex + 1 >= entries.size) {
            hasWidget = false
            reset()
            return
        }

        hasWidget = true
        val abilityLine = entries[abilityIndex + 1]
        if (!abilityLine.contains(":")) return

        val parts = abilityLine.split(":", limit = 2)
        if (parts.size != 2) return

        abilityName = parts[0].trim()
        val status = parts[1].trim()

        if (status.contains("Available", ignoreCase = true)) {
            cooldownSeconds = 0f
            lastUpdateTime = TimeUtils.zero
        } else {
            if (lastUpdateTime == TimeUtils.zero) {
                val match = Regex("""(\d+(?:\.\d+)?)s""").find(status)
                cooldownSeconds = match?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                lastUpdateTime = TimeUtils.now
            }
        }
    }

    private fun getDisplayLines(): List<String> {
        if (!hasWidget || abilityName.isEmpty()) {
            return listOf(
                "§9§lPickaxe Ability:",
                "§fMining Speed Boost: §aAvailable"
            )
        }

        val elapsed = if (lastUpdateTime != TimeUtils.zero) (TimeUtils.now - lastUpdateTime).millis / 1000f else 0f
        val remaining = max(0f, cooldownSeconds - elapsed)
        val isAvailable = remaining <= 0f

        if (isAvailable && wasOnCooldown && showTitle) {
            showTitle("§aAbility Ready!", null, 2000)
            Utils.playSound("mob.cat.meow", 1f, 1f)
            wasOnCooldown = false
        } else if (!isAvailable) {
            wasOnCooldown = true
        }

        val statusText = if (isAvailable) {
            "§aAvailable"
        } else {
            val color = when {
                remaining <= 3f -> "§c"
                remaining <= 10f -> "§e"
                else -> "§6"
            }
            val timeText = if (remaining <= 5f) "%.1fs".format(remaining) else "${remaining.toInt()}s"
            "$color$timeText"
        }

        return listOf(
            "§9§lPickaxe Ability:",
            "§f$abilityName: $statusText"
        )
    }

    private fun reset() {
        abilityName = ""
        cooldownSeconds = 0f
        wasOnCooldown = false
    }
}