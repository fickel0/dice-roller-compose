package com.example.die

import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.random.Random

const val GESTURE_ACCEL_MAG_THRESH_START = 6f
const val GESTURE_ACCEL_MAG_THRESH_END = 4f
const val GESTURE_VELOCITY_MAG_MAX = 20f
const val GESTURE_SENSE_CHANGE_THRESH = 1

const val ROLL_ROTATION_STEP = 15f
const val ROLL_ROTATION_STEP_AMOUNT = 24 // 360 / 15
const val ROLL_ANGLE_SAMPLE_AMOUNT = 3
const val ROLL_START_DELAY_TICKS = 10
const val ROLL_VELOCITY_MAG_THRESH_START = 3f
const val ROLL_VELOCITY_MAG_THRESH_END = 2f
const val ROLL_ROTATION_PER_MPS = 75f
const val ROLL_FRICTION_COEFFICIENT = 0.45

const val RESULT_QUEUE_SIZE_MAX = 12

val getMag = { a: Float, b: Float -> sqrt(a.pow(2) + b.pow(2)) }

// [direction index][rotation index]
val dieResourceIds = Array(8) { Array(24) { 0 } }

// Possible results in horizontal direction (index 0)
val dieResultRotationHorizontal = intArrayOf(1, 3, 6, 4)

// Possible results in vertical direction (index 4)
val dieResultRotationVertical = intArrayOf(1, 5, 6, 2)

// Possible results in both directions
val dieResultRotationIntersection = intArrayOf(1, 6)

var isGestureHappening = false
var gestureSenseChangeCount = 0
var accelMagLast = 0f
var rollAngleSampleQueue = ArrayDeque<Double>()
var rollAngle = 0.0
var rollStartDelayCounter = 0
var pickedUpVelocityMag = 0f
var playDieAnimation = false

// I think a state machine would be appropriate for this
fun handleGesture(accelX: Float, accelY: Float) {
    val accelMag = getMag(accelX, accelY)
    if (isGestureHappening) {
        var angle = atan2(accelY.toDouble(), accelX.toDouble())
        if (angle < 0) {
            angle += 2 * PI
        }
        rollAngleSampleQueue.addLast(angle)
        if (rollAngleSampleQueue.size > ROLL_ANGLE_SAMPLE_AMOUNT) {
            rollAngleSampleQueue.removeFirst()
        }

        if (accelMag < GESTURE_ACCEL_MAG_THRESH_END &&
            accelMagLast < GESTURE_ACCEL_MAG_THRESH_END
        ) {
            isGestureHappening = false
        }

        if (gestureSenseChangeCount < GESTURE_SENSE_CHANGE_THRESH) {
            if ((rollAngleSampleQueue.size == ROLL_ANGLE_SAMPLE_AMOUNT) &&
                (rollAngleSampleQueue[1] - rollAngleSampleQueue[0]) > PI
            ) {
                gestureSenseChangeCount++
            }
        } else {
            if (rollStartDelayCounter >= ROLL_START_DELAY_TICKS * 2) {
                if (pickedUpVelocityMag > ROLL_VELOCITY_MAG_THRESH_START) {
                    playDieAnimation = true
                    resultText.value = "..."
                    angle = 0.0
                    for (sample in rollAngleSampleQueue) {
                        angle += sample
                    }
                    angle /= rollAngleSampleQueue.size
                    rollAngle = angle
                    die.setDirectionFromAngle(rollAngle)

                    die.velocityMag = pickedUpVelocityMag
                    die.velocityMag = min(die.velocityMag, GESTURE_VELOCITY_MAG_MAX)

                    if (!isGestureHappening) {
                        playDieAnimation = false
                        die.startRoll()
                    }
                } else {
                    isGestureHappening = false
                }
            }
            rollStartDelayCounter++
        }
        pickedUpVelocityMag += accelMag / TICKS_PER_SECOND
    } else if (((accelMag - accelMagLast) > GESTURE_ACCEL_MAG_THRESH_START) && !die.isRolling) {
        gestureSenseChangeCount = 0
        isGestureHappening = true
        pickedUpVelocityMag = 0f
        rollStartDelayCounter = 0
    }
    accelMagLast = accelMag
}

class Die {
    var currentResourceId = mutableIntStateOf(0) // for rendering
    var velocityMag = 0f
    var isRolling = false

    private var rotation = 0.0
    private var result = 1
    private var resultQueue = ArrayDeque<Int>()
    private var hasResultQueueOverflowed = false
    private var isEndingRoll = false

    private var sprDirectionIdx = 0
    private var sprReverseAnim = true
    private var sprRotationIdx = 0
    private var sprRotationIdxTarget = 0

    fun setDirectionFromAngle(angle: Double) {
        sprDirectionIdx = round((1 - angle / (2 * PI)) * 16).toInt() % 8
        sprReverseAnim = angle > PI / 16f && angle <= PI + PI / 16f
    }

    fun startRoll() {
        result = Random.nextInt(1, 7)
        resultQueue.addLast(result)
        if (resultQueue.size > RESULT_QUEUE_SIZE_MAX) {
            resultQueue.removeFirst()
            hasResultQueueOverflowed = true
        }
        var resultIsInHorizontalDir = result in dieResultRotationHorizontal

        if (result in dieResultRotationIntersection) {
            resultIsInHorizontalDir = sprDirectionIdx in intArrayOf(2, 1, 0, 7)
        }
        if (resultIsInHorizontalDir) {
            sprDirectionIdx = 0
            sprRotationIdxTarget = dieResultRotationHorizontal.indexOf(result) * 6
        } else {
            sprDirectionIdx = 4
            sprRotationIdxTarget = dieResultRotationVertical.indexOf(result) * 6
        }
        isRolling = true
        isEndingRoll = false
    }

    fun update() {
        if (isRolling) {
            if (!isEndingRoll) {
                if (velocityMag > ROLL_VELOCITY_MAG_THRESH_END) {
                    velocityMag *= (1.0 - ((1.0 - ROLL_FRICTION_COEFFICIENT) / TICKS_PER_SECOND)).toFloat()
                } else {
                    velocityMag = ROLL_VELOCITY_MAG_THRESH_END
                    isEndingRoll = true
                }
            } else if (sprRotationIdx == sprRotationIdxTarget) {
                velocityMag = 0f
                isEndingRoll = false
                isRolling = false
                resultText.value = result.toString()
                resultQueueText.value = resultQueue.joinToString(", ")
                if (hasResultQueueOverflowed) {
                    resultQueueText.value = "... " + resultQueueText.value
                }
            }
        }

        if (sprReverseAnim) {
            rotation -= velocityMag * ROLL_ROTATION_PER_MPS / TICKS_PER_SECOND
        } else {
            rotation += velocityMag * ROLL_ROTATION_PER_MPS / TICKS_PER_SECOND
        }
        sprRotationIdx = (rotation / ROLL_ROTATION_STEP).toInt()
        sprRotationIdx %= ROLL_ROTATION_STEP_AMOUNT
        if (sprRotationIdx < 0) {
            sprRotationIdx += ROLL_ROTATION_STEP_AMOUNT
        }

        currentResourceId.intValue = dieResourceIds[sprDirectionIdx][sprRotationIdx]
    }
}