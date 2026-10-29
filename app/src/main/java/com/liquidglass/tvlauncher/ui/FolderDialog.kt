package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liquidglass.tvlauncher.data.AppInfo
import kotlinx.coroutines.delay

/**
 * The inside of a folder: its apps as tiles you open with OK (hold OK for the app's menu, which has
 * "remove from folder"). Back closes it.
 */
@Composable
fun FolderDialog(
    name: String,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(apps.size) {
            delay(80)
            runCatching { first.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(780.dp)
                .glassPanel(26.dp, LocalTheme.current.glass.card)
                .padding(vertical = 18.dp),
        ) {
            Text(name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 28.dp, bottom = 6.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    AppTile(
                        label = app.label,
                        art = app.tile,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = if (app === apps.first()) first else null,
                        onClick = { onLaunch(app) },
                        onLongClick = { onMenu(app) },
                    )
                }
            }
        }
    }
}
