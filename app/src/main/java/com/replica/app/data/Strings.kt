package com.replica.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Prefs {
    private lateinit var sp: SharedPreferences
    var lang by mutableStateOf("en")
        private set
    var sensitivity by mutableStateOf(1)
        private set
    var beatMarkers by mutableStateOf(true)
        private set
    var referenceLayer by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("replica", Context.MODE_PRIVATE)
        lang = sp.getString("lang", "en") ?: "en"
        sensitivity = sp.getInt("sens", 1)
        beatMarkers = sp.getBoolean("beats", true)
        referenceLayer = sp.getBoolean("ref", false)
    }

    fun setLang(v: String) { lang = v; sp.edit().putString("lang", v).apply() }
    fun setSensitivity(v: Int) { sensitivity = v; sp.edit().putInt("sens", v).apply() }
    fun setBeatMarkers(v: Boolean) { beatMarkers = v; sp.edit().putBoolean("beats", v).apply() }
    fun setReferenceLayer(v: Boolean) { referenceLayer = v; sp.edit().putBoolean("ref", v).apply() }
}

/** Reads Prefs.lang (Compose state), so any composable calling t() recomposes on language change. */
fun t(key: String): String {
    val table = if (Prefs.lang == "tr") TR else EN
    return table[key] ?: EN[key] ?: key
}

private val EN = mapOf(
    "headline" to "Make it happen",
    "sub" to "Pick an edit. Replica rebuilds its cuts, effects and song as an Alight Motion project.",
    "select_video" to "Select Video",
    "change_video" to "Change video",
    "convert" to "Convert",
    "projects" to "Projects",
    "converter" to "Converter",
    "settings" to "Settings",
    "no_projects" to "No projects yet",
    "no_projects_hint" to "Converted edits will appear here.",
    "converting" to "Converting",
    "stage_video" to "Analyzing frames",
    "stage_audio" to "Extracting the song",
    "stage_xml" to "Building the XML",
    "eta" to "Estimated time remaining",
    "calculating" to "Estimating",
    "elapsed" to "Elapsed",
    "cancel" to "Cancel",
    "ready" to "Your XML is ready",
    "stat_clips" to "Clips",
    "stat_bpm" to "BPM",
    "stat_flash" to "Flashes",
    "stat_fades" to "Fades",
    "stat_motion" to "Moving clips",
    "stat_length" to "Length",
    "no_audio" to "None",
    "open_am" to "Open in Alight Motion",
    "save" to "Save to Downloads",
    "share" to "Share",
    "done" to "Done",
    "saved_to" to "Saved to Downloads/Replica",
    "save_failed" to "Could not save the file",
    "am_missing" to "Alight Motion is not installed",
    "am_manual" to "Saved to Downloads/Replica. In Alight Motion, import the XML from Templates.",
    "how_to" to "Replace each clip layer with your own footage. The song and beat markers are already on the timeline.",
    "error_title" to "Conversion failed",
    "back" to "Back",
    "delete" to "Delete",
    "tap_again" to "Tap again",
    "language" to "Language",
    "sensitivity" to "Cut detection",
    "sens_low" to "Low",
    "sens_normal" to "Normal",
    "sens_high" to "High",
    "sens_hint" to "Higher finds more cuts in fast edits. Lower avoids false cuts in shaky footage.",
    "beat_markers" to "Beat markers",
    "beat_markers_hint" to "Add the detected beats as bookmarks on the timeline.",
    "reference" to "Reference video layer",
    "reference_hint" to "Add the original edit as a hidden, muted layer for comparison.",
    "about" to "Replica 1.0.0",
    "video_info" to "%s  -  %s  -  %s",
    "stage_fallback" to "Compatibility decoding",
    "bpm_unknown" to "-",
    "min_short" to "min",
    "sec_short" to "s",
    "clips_count" to "%d clips",
    "pick_failed" to "Could not read this video",
    "no_video_hint" to "MP4, MOV and other formats supported by your device.",
)

private val TR = mapOf(
    "headline" to "İstediğini gerçekleştir",
    "sub" to "Bir edit seç. Replica kesimlerini, efektlerini ve şarkısını Alight Motion projesi olarak yeniden kurar.",
    "select_video" to "Video Seç",
    "change_video" to "Videoyu değiştir",
    "convert" to "Dönüştür",
    "projects" to "Projelerim",
    "converter" to "Dönüştürücü",
    "settings" to "Ayarlar",
    "no_projects" to "Henüz proje yok",
    "no_projects_hint" to "Dönüştürdüğün editler burada görünür.",
    "converting" to "Dönüştürülüyor",
    "stage_video" to "Kareler analiz ediliyor",
    "stage_audio" to "Şarkı ayıklanıyor",
    "stage_xml" to "XML hazırlanıyor",
    "eta" to "Tahmini kalan süre",
    "calculating" to "Hesaplanıyor",
    "elapsed" to "Geçen süre",
    "cancel" to "İptal",
    "ready" to "XML hazır",
    "stat_clips" to "Klip",
    "stat_bpm" to "BPM",
    "stat_flash" to "Flash",
    "stat_fades" to "Karartma",
    "stat_motion" to "Hareketli klip",
    "stat_length" to "Süre",
    "no_audio" to "Yok",
    "open_am" to "Alight Motion'da Aç",
    "save" to "İndirilenlere Kaydet",
    "share" to "Paylaş",
    "done" to "Bitti",
    "saved_to" to "Downloads/Replica klasörüne kaydedildi",
    "save_failed" to "Dosya kaydedilemedi",
    "am_missing" to "Alight Motion yüklü değil",
    "am_manual" to "Downloads/Replica klasörüne kaydedildi. Alight Motion'da Şablonlar bölümünden XML'i içe aktar.",
    "how_to" to "Her klip katmanını kendi görüntünle değiştir. Şarkı ve vuruş işaretleri zaten zaman çizelgesinde.",
    "error_title" to "Dönüştürme başarısız",
    "back" to "Geri",
    "delete" to "Sil",
    "tap_again" to "Tekrar dokun",
    "language" to "Dil",
    "sensitivity" to "Kesim algılama",
    "sens_low" to "Düşük",
    "sens_normal" to "Normal",
    "sens_high" to "Yüksek",
    "sens_hint" to "Yüksek, hızlı editlerde daha çok kesim bulur. Düşük, titrek görüntülerde yanlış kesimi önler.",
    "beat_markers" to "Vuruş işaretleri",
    "beat_markers_hint" to "Algılanan vuruşları zaman çizelgesine yer imi olarak ekle.",
    "reference" to "Referans video katmanı",
    "reference_hint" to "Karşılaştırma için orijinal editi gizli ve sessiz katman olarak ekle.",
    "about" to "Replica 1.0.0",
    "video_info" to "%s  -  %s  -  %s",
    "stage_fallback" to "Uyumluluk çözümlemesi",
    "bpm_unknown" to "-",
    "min_short" to "dk",
    "sec_short" to "sn",
    "clips_count" to "%d klip",
    "pick_failed" to "Bu video okunamadı",
    "no_video_hint" to "Cihazının desteklediği MP4, MOV ve diğer formatlar.",
)
