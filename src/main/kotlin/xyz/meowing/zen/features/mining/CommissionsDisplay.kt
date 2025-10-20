package xyz.meowing.zen.features.mining

import xyz.meowing.zen.Zen
import xyz.meowing.zen.config.ui.ConfigUI
import xyz.meowing.zen.config.ui.types.ConfigElement
import xyz.meowing.zen.config.ui.types.ElementType
import xyz.meowing.zen.events.RenderEvent
import xyz.meowing.zen.events.TablistEvent
import xyz.meowing.zen.features.Feature
import xyz.meowing.zen.hud.HUDManager
import xyz.meowing.zen.utils.Render2D
import xyz.meowing.zen.utils.ScoreboardUtils
import xyz.meowing.zen.utils.Utils.removeFormatting

@Zen.Module
object CommissionsDisplay : Feature("commissions", skyblockOnly = true) {

    private const val name = "Commissions"
    private val commissions = mutableListOf<String>()
    private var hasData = false

    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement("Mining", "Commission Display", ConfigElement(
                "commissions",
                null,
                ElementType.Switch(false)
            ), isSectionToggle = true
        )
    }

    override fun initialize() {
        HUDManager.register(name, "§9§lCommissions:\n§fGolbing Slayer: §c0%")

        register<TablistEvent> { parseTablist() }

        register<RenderEvent.Text> {
            if (HUDManager.isEnabled(name) && hasData) {
                val x = HUDManager.getX(name)
                val y = HUDManager.getY(name)
                val scale = HUDManager.getScale(name)

                getDisplayLines().forEachIndexed { i, line ->
                    Render2D.renderString(line, x, y + i * 10 * scale, scale)
                }
            }
        }
    }

    private fun parseTablist() {
        val entries = ScoreboardUtils.tabList
            .map { (_, displayName) -> displayName.removeFormatting().trim() }
            .filter { it.isNotEmpty() }

        val index = entries.indexOfFirst { it.equals("Commissions:", ignoreCase = true) }
        if (index == -1) {
            hasData = false
            commissions.clear()
            return
        }

        val newComms = mutableListOf<String>()
        for (i in index + 1 until entries.size) {
            val line = entries[i]

            if (line.isEmpty() || (line.contains(":") && !line.contains("%") && !line.contains("DONE"))) break
            if (line.contains("%") || line.contains("DONE")) {
                newComms += line
            }
        }

        hasData = newComms.isNotEmpty()
        commissions.clear()
        commissions.addAll(newComms)
    }

    private fun getDisplayLines(): List<String> {
        if (!hasData) return listOf("§9§lCommissions:", "§fGolbing Slayer: §c0%")

        val lines = mutableListOf("§9§lCommissions:")
        commissions.forEach { raw ->
            val parts = raw.split(":", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val progress = parts[1].trim()

                val color = if (progress == "DONE") {
                    "§a"
                } else {
                    val percent = progress.removeSuffix("%").toFloatOrNull()?.coerceIn(0f, 100f)?.div(100f) ?: 0f
                    when {
                        percent < 0.3f -> "§c"
                        percent < 0.7f -> "§6"
                        percent < 1.0f -> "§e"
                        else -> "§a"
                    }
                }

                lines += " §f$name: $color$progress"
            } else {
                lines += " §f$raw"
            }
        }
        return lines
    }
}