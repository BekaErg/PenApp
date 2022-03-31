package com.example.drawingapp

import android.graphics.*
import android.util.Log
import java.util.*
import kotlin.math.*


class DynPath{

    var curX = 0f
    var prevX = 0f
    var curY = 0f
    var prevY = 0f

    var firstX = 0f
    var firstY = 0f
    var firstRad = 0f

    private val minGapFactor = 0.2f

    var leftPath = Path()
    var rightPath = Path()

    val gapFactor = 0.4f

    var normalX = 0f
    var normalY = 0f
    var norm = 1f

    private var discardedX = 0f
    private var discardedY = 0f
    private var discardedRadius = 0f

    private val pos = floatArrayOf(0f, 0f)
    private val tan = floatArrayOf(0f, 0f)

    private var mRadius = 0f
    private var mPrevRadius = 0f
    private var mDirection = Path.Direction.CCW


    var contourPath = Path()
    private var spinePath = Path().apply { fillType = Path.FillType.WINDING }
    var pathMeasrure = PathMeasure(spinePath, false)
    var tempPath = Path().apply { fillType = Path.FillType.WINDING }


    private var mDistanceRadius = mutableListOf<Pair<Float, Float>>()
    private var mPathMeasure = PathMeasure()

    private var smoothLevel = 0

    fun moveTo(x: Float, y: Float, radius: Float) {
        mDistanceRadius.add(Pair(0f, radius))

        spinePath.moveTo(x,y)
        contourPath.addCircle(x, y, radius, mDirection)
        mPrevRadius = radius

        prevX = x
        prevY = y
        firstX = x
        firstY = y
        firstRad = radius
    }

    fun lineTo(x: Float, y: Float, radius: Float) {
        if( (x - prevX).absoluteValue < minGapFactor * radius && (y - prevY).absoluteValue < minGapFactor * radius) {
            discardedX = x
            discardedY = y
            discardedRadius = radius
            return
        }
        discardedRadius = 0f

        spinePath.lineTo(x,y)
        mPathMeasure = PathMeasure(spinePath, false)
        mPathMeasure.getPosTan(mPathMeasure.length, pos, tan)

        mDistanceRadius.add(Pair(mPathMeasure.length, radius))


        curX = x
        curY = y
        mRadius = radius

        extendPath()
    }


    fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawPath(contourPath, paint)
        if (discardedRadius != 0f) {
            canvas.drawCircle(discardedX, discardedY, discardedRadius, paint)
        }

    }

    fun drawSpine(canvas: Canvas, paint: Paint) {
        canvas.drawPath(spinePath, paint)

        //canvas.drawPath(leftPath, paint)
        //canvas.drawPath(rightPath, paint)
    }


    private fun rewind() {
        spinePath.rewind()
        contourPath.rewind()
    }

    fun restart() {
        rewind()
        this.moveTo(firstX, firstY, firstRad)
    }

    fun granularContour()  {
        val pathMeasure = PathMeasure(Path(spinePath), false)
        val path = Path()

        var curDist = 0f
        var curRad = 0f
        var n = 0
        var delta = 0f

        for ((prev,cur) in mDistanceRadius.zip(mDistanceRadius.drop(1))) {
            n = ceil ((cur.first - prev.first) / (gapFactor * (prev.second + cur.second)) ).toInt()
            delta = (cur.first - prev.first) / n
            for (i in 0 until n) {
                curDist = prev.first + i*delta
                curRad = prev.second  + i * (cur.second - prev.second) / n
                pathMeasure.getPosTan(curDist,  pos, tan)
                path.addCircle(pos[0], pos[1], curRad, Path.Direction.CCW)
            }
        }
        contourPath = path
    }


    private fun updateContour()  {
        var curDist = 0f
        var n = 0
        var delta = 0f

        val pathMeasure = PathMeasure(Path(spinePath), false)
        pathMeasure.getPosTan(0f,  pos, null)

        mPrevRadius = mDistanceRadius[0].second
        prevX = pos[0]
        prevY = pos[1]

        contourPath.rewind()
        contourPath.addCircle(firstX, firstY, mPrevRadius, mDirection)

        for ((prev,cur) in mDistanceRadius.zip(mDistanceRadius.drop(1))) {
            n = smoothLevel
            //n = ceil ((cur.first - prev.first) / (3*minGapFactor * (prev.second + cur.second) / 2) ).toInt()
            delta = (cur.first - prev.first) / n

            for (i in 1 .. n) {
                curDist = prev.first + i * delta
                mRadius = prev.second  + i * (cur.second - prev.second) / n

                pathMeasure.getPosTan(curDist,  pos, tan)
                curX = pos[0]
                curY = pos[1]
                extendPath()
            }
        }
        pathMeasure.getPosTan(pathMeasure.length,  pos, tan)
        mRadius = mDistanceRadius.last().second
        curX = pos[0]
        curY = pos[1]
        extendPath()
    }




    fun quadSmooth(level: Int) {
        val n = mDistanceRadius.size
        if (level <= 0) {
            return
        }
        smoothLevel = level


        val newArr = mutableListOf<Pair<Float, Float>>()
        val newMeasure = PathMeasure()
        val curPos = floatArrayOf(0f, 0f)
        val nextPos = floatArrayOf(0f, 0f)

        mPathMeasure.getPosTan(mDistanceRadius[0].first, curPos, null)
        newArr.add(Pair (mDistanceRadius[0].first, mDistanceRadius[0].second) )

        contourPath.rewind()
        mPathMeasure = PathMeasure(spinePath, false)
        spinePath.rewind()
        spinePath.moveTo(curPos[0], curPos[1])

        for (i in level until n step level) {
            if (i < n - level){
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

            mRadius = (mDistanceRadius[i].second + mDistanceRadius[(i + level).coerceAtMost(n - 1)].second) / 2
            newMeasure.setPath(spinePath, false)
            newArr.add(Pair(newMeasure.length, mRadius) )

        }
        if (newArr.size == 1) {
            mPathMeasure.getPosTan(mDistanceRadius[n - 1].first, nextPos, null)
            spinePath.quadTo(nextPos[0], nextPos[1], nextPos[0], nextPos[1])

            mRadius = mDistanceRadius[n - 1].second
            newArr.add(Pair(newMeasure.length, mRadius) )
        }
        mDistanceRadius = newArr
        updateContour()
    }


    private var matrix = Matrix()


    private fun extendPath(){
        val v = PointF (curX - prevX, curY - prevY)
        val norm = v.length()

        v.x /= norm
        v.y /= norm

        val cosTheta = - (mRadius - mPrevRadius) / norm
        val sinTheta = sqrt( 1 - cosTheta * cosTheta)

        val leftUnitVec = PointF(v.x * cosTheta + v.y * sinTheta, -v.x * sinTheta + v.y * cosTheta )
        val rightUnitVec = PointF(v.x * cosTheta - v.y * sinTheta, v.x * sinTheta + v.y * cosTheta )

        if (norm > mRadius * minGapFactor * 0.2 && norm > (mRadius - mPrevRadius).absoluteValue ) {
            normalX /= norm
            normalY /= norm
            contourPath.moveTo(prevX + leftUnitVec.x * mPrevRadius, prevY + leftUnitVec.y * mPrevRadius)
            contourPath.lineTo(prevX + rightUnitVec.x * mPrevRadius, prevY + rightUnitVec.y * mPrevRadius)
            contourPath.lineTo(curX + rightUnitVec.x * mRadius, curY + rightUnitVec.y * mRadius)
            contourPath.lineTo(curX + leftUnitVec.x * mRadius, curY + leftUnitVec.y * mRadius)
            contourPath.close()
        } else if (false && norm < mRadius / 200f + 10f) {
            return
        }

        contourPath.addCircle(curX, curY, mRadius, mDirection)
        prevX = curX
        prevY = curY
        mPrevRadius = mRadius
    }

    /*
    private fun extendPath(){
        var normalX = -(curY - prevY)
        var normalY = (curX - prevX)
        val norm = sqrt( normalX * normalX + normalY * normalY)
        if (norm > mRadius * minGapFactor * 0.2) {
            normalX /= norm
            normalY /= norm
            contourPath.moveTo(prevX - normalX * mPrevRadius, prevY - normalY * mPrevRadius)
            contourPath.lineTo(prevX + normalX * mPrevRadius, prevY + normalY * mPrevRadius)
            contourPath.lineTo(curX + normalX * mRadius, curY + normalY * mRadius)
            contourPath.lineTo(curX - normalX * mRadius, curY - normalY * mRadius)
            contourPath.close()
        } else if (false && norm < mRadius / 200f + 10f) {
            return
        }

        contourPath.addCircle(curX, curY, mRadius, mDirection)
        prevX = curX
        prevY = curY
        mPrevRadius = mRadius
    }
     */


    fun convertToXYR(distanceArray: MutableList<Pair<Float, Float>>, path: Path, upscale: Int = 1, upsCaleOriginalArray: Boolean = false): MutableList<Triple<Float, Float, Float>>{
        val arrXYR = mutableListOf<Triple<Float, Float, Float>>()
        val arrDistRad = mutableListOf<Pair<Float, Float>>()

        var prevDist = mDistanceRadius[0].first
        var prevRadius = mDistanceRadius[0].second
        mPathMeasure.getPosTan(prevDist, pos, null)
        arrXYR.add(Triple(pos[0], pos[1], prevRadius))
        arrDistRad.add(Pair(prevDist, prevRadius))

        var nextDist: Float
        var nextRadius: Float
        var curDist: Float
        var curRadius: Float

        mPathMeasure.setPath(path, false)

        for (pair in mDistanceRadius.drop(1)) {
            nextDist = pair.first
            nextRadius = pair.second
            for (i in 1..upscale) {
                curDist = prevDist + (nextDist - prevDist) * i / upscale
                curRadius = prevRadius + (nextRadius - prevRadius) * i / upscale
                mPathMeasure.getPosTan(curDist, pos, null)
                arrXYR.add(Triple( pos[0], pos[1], curRadius))

                if (upsCaleOriginalArray) {
                    arrDistRad.add(Pair(curDist, curRadius))
                }
            }
            prevDist = nextDist
            prevRadius = nextRadius
        }
        return arrXYR
    }




}

/*
    private fun gapIsTooSmall(): Boolean {
        mGap = mEpsilon * mCurStrokeRadius
        return mCurTouchX - mPrevTouchX < mGap && mCurTouchY - mPrevTouchY < mGap && mPrevTouchX - mCurTouchX < mGap && mPrevTouchY - mCurTouchY < mGap
    }

    dash.rewind()
        dash.addCircle(0f,0f, mRadius, Path.Direction.CCW)
        paint.pathEffect = PathDashPathEffect(dash, 2*mRadius, 2f,
            PathDashPathEffect.Style.MORPH)
 */