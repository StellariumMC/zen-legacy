package xyz.meowing.zen.features.slayers

import xyz.meowing.zen.Zen
import xyz.meowing.zen.Zen.Companion.prefix
import xyz.meowing.zen.api.SlayerTracker
import xyz.meowing.zen.config.ConfigDelegate
import xyz.meowing.zen.config.ui.ConfigUI
import xyz.meowing.zen.config.ui.types.ConfigElement
import xyz.meowing.zen.config.ui.types.ElementType
import xyz.meowing.zen.events.RenderEvent
import xyz.meowing.zen.events.SkyblockEvent
import xyz.meowing.zen.features.Feature
import xyz.meowing.zen.hud.HUDManager
import xyz.meowing.zen.utils.ChatUtils
import xyz.meowing.zen.utils.NumberUtils.formatNumber
import xyz.meowing.zen.utils.NumberUtils.toFormattedDuration
import xyz.meowing.zen.utils.Render2D
import xyz.meowing.zen.utils.TimeUtils
import xyz.meowing.zen.utils.TimeUtils.millis
import xyz.meowing.knit.api.command.Commodore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Zen.Module
object SlayerStats : Feature("slayerstats", true) {
    private const val name = "SlayerStats"
    private val slayertimer by ConfigDelegate<Boolean>("slayertimer")
    private val slayerStatsLines by ConfigDelegate<Set<Int>>("slayerstatslines")

    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement("Slayers", "Slayer stats", ConfigElement(
                "slayerstats",
                "Slayer stats",
                ElementType.Switch(false)
            ), isSectionToggle = true)
            .addElement("Slayers", "Slayer stats", "", ConfigElement(
                "",
                null,
                ElementType.TextParagraph("Shows slayer statistics such as total bosses killed, bosses per hour, and average kill time. §c/slayerstats reset §rto reset stats. Requires §eSlayer Timer§r to be enabled.")
            ))
            .addElement("Slayers", "Slayer stats", "Options", ConfigElement(
                "slayerstatslines",
                "",
                ElementType.MultiCheckbox(
                    options = listOf(
                        "Show Bosses Killed",
                        "Show Bosses/hr",
                        "Show Average kill time",
                        "Show Average spawn time",
                        "Show Total Session time",
                        "Show XP/hr"
                    ),
                    default = setOf(0, 1, 4, 5)
                )
            ))
    }

    override fun initialize() {
        HUDManager.register("SlayerStats", "$prefix §f§lSlayer Stats: \n§7> §bBosses Killed§f: §c15\n§7> §bBosses/hr§f: §c12\n§7> §bAvg. kill§f: §c45.2s")

        register<RenderEvent.Text> {
            if (HUDManager.isEnabled("SlayerStats")) render()
        }

        register<SkyblockEvent.Slayer.Death> {
            if (!slayertimer) {
                ChatUtils.addMessage("$prefix §cYou must enable the §eSlayer Timer§c feature for Slayer Stats to work.")
            }
        }
    }

    private fun getBPH(): Int {
        if(SlayerTracker.sessionBossKills == 0) return 0

        val avgTotal = ((SlayerTracker.totalKillTime + SlayerTracker.totalSpawnTime).millis / SlayerTracker.sessionBossKills / 1000.0) // Avg Total Time in seconds
        val bph = (3600.0 / avgTotal).toInt()
        return bph
    }

    fun reset() {
        SlayerTracker.reset()
        ChatUtils.addMessage("$prefix §fSlayer stats reset!")
    }

    private fun render() {
        val x = HUDManager.getX(name)
        val y = HUDManager.getY(name)
        val scale = HUDManager.getScale(name)
        val lines = getLines()

        if (lines.isNotEmpty()) {
            var currentY = y
            for (line in lines) {
                Render2D.renderString(line, x, currentY, scale)
                currentY += fontRenderer.FONT_HEIGHT + 2
            }
        }
    }

    private fun getLines(): List<String> {
        if (SlayerTracker.mobLastKilledAt.since.inWholeMinutes > 5 || SlayerTracker.mobLastKilledAt.isZero) {
            return emptyList()
        }

        val list = mutableListOf("$prefix §f§lSlayer Stats: ")

        if (slayerStatsLines.contains(4)) {
            if (SlayerTracker.sessionStart.isZero) {
                list.add(" §7> §bSession time§f: §c-")
            } else {
                val pauseMark = SlayerTracker.pauseStart
                val totalTime = TimeUtils.now - SlayerTracker.sessionStart - (pauseMark?.since ?: Duration.ZERO) - SlayerTracker.totalSessionPaused.milliseconds
                val timeString = totalTime.millis.toFormattedDuration(false)
                list.add(" §7> §bSession time§f: §c$timeString" + if (SlayerTracker.isPaused) " §7(Paused)" else "")
            }
        }

        slayerStatsLines.sorted().forEach { line ->
            when (line) {
                0 -> list.add(" §7> §bBosses Killed§f: §c${SlayerTracker.sessionBossKills}")
                1 -> list.add(" §7> §bBosses/hr§f: §c${if (SlayerTracker.sessionBossKills == 0) "-" else getBPH()}")
                2 -> {
                    val avgKill = if (SlayerTracker.sessionBossKills == 0) "-"
                    else (SlayerTracker.totalKillTime.millis / SlayerTracker.sessionBossKills / 1000.0).format(1) + "s"
                    list.add(" §7> §bAvg. kill§f: §c$avgKill")
                }
                3 -> {
                    val avgSpawn = if (SlayerTracker.sessionBossKills == 0) "-"
                    else (SlayerTracker.totalSpawnTime.millis / SlayerTracker.sessionBossKills / 1000.0).format(1) + "s"
                    list.add(" §7> §bAvg. spawn§f: §c$avgSpawn")
                }
                5 -> {
                    val xpPH = getBPH() * SlayerTracker.xpPerKill
                    list.add(" §7> §bXP/hr§f: §c${xpPH.formatNumber()} XP")
                }
            }
        }

        return list
    }
    private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
}

@Zen.Command
object SlayerStatsCommand : Commodore("slayerstats", "zenslayerstats") {
    init {
        literal("reset") {
            runs {
                SlayerStats.reset()
            }
        }

        runs {
            ChatUtils.addMessage("$prefix §fCommand: §c/slayerstats reset")
        }
    }
}