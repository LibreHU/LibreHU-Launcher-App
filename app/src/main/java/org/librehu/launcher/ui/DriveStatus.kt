package org.librehu.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.launcher.speed.SpeedLimitManager
import org.librehu.launcher.weather.WeatherRepo

/** Weather symbol and temperature for the shortcut bar (Settings → Driving), nothing when off or unknown. */
@Composable
fun WeatherChip() {
    val repo = WeatherRepo.get(LocalContext.current)
    val s by repo.settings.collectAsStateWithLifecycle()
    val w by repo.now.collectAsStateWithLifecycle()
    val now = w ?: return
    if (!s.enabled || !s.statusBar) return
    Text(WeatherRepo.symbol(now.code, now.day) + " " + WeatherRepo.temp(now.tempC, s.fahrenheit), color = CarColors.Text, fontSize = 18.sp)
}

/** Limit sign of the road for the shortcut bar, red inside while too fast; nothing when off or unknown. */
@Composable
fun LimitChip(size: Int = 44) {
    val m = SpeedLimitManager.get(LocalContext.current)
    val s by m.settings.collectAsStateWithLifecycle()
    val st by m.state.collectAsStateWithLifecycle()
    val kmh = st.kmh ?: return
    if (!s.enabled || !s.statusBar) return
    Box(
        Modifier
            .size(size.dp)
            .background(if (st.over) Color(0xFFE53935) else Color.White, CircleShape)
            .border((size / 9).dp, Color(0xFFE53935), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(kmh.toString(), color = if (st.over) Color.White else Color(0xFF202124), fontSize = (size * 0.38f).sp, fontWeight = FontWeight.Bold)
    }
}
