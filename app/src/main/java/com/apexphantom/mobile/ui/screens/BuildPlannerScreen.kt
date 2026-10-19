package com.apexphantom.mobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.apexphantom.mobile.ApexApp
import com.apexphantom.mobile.data.Build
import com.apexphantom.mobile.data.GameId
import com.apexphantom.mobile.data.ITEM_CATEGORY_NAMES
import com.apexphantom.mobile.ui.Ap
import com.apexphantom.mobile.ui.PrimaryButton
import com.apexphantom.mobile.ui.SecondaryButton
import com.apexphantom.mobile.ui.SectionLabel
import com.apexphantom.mobile.ui.SmallButton

@Composable
fun BuildPlannerScreen(nav: NavController, game: GameId, buildId: String?, heroName: String?) {
    val context = LocalContext.current
    val app = context.applicationContext as ApexApp
    val catalog = remember(game) { app.games.catalog(game) }
    val existing = remember(buildId) { app.games.builds.value.firstOrNull { it.id == buildId } }

    var id by remember { mutableStateOf(existing?.id) }
    var hero by remember { mutableStateOf(existing?.hero ?: heroName ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    val slots = remember { mutableStateListOf<String>().apply { addAll(existing?.items ?: List(6) { "" }) } }
    var active by remember { mutableStateOf(slots.indexOfFirst { it.isEmpty() }.let { if (it < 0) 0 else it }) }
    var cat by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var heroPicker by remember { mutableStateOf(hero.isEmpty()) }

    val heroObj = catalog.heroes.firstOrNull { it.name == hero }
    val usedCategories = ITEM_CATEGORY_NAMES.keys.filter { c -> catalog.items.any { it.category == c } }
    val list = catalog.items.filter {
        (cat == "all" || it.category == cat) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true))
    }

    fun current(): Build = Build(
        id = id ?: java.util.UUID.randomUUID().toString().also { id = it },
        game = game,
        hero = hero,
        name = name.ifBlank { "$hero build" },
        items = slots.toList()
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "‹ ${game.title}",
                color = Ap.TextSoft,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { nav.popBackStack() }
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            )
            Text("BUILD PLANNER", color = Ap.RedSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        }
        item {
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Ap.RedDeep)
                    .border(1.dp, Ap.RedBorder, shape)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (heroObj != null) HeroBadge(heroObj, 60.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(hero.ifEmpty { "Karakter seçilmedi" }, color = Ap.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (heroObj != null) Text(heroObj.roleText, color = Ap.Muted, fontSize = 13.sp)
                }
                SmallButton(if (hero.isEmpty()) "Karakter seç" else "Değiştir", filled = hero.isEmpty()) { heroPicker = true }
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                label = { Text("Build adı") },
                placeholder = { Text("Örnek: Kritik hasar") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ap.Red,
                    unfocusedBorderColor = Ap.Border,
                    focusedTextColor = Ap.Text,
                    unfocusedTextColor = Ap.Text,
                    focusedLabelColor = Ap.RedSoft,
                    unfocusedLabelColor = Ap.Muted,
                    cursorColor = Ap.Red
                )
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("EŞYA YUVALARI")
                Text("  Yuvaya dokun, sonra eşya seç", color = Ap.Muted, fontSize = 12.sp)
            }
        }
        items(listOf(0, 3)) { start ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in start until start + 3) {
                    val on = i == active
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 76.dp)
                            .clip(shape)
                            .background(if (on) Ap.RedTint else Ap.Surface)
                            .border(1.dp, if (on) Ap.Red else Ap.Border, shape)
                            .clickable { active = i }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${i + 1}", color = Ap.Muted, fontSize = 12.sp)
                        Text(
                            slots[i].ifEmpty { "Boş" },
                            color = if (slots[i].isEmpty()) Ap.Muted else Ap.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("Bu yuvayı temizle", filled = false) { slots[active] = "" }
                SmallButton("Hepsini temizle", filled = false) {
                    for (i in 0 until 6) slots[i] = ""
                    active = 0
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Kaydet", Modifier.weight(1f)) {
                    if (hero.isEmpty()) {
                        Toast.makeText(context, "Önce bir karakter seç.", Toast.LENGTH_SHORT).show()
                    } else {
                        app.games.save(current())
                        Toast.makeText(context, "Build kaydedildi.", Toast.LENGTH_SHORT).show()
                    }
                }
                SecondaryButton("Klanla Paylaş", Modifier.weight(1f)) {
                    if (hero.isEmpty()) {
                        Toast.makeText(context, "Önce bir karakter seç.", Toast.LENGTH_SHORT).show()
                    } else {
                        val b = current()
                        app.games.save(b)
                        shareBuild(context, b)
                    }
                }
            }
        }
        item { SectionLabel("EŞYALAR") }
        item { SearchField(query, "Eşya ara") { query = it } }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryChip("Tümü", cat == "all") { cat = "all" }
                usedCategories.forEach { c -> CategoryChip(ITEM_CATEGORY_NAMES[c] ?: c, cat == c) { cat = c } }
            }
        }
        items(list, key = { it.category + "/" + it.name }) { eq ->
            val shape = RoundedCornerShape(12.dp)
            val inBuild = eq.name in slots
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(Ap.Surface)
                    .border(1.dp, if (inBuild) Ap.RedBorder else Ap.Border, shape)
                    .clickable {
                        slots[active] = eq.name
                        val next = (1..6).map { (active + it) % 6 }.firstOrNull { slots[it].isEmpty() }
                        if (next != null) active = next
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(eq.name, color = Ap.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    if (inBuild) "Buildde" else ITEM_CATEGORY_NAMES[eq.category] ?: eq.category,
                    color = if (inBuild) Ap.RedSoft else Ap.Muted,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (heroPicker) {
        var q by remember { mutableStateOf("") }
        val heroes = catalog.heroes.filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { heroPicker = false },
            containerColor = Ap.Surface,
            title = { Text("Karakter seç", color = Ap.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(q, "Karakter ara") { q = it }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(heroes, key = { it.name }) { h ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { hero = h.name; heroPicker = false }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HeroBadge(h, 36.dp)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(h.name, color = Ap.Text, fontSize = 15.sp)
                                    Text(h.roleText, color = Ap.Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { heroPicker = false }) { Text("Kapat", color = Ap.TextSoft) } }
        )
    }
}

@Composable
private fun CategoryChip(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        label,
        color = if (active) Color(0xFFFFB3BC) else Ap.TextSoft,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(shape)
            .background(if (active) Ap.RedTint else Color.Transparent)
            .border(1.dp, if (active) Ap.Red else Ap.BorderStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )
}
