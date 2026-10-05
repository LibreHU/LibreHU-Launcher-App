package org.librehu.launcher.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.librehu.launcher.R
import org.librehu.launcher.ui.CarColors
import org.librehu.launcher.ui.CarTheme
import kotlin.math.abs

/**
 * SOS screen: countdown before calling the emergency number (cancellable), then large call buttons (emergency number,
 * contacts), the GPS position to read out to the operator, and the owner's / vehicle's information.
 */
class SosActivity : ComponentActivity() {
    private val countdown = mutableIntStateOf(0)
    private val location = mutableStateOf<Location?>(null)
    private val status = mutableStateOf("")
    private val now = mutableLongStateOf(System.currentTimeMillis())
    private var lm: LocationManager? = null

    /** Opened from the settings to check the screen: no automatic call. */
    private var test = false

    private val listener =
        object : LocationListener {
            override fun onLocationChanged(l: Location) {
                val old = location.value
                // Keep the GPS fix over a coarser network one of the same age.
                if (old == null || l.accuracy <= old.accuracy || l.time - old.time > 10_000) location.value = l
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?,
            ) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val s = SosStore.get(this).settings.value
        test = intent?.getBooleanExtra(EXTRA_TEST, false) == true
        if (savedInstanceState == null && !test) countdown.intValue = s.countdown
        lm = getSystemService(LocationManager::class.java)
        val missing =
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CALL_PHONE)
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        setContent { CarTheme { Screen(s) } }
    }

    override fun onStart() {
        super.onStart()
        startLocation()
    }

    override fun onStop() {
        lm?.removeUpdates(listener)
        super.onStop()
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val m = lm ?: return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                m.getLastKnownLocation(p)?.let(listener::onLocationChanged)
                m.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
            } catch (_: IllegalArgumentException) {
                // Provider absent.
            } catch (_: SecurityException) {
            }
        }
    }

    private fun call(number: String) {
        countdown.intValue = 0
        val r = SosCaller.call(this, number)
        status.value =
            getString(
                when (r) {
                    SosCaller.Result.CALLING -> R.string.sos_calling
                    SosCaller.Result.DIALER -> R.string.sos_dialer
                    SosCaller.Result.FAILED -> R.string.sos_call_failed
                },
                number,
            )
        if (r == SosCaller.Result.FAILED) Toast.makeText(this, status.value, Toast.LENGTH_LONG).show()
    }

    @Composable
    private fun Screen(s: SosSettings) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                now.longValue = System.currentTimeMillis()
                if (countdown.intValue > 0) {
                    countdown.intValue--
                    if (countdown.intValue == 0) call(s.number)
                }
            }
        }
        Row(
            Modifier.fillMaxSize().background(CarColors.Background).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val left = countdown.intValue
                if (left > 0) {
                    Text(
                        stringResource(R.string.sos_countdown, s.number, left),
                        color = SOS_RED,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    BigButton(Icons.Default.Close, stringResource(R.string.sos_cancel), CarColors.SurfaceHigh, CarColors.Text) {
                        countdown.intValue = 0
                        status.value = getString(R.string.sos_cancelled)
                    }
                } else {
                    Text(stringResource(R.string.sos_title), color = SOS_RED, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    if (test) Text(stringResource(R.string.sos_test_banner), color = CarColors.TextDim, fontSize = 16.sp)
                }
                BigButton(Icons.Default.Call, stringResource(R.string.sos_call_number, s.number), SOS_RED, Color.White) { call(s.number) }
                for (c in s.contacts) {
                    BigButton(Icons.Default.Person, c.name.ifBlank { c.number }, CarColors.SurfaceHigh, CarColors.Text) { call(c.number) }
                }
                if (status.value.isNotEmpty()) Text(status.value, color = CarColors.TextDim, fontSize = 16.sp)
                Text(stringResource(R.string.sos_bt_hint), color = CarColors.TextDim, fontSize = 14.sp)
                BigButton(null, stringResource(R.string.close), CarColors.Surface, CarColors.TextDim) { finish() }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(Icons.Default.LocationOn, stringResource(R.string.sos_position)) {
                    val l = location.value
                    if (l == null) {
                        Text(stringResource(R.string.sos_position_searching), color = CarColors.TextDim, fontSize = 18.sp)
                    } else {
                        Text(
                            "%.5f, %.5f".format(l.latitude, l.longitude),
                            color = CarColors.Text,
                            fontSize = 30.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text("${dms(l.latitude, 'N', 'S')}  ${dms(l.longitude, 'E', 'W')}", color = CarColors.Text, fontSize = 20.sp)
                        val age = ((now.longValue - l.time) / 1000).coerceAtLeast(0)
                        Text(
                            stringResource(R.string.sos_position_detail, l.accuracy.toInt(), age),
                            color = CarColors.TextDim,
                            fontSize = 15.sp,
                        )
                    }
                }
                val info = listOf(s.owner, s.medical, s.vehicle).filter { it.isNotBlank() }
                if (info.isNotEmpty()) {
                    Card(Icons.Default.Person, stringResource(R.string.sos_info)) {
                        for (line in info) Text(line, color = CarColors.Text, fontSize = 20.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun BigButton(
        icon: ImageVector?,
        label: String,
        background: Color,
        content: Color,
        onClick: () -> Unit,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = content, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
            }
            Text(label, color = content, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Start)
        }
    }

    @Composable
    private fun Card(
        icon: ImageVector,
        title: String,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CarColors.Surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = CarColors.Accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = CarColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.height(4.dp))
            content()
        }
    }

    companion object {
        const val EXTRA_TEST = "test"
        val SOS_RED = Color(0xFFD93025)

        /** 48°51'24.1"N style, easier to read out than decimals for some operators. */
        fun dms(
            v: Double,
            pos: Char,
            neg: Char,
        ): String {
            val a = abs(v)
            val d = a.toInt()
            val m = ((a - d) * 60).toInt()
            val sec = ((a - d) * 60 - m) * 60
            return "%d°%02d'%04.1f\"%c".format(d, m, sec, if (v >= 0) pos else neg)
        }
    }
}
