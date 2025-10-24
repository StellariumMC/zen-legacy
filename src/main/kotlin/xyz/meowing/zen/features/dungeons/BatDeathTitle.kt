package xyz.meowing.zen.features.dungeons

import xyz.meowing.zen.Zen
import xyz.meowing.zen.config.ui.ConfigUI
import xyz.meowing.zen.config.ui.types.ConfigElement
import xyz.meowing.zen.config.ui.types.ElementType
import xyz.meowing.zen.features.Feature
import xyz.meowing.zen.utils.LocationUtils
import xyz.meowing.zen.utils.TitleUtils
import xyz.meowing.zen.events.EntityEvent
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.passive.EntityBat

@Zen.Module
object BatDeathTitle : Feature("batdeadtitle",true,"catacombs") {
    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement(
                "Dungeons", "Bat Death Title", ConfigElement(
                    "batdeadtitle",
                    null,
                    ElementType.Switch(false)
                ), isSectionToggle = true
            )
    }

    override fun initialize() {
        register<EntityEvent.Leave> {
            if (it.entity is EntityBat && it.entity.ridingEntity !is EntityArmorStand &&
                LocationUtils.subarea?.lowercase()?.contains("boss") != true) {
                TitleUtils.showTitle("§cBat Dead!", null, 1000)
            }
        }
    }
}
