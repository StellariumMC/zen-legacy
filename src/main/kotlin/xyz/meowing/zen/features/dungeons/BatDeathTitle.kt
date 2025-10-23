package xyz.meowing.zen.features.dungeons

import xyz.meowing.zen.Zen
import xyz.meowing.zen.config.ui.ConfigUI
import xyz.meowing.zen.config.ui.types.ConfigElement
import xyz.meowing.zen.config.ui.types.ElementType
import xyz.meowing.zen.events.PacketEvent
import xyz.meowing.zen.features.Feature
import xyz.meowing.zen.utils.LocationUtils
import xyz.meowing.zen.utils.TitleUtils
import net.minecraft.network.play.server.S29PacketSoundEffect

@Zen.Module
object BatDeathTitle : Feature("batdeadtitle",true,"catacombs") {
    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement("Dungeons", "Bat Death Title", ConfigElement(
                "batdeadtitle",
                null,
                ElementType.Switch(false)
            ), isSectionToggle = true)
    }

    override fun initialize() {
        register<PacketEvent.Received> { event ->
            if (event.packet is S29PacketSoundEffect) {
                val packet = event.packet

                if (packet.soundName != "mob.bat.death" && packet.soundName != "mob.bat.hurt") return@register
                if (LocationUtils.subarea?.lowercase()?.contains("boss") == true) return@register

                TitleUtils.showTitle("§cBat Dead!", null, 1000)
            }
        }
    }
}
