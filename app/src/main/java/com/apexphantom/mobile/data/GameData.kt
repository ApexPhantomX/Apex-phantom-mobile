package com.apexphantom.mobile.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class GameId(val key: String, val title: String) {
    MLBB("mlbb", "Mobile Legends: Bang Bang"),
    HOK("hok", "Honor of Kings");

    companion object {
        fun fromKey(key: String): GameId? = entries.firstOrNull { it.key == key }
    }
}

val ROLE_NAMES: Map<String, String> = linkedMapOf(
    "tank" to "Tank",
    "fighter" to "Savaşçı",
    "assassin" to "Suikastçı",
    "mage" to "Büyücü",
    "marksman" to "Nişancı",
    "support" to "Destek"
)

val ITEM_CATEGORY_NAMES: Map<String, String> = linkedMapOf(
    "attack" to "Saldırı",
    "magic" to "Büyü",
    "defense" to "Savunma",
    "movement" to "Hareket",
    "jungle" to "Orman",
    "roam" to "Dolaşım"
)

data class Hero(val name: String, val roles: List<String>, val icon: String?) {
    val initials: String
        get() {
            val parts = name.split(' ', '-', '.').filter { it.isNotBlank() }
            return if (parts.size >= 2) "${parts[0][0]}${parts[1][0]}".uppercase()
            else name.take(2).uppercase()
        }
    val roleText: String get() = roles.mapNotNull { ROLE_NAMES[it] }.joinToString(" / ")
}

data class Item(val name: String, val category: String)

data class GameCatalog(
    val game: GameId,
    val updated: String,
    val heroes: List<Hero>,
    val items: List<Item>
)

data class Build(
    val id: String = UUID.randomUUID().toString(),
    val game: GameId,
    val hero: String,
    val name: String,
    val items: List<String>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("game", game.key)
        .put("hero", hero)
        .put("name", name)
        .put("items", JSONArray(items))

    /** Klanla paylaşmak için okunabilir metin ve içe aktarma kodu. */
    fun shareText(): String {
        val sb = StringBuilder()
        sb.append("Apex Phantom Build\n")
        sb.append("Oyun: ${game.title}\n")
        sb.append("Karakter: $hero\n")
        sb.append("Build: $name\n\n")
        items.forEachIndexed { i, it -> sb.append("${i + 1}. ${it.ifEmpty { "(boş)" }}\n") }
        sb.append("\nUygulamada içe aktarmak için kod:\n")
        sb.append(shareCode())
        return sb.toString()
    }

    fun shareCode(): String {
        val json = JSONObject()
            .put("g", game.key)
            .put("h", hero)
            .put("n", name)
            .put("i", JSONArray(items))
            .toString()
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
        return "$CODE_PREFIX$encoded"
    }

    companion object {
        const val CODE_PREFIX = "APB1:"

        fun fromJson(o: JSONObject): Build? {
            val game = GameId.fromKey(o.optString("game")) ?: return null
            val arr = o.optJSONArray("items") ?: JSONArray()
            return Build(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                game = game,
                hero = o.optString("hero"),
                name = o.optString("name"),
                items = List(6) { i -> if (i < arr.length()) arr.optString(i) else "" }
            )
        }

        /** Paylaşılan metnin içinden kodu bulur ve build'e çevirir. Geçersizse null. */
        fun fromShareText(text: String): Build? {
            val start = text.indexOf(CODE_PREFIX)
            if (start < 0) return null
            val code = text.substring(start + CODE_PREFIX.length).trim().takeWhile { !it.isWhitespace() }
            return try {
                val json = JSONObject(String(Base64.decode(code, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8))
                val game = GameId.fromKey(json.optString("g")) ?: return null
                val arr = json.optJSONArray("i") ?: JSONArray()
                Build(
                    game = game,
                    hero = json.optString("h"),
                    name = json.optString("n"),
                    items = List(6) { i -> if (i < arr.length()) arr.optString(i) else "" }
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Oyun verilerini (assets/games klasöründeki JSON dosyaları) okur ve kayıtlı build'leri saklar. */
class GameRepository(private val context: Context) {

    private val cache = HashMap<GameId, GameCatalog>()
    private val prefs = context.getSharedPreferences("builds", Context.MODE_PRIVATE)

    private val _builds = MutableStateFlow(loadBuilds())
    val builds: StateFlow<List<Build>> = _builds.asStateFlow()

    fun catalog(game: GameId): GameCatalog = cache.getOrPut(game) { loadCatalog(game) }

    private fun loadCatalog(game: GameId): GameCatalog {
        val text = context.assets.open("games/${game.key}.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        val heroes = mutableListOf<Hero>()
        val ha = root.optJSONArray("heroes") ?: JSONArray()
        for (i in 0 until ha.length()) {
            val h = ha.getJSONObject(i)
            val roles = mutableListOf<String>()
            val ra = h.optJSONArray("roles") ?: JSONArray()
            for (j in 0 until ra.length()) roles += ra.getString(j)
            val icon = h.optString("icon").takeIf { it.isNotBlank() && it != "null" }
            heroes += Hero(h.getString("name"), roles, icon)
        }
        val items = mutableListOf<Item>()
        val ia = root.optJSONArray("items") ?: JSONArray()
        for (i in 0 until ia.length()) {
            val it = ia.getJSONObject(i)
            items += Item(it.getString("name"), it.getString("category"))
        }
        return GameCatalog(
            game = game,
            updated = root.optString("updated"),
            heroes = heroes.sortedBy { it.name.lowercase() },
            items = items
        )
    }

    private fun loadBuilds(): List<Build> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { Build.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<Build>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("list", arr.toString()).apply()
        _builds.value = list
    }

    fun save(build: Build) {
        val list = _builds.value.toMutableList()
        val idx = list.indexOfFirst { it.id == build.id }
        if (idx >= 0) list[idx] = build else list.add(0, build)
        persist(list)
    }

    fun delete(id: String) = persist(_builds.value.filterNot { it.id == id })
}
