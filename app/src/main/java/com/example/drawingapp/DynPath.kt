package com.example.drawingapp

import android.graphics.*
import kotlin.math.*


class DynPath {
    private val minGapFactor = 0.1f

    private var curX = 0f
    private var prevX = 0f
    private var curY = 0f
    private var prevY = 0f
    private var firstX = 0f
    private var firstY = 0f
    private var firstRad = 0f
    private var norm = 1f
    private var mRadius = 0f
    private var mPrevRadius = 0f
    private var discardedX = 0f
    private var discardedY = 0f
    private var discardedRadius = 0f

    private val pos = floatArrayOf(0f, 0f)
    private val tan = floatArrayOf(0f, 0f)

    var contourPath = Path()
    var spinePath = Path().apply { fillType = Path.FillType.WINDING }
    private var mPathMeasure = PathMeasure()
    private var mDistanceRadius = mutableListOf<Pair<Float, Float>>()

    private var smoothLevel = 1

    fun moveTo(x : Float, y : Float, radius : Float) {
        mDistanceRadius.add(Pair(0f, radius))

        spinePath.moveTo(x, y)
        contourPath.addCircle(x, y, radius, Path.Direction.CCW)
        mPrevRadius = radius

        prevX = x
        prevY = y
        firstX = x
        firstY = y
        firstRad = radius
    }

    fun lineTo(x : Float, y : Float, radius : Float) {
        norm = sqrt((x - prevX) * (x - prevX) + (y - prevY) * (y - prevY))
        discardedX = x
        discardedY = y
        discardedRadius = radius
        if (norm < minGapFactor * radius) {
            return
        }
        //discardedRadius = 0f

        spinePath.lineTo(x, y)
        mPathMeasure.setPath(spinePath, false)
        mPathMeasure.getPosTan(mPathMeasure.length, pos, tan)
        mDistanceRadius.add(Pair(mPathMeasure.length, radius))

        curX = x
        curY = y
        mRadius = radius
        extendContour()
    }

    fun draw(canvas : Canvas, paint : Paint) {
        canvas.drawPath(contourPath, paint)
        //TODO fix transparent brush issue
        canvas.drawCircle(discardedX, discardedY, discardedRadius, paint)
        discardedRadius = 0f

    }

    fun drawSpine(canvas : Canvas, paint : Paint) {
        canvas.drawPath(spinePath, paint)
    }

    fun rewind() {
        spinePath.rewind()
        contourPath.rewind()
        mDistanceRadius.clear()
        mPrevRadius = 0f
    }

    fun restart() {
        rewind()
        this.moveTo(firstX, firstY, firstRad)
    }

    fun updateContour() {
        var curDist = 0f
        var n = 0
        var delta = 0f
        val pathMeasure = PathMeasure(Path(spinePath), false)
        pathMeasure.getPosTan(0f, pos, null)

        mPrevRadius = mDistanceRadius[0].second
        prevX = pos[0]
        prevY = pos[1]
        contourPath.rewind()
        contourPath.addCircle(firstX, firstY, mPrevRadius, Path.Direction.CCW)

        for ((prev, cur) in mDistanceRadius.zip(mDistanceRadius.drop(1))) {
            n = smoothLevel
            //n = ceil ((cur.first - prev.first) / (3*minGapFactor * (prev.second + cur.second) / 2) ).toInt()
            delta = (cur.first - prev.first) / n
            for (i in 1 .. n) {
                curDist = prev.first + i * delta
                mRadius = prev.second + i * (cur.second - prev.second) / n
                pathMeasure.getPosTan(curDist, pos, tan)
                curX = pos[0]
                curY = pos[1]
                norm = sqrt((curX - prevX) * (curX - prevX) + (curY - prevY) * (curY - prevY))
                extendContour()
            }
        }
    }

    private fun extendContour() {
        val dx = (curX - prevX) / norm
        val dy = (curY - prevY) / norm
        //norm = sqrt(dx * dx + dy * dy)
        val cosTheta = -(mRadius - mPrevRadius) / norm
        val sinTheta = sqrt(1 - cosTheta * cosTheta)

        val leftUnitX = dx * cosTheta + dy * sinTheta
        val leftUnitY = -dx * sinTheta + dy * cosTheta
        val rightUnitX = dx * cosTheta - dy * sinTheta
        val rightUnitY = dx * sinTheta + dy * cosTheta

        if (norm > mRadius * minGapFactor * 0.2 && norm > (mRadius - mPrevRadius).absoluteValue) {
            contourPath.moveTo(prevX + leftUnitX * mPrevRadius, prevY + leftUnitY * mPrevRadius)
            contourPath.lineTo(prevX + rightUnitX * mPrevRadius, prevY + rightUnitY * mPrevRadius)
            contourPath.lineTo(curX + rightUnitX * mRadius, curY + rightUnitY * mRadius)
            contourPath.lineTo(curX + leftUnitX * mRadius, curY + leftUnitY * mRadius)
            contourPath.close()
        }

        contourPath.addCircle(curX, curY, mRadius, Path.Direction.CCW)
        prevX = curX
        prevY = curY
        mPrevRadius = mRadius
    }

