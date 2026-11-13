package com.replica.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.replica.app.AppState
import com.replica.app.Picked
import com.replica.app.Screen
import com.replica.app.data.MediaUtil
import com.replica.app.data.Prefs
import com.replica.app.data.ProjectMeta
import com.replica.app.data.ProjectStore
import com.replica.app.data.t
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- helpers

private fun clock(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

private fun sizeText(b: Long): String =
    if (b <= 0) "" else if (b >= 1_048_576) String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
    else String.format(Locale.US, "%d KB", b / 1024)

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier, size: Int = 36) {
    BasicText(text, modifier, style = label(size, FontWeight.Bold, Color.White, -0.9f))
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier, alpha: Float = 0.62f, size: Int = 16) {
    BasicText(
        text, modifier,
        style = label(size, FontWeight.Normal, Color.White.copy(alpha = alpha)).copy(lineHeight = (size + 6).sp)
    )
}

// ---------------------------------------------------------------- shell

@Composable
fun MainShell() {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = AppState.tab, animationSpec = tween(300), label = "tab") { tab ->
            if (tab == 1) ConverterTab() else ProjectsTab()
        }
        BottomBar(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BottomBar(modifier: Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 40.dp, vertical = 16.dp)
            .fillMaxWidth()
            .appear(300)
            .glass(RoundedCornerShape(30.dp), glow = 0.2f)
            .padding(6.dp)
    ) {
        TabItem(Glyph.Projects, t("projects"), AppState.tab == 0, Modifier.weight(1f)) { AppState.tab = 0 }
        TabItem(Glyph.Converter, t("converter"), AppState.tab == 1, Modifier.weight(1f)) { AppState.tab = 1 }
    }
}

@Composable
private fun TabItem(icon: Glyph, text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent, tween(240), label = "tabbg")
    val fg by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.55f), tween(240), label = "tabfg")
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Glyphs(icon, fg, 22.dp)
        Spacer(Modifier.width(8.dp))
        BasicText(text, style = label(15, FontWeight.SemiBold, fg))
    }
}

// ---------------------------------------------------------------- converter

@Composable
private fun ConverterTab() {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) AppState.pick(uri)
    }
    val picked = AppState.picked
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            IconPill(Glyph.Settings, { AppState.screen = Screen.SETTINGS }, delayMs = 100)
        }
        Spacer(Modifier.weight(0.7f))
        Heading(t("headline"), Modifier.appear(0), 40)
        Spacer(Modifier.height(12.dp))
        Body(t("sub"), Modifier.appear(90))
        Spacer(Modifier.height(32.dp))
        if (picked == null) {
            GlassButton(
                t("select_video"), { launcher.launch(arrayOf("video/*")) },
                primary = true, delayMs = 180, height = 64.dp, icon = Glyph.Video
            )
            Spacer(Modifier.height(14.dp))
            Body(t("no_video_hint"), Modifier.appear(260), alpha = 0.4f, size = 14)
        } else {
            VideoCard(picked)
            Spacer(Modifier.height(16.dp))
            GlassButton(t("convert"), { AppState.startConversion() }, primary = true, delayMs = 120, height = 64.dp)
            Spacer(Modifier.height(12.dp))
            GlassButton(t("change_video"), { launcher.launch(arrayOf("video/*")) }, delayMs = 200, height = 52.dp)
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun VideoCard(p: Picked) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val bmp = p.thumb
            if (bmp != null) {
                Image(
                    bmp, null,
                    Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(12.dp))
            }
            BasicText(
                p.name, Modifier.padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = label(16, FontWeight.SemiBold)
            )
            Spacer(Modifier.height(4.dp))
            val meta = listOf(clock(p.durationMs / 1000), "${p.width}x${p.height}", sizeText(p.size))
                .filter { it.isNotEmpty() }.joinToString("   -   ")
            Body(meta, Modifier.padding(horizontal = 4.dp), alpha = 0.55f, size = 14)
        }
    }
}

// ---------------------------------------------------------------- projects

@Composable
private fun ProjectsTab() {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Heading(t("projects"), Modifier.appear(0))
        Spacer(Modifier.height(18.dp))
        val list = AppState.projects
        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.appear(120)) {
                    BasicText(t("no_projects"), style = label(20, FontWeight.SemiBold))
                    Spacer(Modifier.height(6.dp))
                    Body(t("no_projects_hint"), alpha = 0.5f, size = 15)
                }
            }
            Spacer(Modifier.height(96.dp))
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(list, key = { it.id }) { ProjectCard(it) }
            }
        }
    }
}

