package com.apexphantom.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.apexphantom.mobile.ApexApp
import com.apexphantom.mobile.data.Build
import com.apexphantom.mobile.data.GameId
import com.apexphantom.mobile.data.Hero
import com.apexphantom.mobile.data.ITEM_CATEGORY_NAMES
import com.apexphantom.mobile.data.ROLE_NAMES
import com.apexphantom.mobile.ui.Ap
import com.apexphantom.mobile.ui.ApCard
import com.apexphantom.mobile.ui.PrimaryButton
import com.apexphantom.mobile.ui.SecondaryButton
import com.apexphantom.mobile.ui.Segmented
import com.apexphantom.mobile.ui.SmallButton

val ROLE_COLORS: Map<String, Color> = mapOf(
    "tank" to Color(0xFF8FA3B8),
    "fighter" to Color(0xFFF2994A),
    "assassin" to Color(0xFFE5243B),
    "mage" to Color(0xFFA98BFF),
    "marksman" to Color(0xFFF2D06B),
    "support" to Color(0xFF3DDC97)
)

@Composable
fun HeroBadge(hero: Hero, size: Dp) {
    val ring = ROLE_COLORS[hero.roles.firstOrNull()] ?: Ap.Muted
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w / 2, h * 0.05f)
                lineTo(w * 0.9f, h * 0.275f)
                lineTo(w * 0.9f, h * 0.725f)
                lineTo(w / 2, h * 0.95f)
                lineTo(w * 0.1f, h * 0.725f)
                lineTo(w * 0.1f, h * 0.275f)
                close()
            }
            drawPath(path, Color(0xFF1C0B0F))
            drawPath(path, ring, style = Stroke(width = 2.dp.toPx()))
        }
        Text(hero.initials, color = Ap.Text, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.26f).sp)
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
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

@Composable
fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ap.Red,
            unfocusedBorderColor = Ap.Border,
            focusedContainerColor = Ap.Surface,
            unfocusedContainerColor = Ap.Surface,
            focusedTextColor = Ap.Text,
            unfocusedTextColor = Ap.Text,
            cursorColor = Ap.Red
        )
    )
}

