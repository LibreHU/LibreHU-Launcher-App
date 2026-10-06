package org.librehu.launcher.standby

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.librehu.launcher.data.NowPlaying
import org.librehu.launcher.ui.CarColors
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Standby clock on black: digital or analog, date, what is playing; drifts a little every minute. */
@Composable
fun StandbyFace(
    s: StandbySettings,
    nowPlaying: NowPlaying?,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shift by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(s.showSeconds, s.style) {
        var minute = -1L
        while (true) {
            now = System.currentTimeMillis()
            val m = now / 60_000
            if (s.shift && minute >= 0 && m != minute) shift = Offset(Random.nextFloat() * 2 - 1, Random.nextFloat() * 2 - 1)
            minute = m
            val step = if (s.showSeconds || s.style == ClockStyle.ANALOG) 1000L else 60_000L
            delay(step - now % step)
        }
    }
    val dx by animateDpAsState((shift.x * 40).dp, tween(2000), label = "dx")
    val dy by animateDpAsState((shift.y * 24).dp, tween(2000), label = "dy")
    val color = if (s.accentColor) CarColors.Accent else Color(0xFFE8EAED)
    val dim = Color(0xFF9AA0A6)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(Modifier.offset(dx, dy), horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.showPhone) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val (label, percent) =
                    remember {
                        org.librehu.launcher.data
                            .phoneDisplay(context)
                    }
                org.librehu.launcher.ui
                    .PhoneStatusView(compact = false, label = label, batteryPercent = percent, textSize = 16)
                Spacer(Modifier.height(20.dp))
            }
            when (s.style) {
                ClockStyle.DIGITAL -> Digital(now, s.showSeconds, color, dim)
                ClockStyle.ANALOG -> Analog(now, s.showSeconds, color, dim)
            }
            if (s.showDate) {
                Spacer(Modifier.height(12.dp))
                Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)), color = dim, fontSize = 26.sp)
            }
            val np = nowPlaying
            if (s.showMedia && np != null && np.playing) {
                Spacer(Modifier.height(28.dp))
                // Centred under the clock: the text only takes its own width (a fixed width left it aligned left).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        np.title.ifBlank { np.subtitle },
                        color = dim,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                    // Same width as the icon on the other side: the title stays centred on the clock.
                    Spacer(Modifier.width(34.dp))
                }
                if (np.title.isNotBlank() && np.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        np.subtitle,
                        color = dim,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Digital(
    now: Long,
    seconds: Boolean,
    color: Color,
    dim: Color,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            android.text.format.DateFormat
                .getTimeFormat(context)
                .format(Date(now)),
            color = color,
            fontSize = 150.sp,
            fontWeight = FontWeight.Thin,
        )
        if (seconds) {
            Text(
                "%02d".format((now / 1000) % 60),
                color = dim,
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.offset(y = (-28).dp),
            )
        }
    }
}

@Composable
private fun Analog(
    now: Long,
    seconds: Boolean,
    color: Color,
    dim: Color,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val sec = cal.get(Calendar.SECOND)
    val min = cal.get(Calendar.MINUTE) + sec / 60f
    val hour = cal.get(Calendar.HOUR) + min / 60f
    Canvas(Modifier.size(320.dp)) {
        val r = size.minDimension / 2
        val c = center

        fun hand(
            turns: Float,
            length: Float,
            width: Float,
            col: Color,
        ) {
            val a = turns * 2 * PI - PI / 2
            drawLine(col, c, Offset(c.x + (cos(a) * length).toFloat(), c.y + (sin(a) * length).toFloat()), width, StrokeCap.Round)
        }
        for (i in 0 until 60) {
            val a = i * PI / 30
            val outer = r * 0.98f
            val inner = if (i % 5 == 0) r * 0.86f else r * 0.93f
            drawLine(
                if (i % 5 == 0) color else dim.copy(alpha = 0.5f),
                Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
                Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
                if (i % 5 == 0) 6f else 2f,
                StrokeCap.Round,
            )
        }
        hand(hour / 12f, r * 0.5f, 14f, color)
        hand(min / 60f, r * 0.78f, 8f, color)
        if (seconds) hand(sec / 60f, r * 0.85f, 3f, CarColors.Accent)
        drawCircle(color, 10f, c)
    }
}
