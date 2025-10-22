package xyz.meowing.zen.api

import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import xyz.meowing.zen.Zen
import xyz.meowing.zen.Zen.Companion.LOGGER
import xyz.meowing.zen.Zen.Companion.scope
import xyz.meowing.zen.utils.DataUtils
import xyz.meowing.zen.utils.ItemUtils.skyblockID
import xyz.meowing.zen.utils.LoopUtils
import xyz.meowing.zen.utils.NetworkUtils
import net.minecraft.item.ItemStack
import java.util.concurrent.atomic.AtomicBoolean

@Zen.Module
object ItemAPI {
    private val isLoading = AtomicBoolean(false)
    private val initializationComplete = CompletableDeferred<Unit>()

    private var skyblockItemData = JsonObject()
    private var liveAuctionData = JsonObject()

    private val itemDataFile = DataUtils("itemData", JsonObject())
    private val liveAuctionDataFile = DataUtils("liveAuctionData", JsonObject())

    init {
        scope.launch {
            if (itemDataFile.getData().entrySet().isNotEmpty()) {
                skyblockItemData = itemDataFile.getData()
            }

            if (liveAuctionDataFile.getData().entrySet().isNotEmpty()) {
                liveAuctionData = liveAuctionDataFile.getData()
            }

            NEUApi.downloadAndProcessRepo()

            LOGGER.info("Loaded ${skyblockItemData.entrySet().size} Items from saved data file")

            if (skyblockItemData.entrySet().isEmpty()) {
                updateSkyblockItemData(false)
            } else {
                initializationComplete.complete(Unit)
                scope.launch {
                    updateSkyblockItemData(false)
                }
            }
        }

        LoopUtils.loop(1000 * 60 * 10) {
            if (!isLoading.get()) {
                updateSkyblockItemData(false)
            }
        }
    }

    // Convert NEU ID system to Zen ID system
    // MEGALODON;3 -> MEGALODON-EPIC
    // ULTIMATE_WISE;2 -> ENCHANTMENT_ULTIMATE_WISE_2
    fun convertNeuItemToZen(neuId: String, neuData: JsonObject): String {
        var newId = neuId
        if (newId.contains(";")) {
            if (neuData.get("displayname").asString.contains("[Lvl")) {
                newId = convertNeuPetID(neuId)
            }

            if (neuData.get("itemid").asString == "minecraft:enchanted_book") {
                newId = "ENCHANTMENT_${newId.replace(";", "_")}"
            }
        }
        return newId
    }

    // MEGALODON;3 -> MEGALODON-EPIC
    fun convertNeuPetID(neuId: String): String {
        val parts = neuId.split(";")
        return "${parts[0]}-${intToPetTier(parts[1].toInt())}"
    }

    fun intToPetTier(tier: Int): String = when (tier) {
        5 -> "MYTHIC"
        4 -> "LEGENDARY"
        3 -> "EPIC"
        2 -> "RARE"
        1 -> "UNCOMMON"
        0 -> "COMMON"
        else -> "UNKNOWN"
    }

    private fun loadNeuRepo(force: Boolean) {
        LOGGER.info("Loading Skyblock Items from NEU Repo..")
        skyblockItemData = JsonObject()

        try {
            NEUApi.downloadAndProcessRepo(force)
            NEUApi.NeuItemData.getData().entrySet().forEach {
                val newKey = convertNeuItemToZen(it.key, it.value.asJsonObject)
                it.value.asJsonObject.addProperty("sbtID", newKey)

                if (it.value.asJsonObject.has("lore")) {
                    val lore = it.value.asJsonObject.get("lore").asJsonArray
                    lore.reversed().find { loreEntry ->
                        val rarity = extractRarity(loreEntry.asString)
                        if (rarity != null) {
                            it.value.asJsonObject.addProperty("rarity", rarity)
                            true
                        } else false
                    }
                }
                skyblockItemData.add(newKey, it.value)
            }

            LOGGER.info("Loaded Skyblock Items from NEU Repo!")
        } catch (e: Exception) {
            LOGGER.error("There was a problem loading NEU Repo.. ${e.message}")
        }
    }

