package com.example.die

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.die.ui.theme.DieTheme
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

const val TICKS_PER_SECOND = 50

var resultText = mutableStateOf(" ")
var resultQueueText = mutableStateOf(" ")
val die = Die()

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val tickTask = Runnable {
        die.update()
    }
    private val scheduledExecutor = Executors.newScheduledThreadPool(1)
    private var future: ScheduledFuture<*>? = null
    private var taskIsRunning = false

    @SuppressLint("DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        for (i in 0..7) {
            for (j in 0..23) {
                // There are a lot of images so the resources are retrieved by name
                // This is not done all the time so it should not be an issue
                dieResourceIds[i][j] = resources.getIdentifier(
                    "d6_${i}_${j}",
                    "drawable",
                    applicationContext.packageName
                )
            }
        }
        resultText.value = resources.getString(R.string.roll_instruction)

        setContent {
            DieTheme {
                Scaffold(
                    bottomBar =
                    {
                        BottomAppBar(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                        {
                            HistoryText()
                        }
                    }
                )
                {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(it),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DieImage(resources.getString(R.string.die_description))
                        ResultText()
                    }
                }
            }
        }
    }

    private fun startTask() {
        if (taskIsRunning) {
            return
        }
        future = scheduledExecutor.scheduleAtFixedRate(
            tickTask,
            0,
            1000000L / TICKS_PER_SECOND,
            TimeUnit.MICROSECONDS
        )
        taskIsRunning = true
    }

    private fun stopTask() {
        if (!taskIsRunning) {
            return
        }
        future?.cancel(false)
        taskIsRunning = false
    }

    override fun onStart() {
        super.onStart()
        die.currentResourceId.intValue = dieResourceIds[0][0]
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        startTask()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, 1000000 / (TICKS_PER_SECOND * 2))
        }
        startTask()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopTask()
    }

    override fun onStop() {
        super.onStop()
        sensorManager.unregisterListener(this)
        stopTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        stopTask()
        scheduledExecutor.shutdownNow()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) {
            return
        }
        handleGesture(event.values[0], event.values[1])
    }
}

// I've tried optimizing this a few different ways and none of them beat this
//  memory and CPU-time-wise, so I guess this will have to do
@Composable
fun DieImage(description: String) {
    val scale: Float by animateFloatAsState(
        if (playDieAnimation) 1.65f else 1.25f,
        label = "die_rise_animation", animationSpec = SpringSpec(stiffness = Spring.StiffnessLow)
    )
    val dieId = die.currentResourceId
    Image(
        painter = painterResource(id = dieId.intValue),
        contentDescription = description,
        modifier = Modifier.scale(scale)
    )
}

@Composable
fun ResultText() {
    Text(text = resultText.value, fontSize = 22.sp, modifier = Modifier.padding(top = 128.dp))
}

@Composable
fun HistoryText() {
    Text(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        textAlign = TextAlign.Right,
        fontSize = 22.sp,
        text = buildAnnotatedString {
            append(resultQueueText.value.dropLast(1))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(resultQueueText.value.last())
            }
        }
    )
}