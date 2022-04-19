package com.example.drawingapp

import android.graphics.*
import android.util.Log
import kotlin.math.absoluteValue
import kotlin.math.sqrt

class PenPath {
    var minGapFactor = 0.2f
    val contourPath = Path()

    /**
     * The path thickness depends on direction of the movement
     * It is thickest when moving across to the directionBiasVector
     */
    var directionBiasVector = Pair(1f / sqrt(2f), -1f / sqrt(2f))
        set(value) {
            val norm = sqrt(value.first * value.first + value.second * value.second)
            field = if (norm == 0f) {
                Pair(1f, 0f)
            } else {
                Pair(value.first / norm, value.second / norm)
            }
        }

    /**
     * Variation between thickest and thinnest lines due to moving direction
     * (This is independent from pressure level)
     */
    var directionBiasLevel = 0.7f
        set(value) {
            field = value.coerceAtLeast(0f).coerceAtMost(1f)
        }
    /**
     * RealTime smoothing by averaging last inputBufferSize points into one
     * Useful to reduce input jitter and smoothen strokes
     * Values lower than 5 do not have significant disadvantages
     */
    var inputBufferSize = 4
        set(value) {
            field = value.coerceAtLeast(1).coerceAtMost(100)
        }

    private var curX = 0f
    private var curY = 0f
    private var prevX = 0f
    private var prevY = 0f
    private var norm = 1f
    private var mRadius = 0f
    private var mPrevRadius = 0f
    private val pos = floatArrayOf(0f, 0f)


    //private var smoothLevel = 1
    private var mPathMeasure = PathMeasure()
    private var arr = mutableListOf<Triple<Float, Float, Float>>()
    private var inputBuffer : ArrayDeque<Triple<Float, Float, Float>> = ArrayDeque()

    /**
     * similar to Path.moveTo()
     */
    fun moveTo(x : Float, y : Float, radius : Float) {
        moveToImpl(x, y, radius, true)
    }

    /**
     * similar to Path.lineTo()
     */
    fun lineTo(x : Float, y : Float, radius : Float) {
        inputBuffer.addLast(Triple(x, y, radius))
        if (inputBuffer.size > inputBufferSize) inputBuffer.removeFirst()

        //calculate average of all points in buffer and pass it to lineTo implementation
        val (sumX, sumY, sumRad) = inputBuffer.fold(Triple(0f, 0f, 0f)) { acc, value ->
            Triple(
                acc.first + value.first,
                acc.second + value.second,
                acc.third + value.third
            )
        }
        lineToImpl(
            sumX / inputBuffer.size,
            sumY / inputBuffer.size,
            sumRad / inputBuffer.size,
            true
        )
    }

    /**
     * Unloads buffer and adds to the path.
     * Only needed if inputBufferSize > 1
     */
    fun finish(addOnlyLast: Boolean = true) {
        if (addOnlyLast) {
            lineToImpl(inputBuffer.last().first, inputBuffer.last().second, inputBuffer.last().third, true)
            inputBuffer.clear()
            return
        }
        var (sumX, sumY, sumRad) = inputBuffer.fold(Triple(0f, 0f, 0f)) { acc, value ->
            Triple(
                acc.first + value.first,
                acc.second + value.second,
                acc.third + value.third
            )
        }
        while (inputBuffer.size > 1) {
            val (x, y, rad) = inputBuffer.removeFirst()
            sumX -= x
            sumY -= y
            sumRad -= rad
            lineToImpl(
                sumX / inputBuffer.size,
                sumY / inputBuffer.size,
                sumRad / inputBuffer.size,
                true
            )
        }
        inputBuffer.clear()
    }

    /**
     * Draws contour path on the canvas
     * Use fill paint to get normal brush effect
     * use smoothEffect makes drawing of the path feel more responsive
     * Only recommended to turn of smoothEffect when the paint is translucent
     */
    fun draw(canvas : Canvas, paint : Paint, realTimePreview: Boolean = true) {
        canvas.drawPath(contourPath, paint)
        if (!realTimePreview) return
        for ((x, y, _) in inputBuffer) {
            canvas.drawCircle(x, y, mRadius, paint)
        }
    }