@Composable
private fun ProjectCard(m: ProjectMeta) {
    val thumb = remember(m.id) {
        try {
            BitmapFactory.decodeFile(ProjectStore.thumbFile(m.id).absolutePath)?.asImageBitmap()
        } catch (e: Exception) { null }
    }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) {
        if (confirm) { kotlinx.coroutines.delay(2500); confirm = false }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    AppState.resultId = m.id; AppState.screen = Screen.RESULT
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(72.dp, 92.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f))) {
                if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = label(17, FontWeight.SemiBold))
                Spacer(Modifier.height(4.dp))
                val bpm = if (m.bpm > 0) "${Math.round(m.bpm)} BPM" else null
                val line = listOfNotNull(
                    String.format(t("clips_count"), m.clips), bpm, clock(m.durationMs / 1000)
                ).joinToString("   -   ")
                Body(line, alpha = 0.55f, size = 14)
                Spacer(Modifier.height(2.dp))
                Body(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(m.createdAt)), alpha = 0.38f, size = 13)
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (confirm) Color(0x55FF3B30) else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (confirm) AppState.deleteProject(m.id) else confirm = true
                    },
                contentAlignment = Alignment.Center
            ) { Glyphs(Glyph.Trash, if (confirm) Color(0xFFFF6B63) else Color.White.copy(alpha = 0.6f), 22.dp) }
        }
    }
}