    fun extractRarity(itemDescription: String): String? {
        val rarities = listOf("SUPREME", "VERY_SPECIAL", "SPECIAL", "DIVINE", "MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON")
        return rarities.find { itemDescription.contains(it, ignoreCase = true) }
    }

    /*
    * Responsible for getting item data from NEU Repo and SBT API,
    * if fails, it has a backup of the last known data.
    * If force is true, it will clear the cache and get all data, from NEU Repo and SBT API
    */
    fun updateSkyblockItemData(force: Boolean = false) {
        if (!isLoading.compareAndSet(false, true)) {
            return
        }

        scope.launch {
            delay(5000)
            try {
                LOGGER.info("Updating Skyblock Item Data..")
                if (force) {
                    skyblockItemData = JsonObject()
                }

                if (skyblockItemData.entrySet().isEmpty()) {
                    loadNeuRepo(force)
                }

                loadPricingData()

                itemDataFile.setData(skyblockItemData)
                liveAuctionDataFile.setData(liveAuctionData)
                itemDataFile.save()
                liveAuctionDataFile.save()

                if (!initializationComplete.isCompleted) {
                    initializationComplete.complete(Unit)
                }
            } finally {
                isLoading.set(false)
            }
        }
    }

    private fun loadPricingData() {
        LOGGER.info("Loading Lowest Item Prices from SBT API")

        try {
            NetworkUtils.getJson(
                url = "https://zen.mrfast-developer.com/pricingData",
                onSuccess = { json ->
                    if (json.entrySet().isEmpty()) {
                        LOGGER.warn("There was a problem loading SBT Prices..")
                        return@getJson
                    }

                    LOGGER.info("Loaded ${json.entrySet().size} Items From Skyblock-Tweaks Item API")

                    json.entrySet().forEach { entry ->
                        val item = entry.value.asJsonObject
                        val itemId = entry.key

                        if (!skyblockItemData.has(itemId)) {
                            skyblockItemData.add(itemId, item)
                        } else {
                            val itemJson = skyblockItemData[itemId].asJsonObject
                            item.entrySet().forEach { (key, value) ->
                                itemJson.add(key, value)
                            }
                        }
                    }
                },
                onError = {
                    LOGGER.error("There was a problem loading SBT Prices.. ${it.message}")
                    it.printStackTrace()
                }
            )
            NetworkUtils.getJson(
                url = "https://zen.mrfast-developer.com/liveAuctions",
                onSuccess = { json ->
                    if (json.entrySet().isEmpty()) {
                        LOGGER.warn("There was a problem loading SBT Live Auctions..")
                        return@getJson
                    }

                    LOGGER.info("Loaded ${json.entrySet().size} Live Auctions From Zen Item API")

                    json.entrySet().forEach { entry ->
                        val item = entry.value.asJsonObject
                        val itemId = entry.key

                        if (!liveAuctionData.has(itemId)) {
                            liveAuctionData.add(itemId, item)
                        } else {
                            val itemJson = liveAuctionData[itemId].asJsonObject
                            item.entrySet().forEach { (key, value) ->
                                itemJson.add(key, value)
                            }
                        }
                    }
                },
                onError = {
                    LOGGER.error("There was a problem loading SBT Live Auctions.. ${it.message}")
                    it.printStackTrace()
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            LOGGER.error("There was a problem loading SBT Pricing Data..")
        }
    }

    fun getItemInfo(itemId: String): JsonObject? {
        runBlocking {
            initializationComplete.await()
        }
        return skyblockItemData[itemId]?.asJsonObject
    }

    fun getSkyblockItems(): JsonObject {
        runBlocking {
            initializationComplete.await()
        }
        return skyblockItemData
    }

    fun getItemInfo(stack: ItemStack): JsonObject? {
        runBlocking {
            initializationComplete.await()
        }
        return stack.skyblockID.let { skyblockItemData[it]?.asJsonObject }
    }
}