    /**
     * similar to Path.rewind()
     */
    fun rewind() {
        contourPath.rewind()
        arr.clear()
        mPrevRadius = 0f
        inputBuffer.clear()
    }

    /**
     * reverts path to the starting point
     * common use is to draw a line
     */
    fun restart() {
        if (arr.isEmpty()) return
        val (firstX, firstY, firstRad) = arr[0]
        rewind()
        this.moveTo(firstX, firstY, firstRad)
    }

    /**
     * Transforms the path according to the transformation Matrix
     * Does not change thickness of path
     */
    fun transform(matrix : Matrix) {
        if (arr.isEmpty()) return
        val src : FloatArray = arr.flatMap { listOf(it.first, it.second) }.toFloatArray()
        matrix.mapPoints(src)
        src.zip(src.drop(1)).filterIndexed { i, _ ->
            i % 2 == 0
        }.forEachIndexed { i, (x, y) ->
            arr[i] = Triple(x, y, arr[i].third)
        }
        updateContour()
    }

    /**
     * Applies smoothing / upscaling / downscaling
     * level should be from 1 to 100
     */
    fun postSmooth(smoothType : SmoothType, level : Int) {
        when (smoothType) {
            SmoothType.SLIDING_AVERAGE -> {
                slidingAverage(level, 1)
            }
            SmoothType.QUADRATIC -> {
                quadSmooth(level, 1)
            }
            SmoothType.UPSAMPLE -> {
                quadSmooth(1, level)
            }
            SmoothType.DOWNSAMPLE -> {
                slidingAverage(1, level)
            }
        }
    }


    /**
     * Smooths line by quadratic curves.
     * higher level will smooth more.
     * The resulting path will contain upscaleFactor times more vertices
     * Use-case of upscaleFactor > 1 is when the path is have been enlarged by scaling
     */
    private fun quadSmooth(level : Int, upscaleFactor : Int = 1) {
        if (level <= 0) {
            return
        }
        mPrevRadius = arr[0].third
        prevX = arr[0].first
        prevY = arr[0].second
        val newArr = mutableListOf<Triple<Float, Float, Float>>()
        newArr.add(Triple(prevX, prevY, mPrevRadius))

        for (i in 0 until arr.size step level) {
            val (l, r) = if (i + level < arr.size) Pair(i, i + level) else Pair(
                arr.size - 1,
                arr.size - 1
            )

            mRadius = (arr[l].third + arr[r].third) / 2f
            curX = (arr[l].first + arr[r].first) / 2f
            curY = (arr[l].second + arr[r].second) / 2f
            val anchorX = arr[i].first
            val anchorY = arr[i].second

            val curArc = Path()
            curArc.moveTo(prevX, prevY)
            curArc.quadTo(anchorX, anchorY, curX, curY)
            mPathMeasure.setPath(curArc, false)
            subdivide(upscaleFactor * level, mPrevRadius, mRadius, newArr)

            mPrevRadius = mRadius
            prevX = curX
            prevY = curY
        }

        arr = newArr
        updateContour()
    }

    /**
     * If the array was modified, updateContour has to be used in order to
     * obtain new corresponding contour Path
     */
    private fun updateContour() {
        this.moveToImpl(arr[0].first, arr[0].second, arr[0].third, false)
        for ((x, y, rad) in arr.drop(0).dropLast(0)) {
            this.lineToImpl(x, y, rad, false, 0f)
        }
    }

    //subdivide path that is set to mPathMeasure into n parts.
    private fun subdivide(
        n : Int,
        prevRadius : Float,
        nextRadius : Float,
        targetArray : MutableList<Triple<Float, Float, Float>>
    ) {
        for (j in 1 .. n) {
            val d = j * mPathMeasure.length / n
            val radius = prevRadius + (nextRadius - prevRadius) * j / n
            mPathMeasure.getPosTan(d, pos, null)
            targetArray.add(Triple(pos[0], pos[1], radius))
        }
    }