// ---------------------------------------------------------------- progress

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun ProgressScreen() {
    KeepScreenOn()
    val p = AppState.progress
    val err = AppState.error
    val frac by animateFloatAsState(p.fraction, tween(500), label = "frac")
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "spin"
    )
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        if (err != null) {
            Heading(t("error_title"), size = 30)
            Spacer(Modifier.height(12.dp))
            Body(err, alpha = 0.6f, size = 15)
            Spacer(Modifier.weight(1f))
            GlassButton(t("back"), { AppState.screen = Screen.MAIN }, primary = true)
            return@Column
        }
        BasicText(
            when (p.stage) { 0 -> t("stage_video"); 1 -> t("stage_audio"); else -> t("stage_xml") },
            style = label(18, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f))
        )
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(250.dp).glass(CircleShape, glow = 0.28f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val tl = Offset(inset, inset)
                drawArc(
                    Color.White.copy(alpha = 0.10f), -90f, 360f, false, tl, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                rotate(-90f) {
                    drawArc(
                        Brush.sweepGradient(listOf(Color(0xFF6F7DFF), Color.White)),
                        0f, 360f * frac.coerceIn(0.004f, 1f), false, tl, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                rotate(spin) {
                    drawArc(
                        Color.White.copy(alpha = 0.18f), -90f, 26f, false,
                        Offset(inset - 7.dp.toPx(), inset - 7.dp.toPx()),
                        Size(arcSize.width + 14.dp.toPx(), arcSize.height + 14.dp.toPx()),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            BasicText("${(frac * 100).toInt().coerceIn(0, 100)}%", style = label(52, FontWeight.Bold, Color.White, -1.5f))
        }
        Spacer(Modifier.weight(1f))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Body(t("eta"), alpha = 0.6f, size = 15)
                    BasicText(
                        if (p.etaSec < 0) t("calculating") else "~ " + clock(if (frac >= 0.995f) 0 else p.etaSec),
                        style = label(17, FontWeight.SemiBold)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Body(t("elapsed"), alpha = 0.6f, size = 15)
                    BasicText(clock(p.elapsedSec), style = label(17, FontWeight.SemiBold))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GlassButton(t("cancel"), { AppState.cancel() }, height = 54.dp)
    }
}

// ---------------------------------------------------------------- result

@Composable
fun ResultScreen() {
    val ctx = LocalContext.current
    val id = AppState.resultId
    val meta = AppState.projects.firstOrNull { it.id == id }
    if (meta == null) {
        LaunchedEffect(Unit) { AppState.screen = Screen.MAIN }
        return
    }
    val file = ProjectStore.xmlFile(meta.id)
    val fileName = "Replica_" + meta.name.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".xml"
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        IconPill(Glyph.Back, { AppState.screen = Screen.MAIN })
        Spacer(Modifier.height(22.dp))
        Heading(t("ready"), Modifier.appear(0), 34)
        Spacer(Modifier.height(6.dp))
        BasicText(meta.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = label(16, FontWeight.Medium, Color.White.copy(alpha = 0.55f)))
        Spacer(Modifier.height(22.dp))

        val bpmText = if (meta.hasAudio && meta.bpm > 0) Math.round(meta.bpm).toString() else if (meta.hasAudio) t("bpm_unknown") else t("no_audio")
        val stats = listOf(
            t("stat_clips") to meta.clips.toString(),
            t("stat_bpm") to bpmText,
            t("stat_flash") to meta.flashes.toString(),
            t("stat_fades") to meta.fades.toString(),
            t("stat_motion") to meta.motionClips.toString(),
            t("stat_length") to clock(meta.durationMs / 1000)
        )
        for (r in 0 until 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in 0 until 2) {
                    val (k, v) = stats[r * 2 + c]
                    GlassCard(Modifier.weight(1f), delayMs = 80 + r * 70 + c * 40) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Body(k, alpha = 0.55f, size = 13)
                            Spacer(Modifier.height(2.dp))
                            BasicText(v, style = label(28, FontWeight.Bold, Color.White, -0.5f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(6.dp))
        Body(t("how_to"), Modifier.appear(320), alpha = 0.5f, size = 14)
        Spacer(Modifier.height(22.dp))

        GlassButton(
            t("open_am"), {
                when (MediaUtil.openInAlight(ctx, file, fileName)) {
                    1 -> AppState.showToast(t("am_manual"))
                    2 -> AppState.showToast(t("am_missing"))
                }
            },
            primary = true, delayMs = 380, height = 64.dp, icon = Glyph.Open
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(
                t("save"), {
                    val ok = MediaUtil.saveXmlToDownloads(ctx, file, fileName)
                    AppState.showToast(if (ok) t("saved_to") else t("save_failed"))
                },
                Modifier.weight(1f), delayMs = 440, height = 54.dp, icon = Glyph.Download
            )
            GlassButton(
                t("share"), { MediaUtil.share(ctx, file) },
                Modifier.weight(1f), delayMs = 500, height = 54.dp, icon = Glyph.Share
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        IconPill(Glyph.Back, { AppState.screen = Screen.MAIN })
        Spacer(Modifier.height(22.dp))
        Heading(t("settings"), Modifier.appear(0), 34)
        Spacer(Modifier.height(22.dp))

        GlassCard(Modifier.fillMaxWidth(), delayMs = 60) {
            Column(Modifier.padding(16.dp)) {
                BasicText(t("language"), style = label(16, FontWeight.SemiBold))
                Spacer(Modifier.height(12.dp))
                Segmented(listOf("English", "Türkçe"), if (Prefs.lang == "tr") 1 else 0, {
                    Prefs.setLang(if (it == 1) "tr" else "en")
                })
            }
        }
        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.fillMaxWidth(), delayMs = 120) {
            Column(Modifier.padding(16.dp)) {
                BasicText(t("sensitivity"), style = label(16, FontWeight.SemiBold))
                Spacer(Modifier.height(12.dp))
                Segmented(
                    listOf(t("sens_low"), t("sens_normal"), t("sens_high")), Prefs.sensitivity,
                    { Prefs.setSensitivity(it) }
                )
                Spacer(Modifier.height(10.dp))
                Body(t("sens_hint"), alpha = 0.5f, size = 13)
            }
        }
        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.fillMaxWidth(), delayMs = 180) {
            Column(Modifier.padding(16.dp)) {
                ToggleRow(t("beat_markers"), t("beat_markers_hint"), Prefs.beatMarkers) { Prefs.setBeatMarkers(it) }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                Spacer(Modifier.height(16.dp))
                ToggleRow(t("reference"), t("reference_hint"), Prefs.referenceLayer) { Prefs.setReferenceLayer(it) }
            }
        }
        Spacer(Modifier.height(24.dp))
        BasicText(
            t("about"), Modifier.fillMaxWidth(), style = label(14, FontWeight.Medium, Color.White.copy(alpha = 0.35f)).copy(textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = label(16, FontWeight.SemiBold))
            Spacer(Modifier.height(3.dp))
            Body(hint, alpha = 0.5f, size = 13)
        }
        Spacer(Modifier.width(14.dp))
        GlassSwitch(checked, onChange)
    }
}
