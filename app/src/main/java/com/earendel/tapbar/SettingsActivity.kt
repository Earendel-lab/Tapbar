package com.earendel.tapbar

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        val prefs = Prefs(this)
        setContent {
            SettingsRoot(prefs) { finish() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoot(prefs: Prefs, onBack: () -> Unit) {
    var themeMode by remember { mutableIntStateOf(prefs.themeMode) }

    StatusTapTheme(themeMode = themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        ) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ThemeRow(
                            icon = Icons.Rounded.BrightnessAuto,
                            label = "Auto",
                            selected = themeMode == 0,
                            index = 0,
                            count = 3,
                            onClick = {
                                themeMode = 0
                                prefs.themeMode = 0
                            }
                        )
                        ThemeRow(
                            icon = Icons.Rounded.WbSunny,
                            label = "Light",
                            selected = themeMode == 1,
                            index = 1,
                            count = 3,
                            onClick = {
                                themeMode = 1
                                prefs.themeMode = 1
                            }
                        )
                        ThemeRow(
                            icon = Icons.Rounded.DarkMode,
                            label = "Dark",
                            selected = themeMode == 2,
                            index = 2,
                            count = 3,
                            onClick = {
                                themeMode = 2
                                prefs.themeMode = 2
                            }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "About",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AboutSection()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Made with ♥️",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit
) {
    SegmentedCard(
        index = index,
        count = count,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_bold),
                    contentDescription = "Selected",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AboutRow(
            icon = Icons.Rounded.Person,
            title = "Developer",
            value = "Earendel",
            index = 0,
            count = 4,
            onClick = { openUrl(context, "https://earendel.pages.dev/") }
        )
        AboutRow(
            icon = Icons.Rounded.Code,
            title = "Source code",
            value = "GitHub",
            index = 1,
            count = 4,
            onClick = { openUrl(context, "https://github.com/Earendel-lab/Tapbar") }
        )
        AboutRow(
            icon = Icons.Rounded.Star,
            title = "Star the project",
            value = "GitHub",
            index = 2,
            count = 4,
            onClick = { openUrl(context, "https://github.com/Earendel-lab/Tapbar") }
        )
        AboutRow(
            icon = Icons.Rounded.Description,
            title = "License",
            value = "Open Source License",
            index = 3,
            count = 4,
            onClick = { openUrl(context, "https://github.com/Earendel-lab/Tapbar/blob/main/LICENSE") }
        )
    }
}

@Composable
fun AboutRow(
    icon: ImageVector,
    title: String,
    value: String,
    index: Int,
    count: Int,
    onClick: () -> Unit
) {
    SegmentedCard(
        index = index,
        count = count,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_up_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