    //function used in postSmooth
    private fun slidingAverage(windowSize : Int, downSampleFactor : Int) {
        if (arr.size < 2) return
        //Adding downSampleFactor copies of the last point
        //To avoid shifting of the last point
        for (i in 0 until downSampleFactor - 1) {
            val x = arr.last()
            arr.add(Triple(x.first, x.second, x.third))
        }

        val temp = mutableListOf<Triple<Float, Float, Float>>()
        for (i in 0 until arr.size step downSampleFactor) {
            val l = (i - windowSize).coerceAtLeast(0)
            val r = (i + windowSize).coerceAtMost(arr.size)
            var avgX = 0f
            var avgY = 0f
            var avgR = 0f
            for (j in l until r) {
                avgX += arr[j].first / (r - l)
                avgY += arr[j].second / (r - l)
                avgR += arr[j].third / (r - l)
            }
            temp.add(Triple(avgX, avgY, avgR))
        }
        arr = temp
        updateContour()
    }

    //helper function for moveTo()
    private fun extendContour() {
        val dx = (curX - prevX) / norm
        val dy = (curY - prevY) / norm
        val cosTheta = -(mRadius - mPrevRadius) / norm
        val sinTheta = sqrt(1 - cosTheta * cosTheta)

        val leftUnitX = dx * cosTheta + dy * sinTheta
        val leftUnitY = -dx * sinTheta + dy * cosTheta
        val rightUnitX = dx * cosTheta - dy * sinTheta
        val rightUnitY = dx * sinTheta + dy * cosTheta

        //If one circle is inside another, skip the process
        if (norm > (mRadius - mPrevRadius).absoluteValue) {
            contourPath.moveTo(prevX + leftUnitX * mPrevRadius, prevY + leftUnitY * mPrevRadius)
            contourPath.lineTo(prevX + rightUnitX * mPrevRadius, prevY + rightUnitY * mPrevRadius)
            contourPath.lineTo(curX + rightUnitX * mRadius, curY + rightUnitY * mRadius)
            contourPath.lineTo(curX + leftUnitX * mRadius, curY + leftUnitY * mRadius)
            contourPath.close()
        }
        contourPath.addCircle(curX, curY, mRadius, Path.Direction.CCW)
    }

    private fun moveToImpl(x : Float, y : Float, radius : Float, addToArr : Boolean) {
        mRadius = (1f - directionBiasLevel) * radius
        contourPath.rewind()
        contourPath.addCircle(x, y, mRadius, Path.Direction.CCW)

        if (addToArr) {
            arr.add(Triple(x, y, mRadius))
        }
        Log.i("gela", "prev ${prevX}")
        prevX = x
        prevY = y
        mPrevRadius = mRadius
    }

    private fun lineToImpl(x : Float, y : Float, radius : Float, addToArr : Boolean, gapFactor: Float = minGapFactor) {
        norm = sqrt((x - prevX) * (x - prevX) + (y - prevY) * (y - prevY))
        if (norm == 0f) {
            return
        }
        mRadius = if (addToArr) {
            radius * calculateBias((x - prevX) / norm, (y - prevY) / norm)
        } else {
            radius
        }
        if (norm < gapFactor * mRadius + 0.4f) {
            return
        }

        curX = x
        curY = y
        extendContour()

        if (addToArr) {
            arr.add(Triple(x, y, mRadius))
        }

        prevX = curX
        prevY = curY
        mPrevRadius = mRadius
    }

    private fun calculateBias(x : Float, y : Float) : Float {
        return 0.5f* ((x * directionBiasVector.first + y * directionBiasVector.second) * directionBiasLevel + 2f - directionBiasLevel)
    }


    /**
     * Enum used for different types of upscaling
     */
    enum class SmoothType {
        SLIDING_AVERAGE,
        QUADRATIC,
        UPSAMPLE,
        DOWNSAMPLE,
    }

}