package com.vio.studio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

object Cats {
    val short = mapOf("auto" to "تلقائي", "ed" to "إيديت", "col" to "ألوان", "hdr" to "HDR", "pro" to "احترافي", "fx" to "حركة", "part" to "أجواء",
        "edit" to "مونتاج", "aud" to "صوت", "res" to "الدقة", "cv" to "واقعية", "ai" to "ذكاء اصطناعي", "out" to "تصدير")
    val icon: Map<String, ImageVector> = mapOf("auto" to Icons.Rounded.AutoFixHigh, "ed" to Icons.Rounded.GraphicEq, "col" to Icons.Rounded.Palette, "hdr" to Icons.Rounded.HdrOn,
        "pro" to Icons.Rounded.WorkspacePremium, "fx" to Icons.Rounded.Bolt, "part" to Icons.Rounded.AcUnit, "edit" to Icons.Rounded.ContentCut, "aud" to Icons.Rounded.MusicNote,
        "res" to Icons.Rounded.HighQuality, "cv" to Icons.Rounded.Biotech, "ai" to Icons.Rounded.AutoAwesome, "out" to Icons.Rounded.IosShare)
    fun of(cat: String) = BotCatalog.fx.filter { it.cat == cat }
}
