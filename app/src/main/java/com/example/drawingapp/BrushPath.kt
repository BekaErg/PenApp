package com.example.drawingapp

import android.graphics.*
import android.graphics.drawable.PictureDrawable
import android.util.Log
import kotlin.math.absoluteValue
import kotlin.math.sqrt

class BrushPath {
    var minGapFactor = 0.1f
    var contourPath = Path()
    var directionBias = Pair(1f/sqrt(2f), -1f/sqrt(2f))
        set(value) {
            val norm = sqrt(value.first * value.first + value.second * value.second)
            field = Pair(value.first / norm, value.second / norm)
        }

    private var curX = 0f
    private var curY = 0f
    private var prevX = 0f
    private var prevY = 0f
    private var norm = 1f
    private var mRadius = 0f
    private var mPrevRadius = 0f
    private val pos = floatArrayOf(0f, 0f)


    private var smoothLevel = 1
    private var mPathMeasure = PathMeasure()
    private var arr = mutableListOf<Triple<Float, Float, Float>>()

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
        lineToImpl(x, y, radius, true)
    }

    /**
     * Draws contour path on the canvas
     * Use fill paint to get normal brush effect
     * use smoothEffect makes drawing of the path feel more responsive
     * Only recommended to turn of smoothEffect when the paint is translucent
     */
    fun draw(canvas : Canvas, paint : Paint, smoothEffect: Boolean = true) {
        canvas.drawPath(contourPath, paint)
    }

    /**
     * similar to Path.rewind()
     */
    fun rewind() {
        contourPath.rewind()
        arr.clear()
        mPrevRadius = 0f
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
     * similar to Path.transform
     */
    fun transform(matrix: Matrix) {
        if (arr.isEmpty()) return
        val src: FloatArray = arr.flatMap{listOf(it.first, it.second)}.toFloatArray()
        matrix.mapPoints(src)
        src.zip(src.drop(1)).filterIndexed { i, _ ->
            i % 2 == 0
        }.forEachIndexed { i, (x, y) ->
            arr[i] = Triple(x, y, arr[i].third)
        }
        updateContour()
    }

    /**
     * Smooths line by quadratic curves.
     * higher level will smooth more.
     * The resulting path will contain upscaleFactor times more vertices
     * Use-case of upscaleFactor > 1 is when the path is have been enlarged by scaling
     */
    fun quadSmooth(level : Int, upscaleFactor: Int = 1) {
        if (level <= 0) {
            return
        }
        smoothLevel = level

        mPrevRadius = arr[0].third
        prevX = arr[0].first
        prevY = arr[0].second
        val newArr = mutableListOf<Triple<Float, Float, Float>>()
        newArr.add(Triple(prevX, prevY, mPrevRadius))

        for (i in 0 until arr.size step level) {
            val (l, r) = if (i + level < arr.size) Pair(i, i + level) else Pair(arr.size - 1, arr.size - 1)

            mRadius = (arr[l].third + arr[r].third) / 2f
            curX = (arr[l].first + arr[r].first) / 2f
            curY = (arr[l].second + arr[r].second) / 2f
            val anchorX = arr[i].first
            val anchorY = arr[i].second

            val curArc = Path()
            curArc.moveTo(prevX, prevY)
            curArc.quadTo(anchorX, anchorY, curX, curY)
            mPathMeasure.setPath(curArc, false)
            subdivide(upscaleFactor * smoothLevel, mPrevRadius, mRadius, newArr)

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
        for ((x,y,rad) in arr.drop(0).dropLast(0)) {
            this.lineToImpl(x, y, rad, false)
        }
    }

    //subdivide path that is set to mPathMeasure into n parts.
    private fun subdivide(n: Int, prevRadius: Float, nextRadius: Float, targetArray: MutableList<Triple<Float, Float, Float>>)  {
        for (j in 1 .. n) {
            val d = j * mPathMeasure.length / n
            val radius = prevRadius + (nextRadius - prevRadius) * j / n
            mPathMeasure.getPosTan(d, pos, null)
            targetArray.add(Triple(pos[0], pos[1], radius))
        }
    }

    //helper function for moveTo()
    private fun extendContour() {
        val dx = (curX - prevX) / norm //* 0.5f
        val dy = (curY - prevY) / norm //* 2f
        //norm = sqrt(dx * dx + dy * dy)
        val cosTheta = -(mRadius - mPrevRadius) / norm
        val sinTheta = sqrt(1 - cosTheta * cosTheta)

        val leftUnitX = dx * cosTheta + dy * sinTheta
        val leftUnitY = -dx * sinTheta + dy * cosTheta
        val rightUnitX = dx * cosTheta - dy * sinTheta
        val rightUnitY = dx * sinTheta + dy * cosTheta

        if (norm > (mRadius - mPrevRadius).absoluteValue) {
            contourPath.moveTo(prevX + leftUnitX * mPrevRadius, prevY + leftUnitY * mPrevRadius)
            contourPath.lineTo(prevX + rightUnitX * mPrevRadius, prevY + rightUnitY * mPrevRadius)
            contourPath.lineTo(curX + rightUnitX * mRadius, curY + rightUnitY * mRadius)
            contourPath.lineTo(curX + leftUnitX * mRadius, curY + leftUnitY * mRadius)
            contourPath.close()
        }
        //contourPath.addOval(curX - mRadius * 2f, curY - mRadius * 0.5f, curX + mRadius * 2f, curY + mRadius * 0.5f, Path.Direction.CCW)
        contourPath.addCircle(curX, curY, mRadius, Path.Direction.CCW)
    }

    private fun moveToImpl(x : Float, y : Float, radius : Float, addToArr: Boolean) {
        if (addToArr) {
            arr.add(Triple(x,y,radius))
        }
        contourPath.rewind()
        contourPath.addCircle(x,y,radius, Path.Direction.CCW)

        prevX = x
        prevY = y
        mPrevRadius = radius
    }
    private fun lineToImpl (x : Float, y : Float, radius : Float, addToArr: Boolean) {
        curX = x
        curY = y
        norm = sqrt((x - prevX) * (x - prevX) + (y - prevY) * (y - prevY))
        mRadius = radius// * calculateBias((curX - prevX) / norm, (curY - prevY)/norm)

        if (norm < minGapFactor * mRadius) {
            return
        }

        extendContour()
        if (addToArr) {
            arr.add(Triple(x,y,radius))
        }

        prevX = curX
        prevY = curY
        mPrevRadius = mRadius
    }

    private fun calculateBias(x: Float, y: Float): Float {
        return (x * directionBias.first + y * directionBias.second)*0.5f + 1.5f
    }

}