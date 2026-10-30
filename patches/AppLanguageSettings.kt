package com.chi.tvremote

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Language preference foundation. A null tag means follow the Android system locale. */
object AppLanguagePreference {
    private const val PREFS = "chi_tv_remote_settings"
    private const val KEY_LANGUAGE = "ui_language"

    data class Choice(val tag: String, val nativeName: String)

    val choices = listOf(
        Choice("zh-TW", "繁體中文"),
        Choice("zh-CN", "简体中文"),
        Choice("en", "English"),
        Choice("ja", "日本語"),
        Choice("ko", "한국어"),
        Choice("th", "ภาษาไทย"),
        Choice("vi", "Tiếng Việt")
    )

    fun selectedTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)

    fun save(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (tag == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, tag)
        }.apply()
    }

    fun isSupported(tag: String): Boolean =
        choices.any { tag.equals(it.tag, ignoreCase = true) || tag.startsWith(it.tag + "-", ignoreCase = true) }

    fun effectiveTag(context: Context): String {
        val manual = selectedTag(context)
        if (manual != null) return manual
        val system = context.resources.configuration.locales[0]?.toLanguageTag() ?: "zh-TW"
        return if (isSupported(system)) system else "zh-TW"
    }
}

@Composable
fun LanguageSettingsScreen() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppLanguagePreference.selectedTag(context)) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("語言 / LANGUAGE", fontSize = 12.sp)
        Text("預設跟隨手機系統語言；也可以手動指定介面語言。", fontSize = 14.sp)
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected == null, onClick = {
                selected = null
                AppLanguagePreference.save(context, null)
                (context as? Activity)?.recreate()
            })
            Column {
                Text("跟隨系統語言")
                Text("Follow system", fontSize = 12.sp)
            }
        }
        AppLanguagePreference.choices.forEach { choice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == choice.tag, onClick = {
                    selected = choice.tag
                    AppLanguagePreference.save(context, choice.tag)
                    (context as? Activity)?.recreate()
                })
                Text(choice.nativeName)
            }
        }
    }
}