fun shareBuild(context: android.content.Context, build: Build) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, build.shareText())
    }
    context.startActivity(Intent.createChooser(send, "Build’i paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun GameScreen(nav: NavController, game: GameId) {
    val context = LocalContext.current
    val app = context.applicationContext as ApexApp
    val catalog = remember(game) { app.games.catalog(game) }
    val allBuilds by app.games.builds.collectAsState()
    val builds = allBuilds.filter { it.game == game }

    var section by rememberSaveable(game) { mutableStateOf(0) }
    var query by rememberSaveable(game) { mutableStateOf("") }
    var role by rememberSaveable(game) { mutableStateOf("all") }
    var itemCat by rememberSaveable(game) { mutableStateOf("all") }
    var importOpen by remember { mutableStateOf(false) }

    val heroes = catalog.heroes.filter {
        (role == "all" || role in it.roles) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true))
    }
    val usedCategories = ITEM_CATEGORY_NAMES.keys.filter { c -> catalog.items.any { it.category == c } }
    val filteredItems = catalog.items.filter {
        (itemCat == "all" || it.category == itemCat) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("REHBER", color = Ap.RedSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Text(game.title, color = Ap.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "${catalog.heroes.size} karakter · ${catalog.items.size} eşya · Veri: ${catalog.updated}",
                    color = Ap.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        item {
            Segmented(listOf("Karakterler", "Eşyalar", "Buildler"), section) { section = it; query = "" }
        }

        when (section) {
            0 -> {
                item { SearchField(query, "Karakter ara") { query = it } }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Tümü", role == "all") { role = "all" }
                        ROLE_NAMES.forEach { (key, name) -> Chip(name, role == key) { role = key } }
                    }
                }
                if (heroes.isEmpty()) item { Text("Aramana uyan karakter yok.", color = Ap.Muted, fontSize = 14.sp) }
                items(heroes.chunked(3)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { hero ->
                            val shape = RoundedCornerShape(14.dp)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(Ap.Surface)
                                    .border(1.dp, Ap.Border, shape)
                                    .clickable { nav.navigate("build/${game.key}?hero=${Uri.encode(hero.name)}") }
                                    .padding(vertical = 12.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                HeroBadge(hero, 56.dp)
                                Text(hero.name, color = Ap.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(hero.roleText, color = Ap.Muted, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item { Text("Bir karaktere dokunarak onun için build hazırlayabilirsin.", color = Ap.Muted, fontSize = 12.sp) }
            }
            1 -> {
                item { SearchField(query, "Eşya ara") { query = it } }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Tümü", itemCat == "all") { itemCat = "all" }
                        usedCategories.forEach { c -> Chip(ITEM_CATEGORY_NAMES[c] ?: c, itemCat == c) { itemCat = c } }
                    }
                }
                if (filteredItems.isEmpty()) item { Text("Aramana uyan eşya yok.", color = Ap.Muted, fontSize = 14.sp) }
                items(filteredItems, key = { it.category + "/" + it.name }) { eq ->
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(shape)
                            .background(Ap.Surface)
                            .border(1.dp, Ap.Border, shape)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(eq.name, color = Ap.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(ITEM_CATEGORY_NAMES[eq.category] ?: eq.category, color = Ap.Muted, fontSize = 12.sp)
                    }
                }
            }
            else -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Yeni Build", Modifier.weight(1f)) { nav.navigate("build/${game.key}") }
                        SecondaryButton("Kod ile Ekle", Modifier.weight(1f)) { importOpen = true }
                    }
                }
                if (builds.isEmpty()) {
                    item {
                        Text(
                            "Henüz kayıtlı build yok. Yeni Build ile oluşturabilir ya da klandan gelen paylaşımı Kod ile Ekle ile içe aktarabilirsin.",
                            color = Ap.Muted, fontSize = 13.sp, lineHeight = 19.sp
                        )
                    }
                }
                items(builds, key = { it.id }) { b ->
                    ApCard {
                        Text(b.name.ifBlank { "İsimsiz build" }, color = Ap.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(b.hero, color = Ap.RedSoft, fontSize = 13.sp)
                        Text(
                            b.items.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Eşya seçilmemiş" },
                            color = Ap.TextSoft, fontSize = 13.sp, lineHeight = 19.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallButton("Düzenle", filled = false) { nav.navigate("build/${game.key}?id=${b.id}") }
                            SmallButton("Klanla Paylaş", filled = true) { shareBuild(context, b) }
                            SmallButton("Sil", filled = false) { app.games.delete(b.id) }
                        }
                    }
                }
            }
        }
    }

    if (importOpen) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importOpen = false },
            containerColor = Ap.Surface,
            title = { Text("Kod ile Ekle", color = Ap.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Klandan gelen mesajın tamamını ya da sadece APB1: ile başlayan kodu yapıştır.", color = Ap.TextSoft, fontSize = 13.sp)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Ap.Red,
                            unfocusedBorderColor = Ap.Border,
                            focusedTextColor = Ap.Text,
                            unfocusedTextColor = Ap.Text,
                            cursorColor = Ap.Red
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val b = Build.fromShareText(text)
                    when {
                        b == null -> Toast.makeText(context, "Kod okunamadı. Mesajı eksiksiz yapıştırdığından emin ol.", Toast.LENGTH_LONG).show()
                        b.game != game -> Toast.makeText(context, "Bu build ${b.game.title} için. O oyunun sekmesinden ekle.", Toast.LENGTH_LONG).show()
                        else -> {
                            app.games.save(b)
                            Toast.makeText(context, "Build eklendi: ${b.name}", Toast.LENGTH_SHORT).show()
                            importOpen = false
                        }
                    }
                }) { Text("Ekle", color = Ap.Red) }
            },
            dismissButton = { TextButton(onClick = { importOpen = false }) { Text("Vazgeç", color = Ap.TextSoft) } }
        )
    }
}