    fun quadSmooth(level : Int) {
        if (level <= 0) {
            return
        }
        smoothLevel = level
        val n = mDistanceRadius.size
        val newArr = mutableListOf<Pair<Float, Float>>()
        val newMeasure = PathMeasure()
        val curPos = floatArrayOf(0f, 0f)
        val nextPos = floatArrayOf(0f, 0f)

        mPathMeasure = PathMeasure(spinePath, false)
        mPathMeasure.getPosTan(mDistanceRadius[0].first, curPos, null)
        newArr.add(Pair(mDistanceRadius[0].first, mDistanceRadius[0].second))

        contourPath.rewind()
        spinePath.rewind()
        spinePath.moveTo(curPos[0], curPos[1])

        for (i in level until n step level) {
            if (i < n - level) {
                mPathMeasure.getPosTan(mDistanceRadius[i].first, curPos, null)
                mPathMeasure.getPosTan(mDistanceRadius[i + level].first, nextPos, null)
                curX = (curPos[0] + nextPos[0]) / 2
                curY = (curPos[1] + nextPos[1]) / 2
            } else {
                mPathMeasure.getPosTan(mDistanceRadius[i].first, curPos, null)
                mPathMeasure.getPosTan(mDistanceRadius[n - 1].first, nextPos, null)
                curX = nextPos[0]
                curY = nextPos[1]
            }
            spinePath.quadTo(curPos[0], curPos[1], curX, curY)
            mRadius =
                (mDistanceRadius[i].second + mDistanceRadius[(i + level).coerceAtMost(n - 1)].second) / 2
            newMeasure.setPath(spinePath, false)
            newArr.add(Pair(newMeasure.length, mRadius))

        }
        if (newArr.size == 1) {
            mPathMeasure.getPosTan(mDistanceRadius[n - 1].first, nextPos, null)
            spinePath.quadTo(nextPos[0], nextPos[1], nextPos[0], nextPos[1])

            mRadius = mDistanceRadius[n - 1].second
            newArr.add(Pair(newMeasure.length, mRadius))
        }
        mDistanceRadius = newArr
        updateContour()
    }

    fun convertToXYR(
        distanceArray : MutableList<Pair<Float, Float>>,
        path : Path,
        upscale : Int = 1,
        upsCaleOriginalArray : Boolean = false
    ) : MutableList<Triple<Float, Float, Float>> {
        val arrXYR = mutableListOf<Triple<Float, Float, Float>>()
        val arrDistRad = mutableListOf<Pair<Float, Float>>()

        var prevDist = mDistanceRadius[0].first
        var prevRadius = mDistanceRadius[0].second
        mPathMeasure.getPosTan(prevDist, pos, null)
        arrXYR.add(Triple(pos[0], pos[1], prevRadius))
        arrDistRad.add(Pair(prevDist, prevRadius))

        var nextDist : Float
        var nextRadius : Float
        var curDist : Float
        var curRadius : Float

        mPathMeasure.setPath(path, false)

        for (pair in mDistanceRadius.drop(1)) {
            nextDist = pair.first
            nextRadius = pair.second
            for (i in 1 .. upscale) {
                curDist = prevDist + (nextDist - prevDist) * i / upscale
                curRadius = prevRadius + (nextRadius - prevRadius) * i / upscale
                mPathMeasure.getPosTan(curDist, pos, null)
                arrXYR.add(Triple(pos[0], pos[1], curRadius))

                if (upsCaleOriginalArray) {
                    arrDistRad.add(Pair(curDist, curRadius))
                }
            }
            prevDist = nextDist
            prevRadius = nextRadius
        }
        return arrXYR
    }

    fun granularContour() {
        val pathMeasure = PathMeasure(Path(spinePath), false)
        val path = Path()

        var curDist = 0f
        var curRad = 0f
        var n = 0
        var delta = 0f

        for ((prev, cur) in mDistanceRadius.zip(mDistanceRadius.drop(1))) {
            n = ceil((cur.first - prev.first) / (minGapFactor * (prev.second + cur.second))).toInt()
            delta = (cur.first - prev.first) / n
            for (i in 0 until n) {
                curDist = prev.first + i * delta
                curRad = prev.second + i * (cur.second - prev.second) / n
                pathMeasure.getPosTan(curDist, pos, tan)
                path.addCircle(pos[0], pos[1], curRad, Path.Direction.CCW)
            }
        }
        contourPath = path
    }

}
