package com.chi.tvremote

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Persisted language preference. A null tag means follow the Android system locale. */
object AppLanguagePreference {
    private const val PREFS = "chi_tv_remote_settings"
    private const val KEY_LANGUAGE = "ui_language"

    data class Choice(val tag: String?, val nativeName: String)

    val choices = listOf(
        Choice(null, "跟隨系統語言"),
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
        choices.any { it.tag != null && (tag.equals(it.tag, true) || tag.startsWith(it.tag + "-", true)) }

    fun effectiveTag(context: Context): String {
        val manual = selectedTag(context)
        if (manual != null) return manual
        val system = context.resources.configuration.locales[0]?.toLanguageTag() ?: "zh-TW"
        return if (isSupported(system)) system else "zh-TW"
    }

    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(effectiveTag(base))
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppLanguagePreference.selectedTag(context)) }
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = AppLanguagePreference.choices.firstOrNull { it.tag == selected }?.nativeName
        ?: "跟隨系統語言"

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("語言 / LANGUAGE", fontSize = 12.sp)
        Text("選擇 App 顯示語言；預設跟隨手機系統語言。", fontSize = 14.sp)
        Divider()
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("介面語言") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                AppLanguagePreference.choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.nativeName) },
                        onClick = {
                            selected = choice.tag
                            AppLanguagePreference.save(context, choice.tag)
                            expanded = false
                            (context as? Activity)?.recreate()
                        }
                    )
                }
            }
        }
    }
}
