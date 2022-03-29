package com.example.drawingapp

import android.graphics.*
import android.util.Log
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.sqrt


class DynPath{

    var curX = 0f
    var prevX = 0f
    var curY = 0f
    var prevY = 0f

    var firstX = 0f
    var firstY = 0f
    var firstRad = 0f

    private val minGapFactor = 0.1f

    var leftPath = Path()
    var rightPath = Path()

    val gapFactor = 0.1f

    var normalX = 0f
    var normalY = 0f
    var norm = 1f


    private val pos = floatArrayOf(0f, 0f)
    private val tan = floatArrayOf(0f, 0f)

    private var mRadius = 0f
    private var mPrevRadius = 0f
    private var mDirection = Path.Direction.CCW


    var contourPath = Path()
    private var spinePath = Path().apply { fillType = Path.FillType.WINDING }
    var pathMeasrure = PathMeasure(spinePath, false)
    var tempPath = Path().apply { fillType = Path.FillType.WINDING }


    private var mKeyPoints = mutableListOf<Triple<Float, Float, Float>>()
    private var mDistanceRadius = mutableListOf<Pair<Float, Float>>()
    private var mPathMeasure = PathMeasure()



    fun moveTo(x: Float, y: Float, radius: Float) {
        mKeyPoints.add(Triple(x,y, radius))
        mDistanceRadius.add(Pair(0f, radius))

        spinePath.moveTo(x,y)
        leftPath.moveTo(x,y)
        rightPath.moveTo(x,y)
        contourPath.addCircle(x, y, radius, mDirection)

        mPrevRadius = radius
        prevX = x
        prevY = y
        firstX = x
        firstY = y
        firstRad = radius
    }

    fun lineTo(x: Float, y: Float, radius: Float) {
        if( (x - prevX).absoluteValue < gapFactor * radius && (y - prevY).absoluteValue < gapFactor * radius) {
            return
        }

        spinePath.lineTo(x,y)
        mPathMeasure = PathMeasure(spinePath, false)
        mPathMeasure.getPosTan(mPathMeasure.length, pos, tan)

        mKeyPoints.add(Triple(x,y, radius))
        mDistanceRadius.add(Pair(mPathMeasure.length, radius))


        curX = x
        curY = y
        mRadius = radius

        extendPath()
    }


    fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawPath(contourPath, paint)
    }

    fun drawSpine(canvas: Canvas, paint: Paint) {
        canvas.drawPath(spinePath, paint)

        //canvas.drawPath(leftPath, paint)
        //canvas.drawPath(rightPath, paint)
    }


    private fun rewind() {
        spinePath.rewind()
        contourPath.rewind()
        mKeyPoints.clear()
    }

    fun restart() {
        rewind()

        this.moveTo(firstX, firstY, firstRad)
    }

    /*

    fun finishLine() {
        contourPath = Path().apply { fillType = Path.FillType.WINDING }
        prevX = firstX
        prevY = firstY
        contourPath.moveTo(firstX, firstY)
        var i = 0;
        for (j in 0 until 2*mKeyPointsX.size - 1) {

            i = j.coerceAtMost(2 * mKeyPointsX.size - 1 - j)
            assert(i < mRadiuses.size) {
                "i is out of range $i  ${mRadiuses.size}  $j"
            }

            lastX = mKeyPointsX[i]
            lastY = mKeyPointsY[i]
            mRadius = mRadiuses[i]

            var normalX = -(lastY - prevY)
            var normalY = (lastX - prevX)
            val norm = sqrt( normalX * normalX + normalY * normalY)
            if (norm < 0.1f) {
                continue
            }
            normalX /= norm
            normalY /= norm
            contourPath.lineTo(lastX - normalX * mRadius, lastY - normalY * mRadius)

            prevX = lastX
            prevY = lastY
            mPrevRadius = mRadius
        }
        contourPath.close()
    }

     */



    fun finalPath()  {
        val pathMeasure = PathMeasure(Path(spinePath), false)
        val path = Path()

        var curDist = 0f
        var curRad = 0f
        var n = 0
        var delta = 0f
        var counter = 0
        for ((prev,cur) in mDistanceRadius.zip(mDistanceRadius.drop(1))) {
            //pathMeasure.getPosTan(prev.first,  pos, tan)
            //path.addCircle(pos[0], pos[1], 10*prev.second, Path.Direction.CCW)
            //Log.i("gela", "dist: ${prev.first}     rad ${prev.second}")
            counter += 1
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
        /*
        spinePath.addCircle(curX, curY, mRadius, Path.Direction.CCW)
        Log.i("gela", "posX: ${pos[0]}     posY${pos[1]}")
        Log.i("gela", "${pathMeasure.length}   ")
         */
    }


    fun finishLine() {
        contourPath = Path().apply { fillType = Path.FillType.WINDING }
        prevX = firstX
        prevY = firstY
        contourPath.moveTo(firstX, firstY)

        for (point in mKeyPoints) {
            curX = point.first
            curY = point.second
            mRadius = point.third

            var normalX = -(curY - prevY)
            var normalY = (curX - prevX)
            val norm = sqrt( normalX * normalX + normalY * normalY)
            if (norm < 0.01f * mRadius) {
                continue
            }
            normalX /= norm
            normalY /= norm
            contourPath.lineTo(curX - normalX * mRadius, curY - normalY * mRadius)

            prevX = curX
            prevY = curY
            mPrevRadius = mRadius
        }
        contourPath.close()
    }




    fun quadSmooth(level: Int) {
        if (level <= 0) {
            return
        }
        val n = mKeyPoints.size
        contourPath = Path().apply { fillType = Path.FillType.WINDING }
        prevX = firstX
        prevY = firstY

        spinePath.rewind()
        spinePath.moveTo(prevX, prevY)

        for (i in level until n step level) {
            if (i < n - level){
                curX = (mKeyPoints[i].first + mKeyPoints[i + level].first) / 2
                curY = (mKeyPoints[i].second + mKeyPoints[i + level].second) / 2
            } else {
                curX = mKeyPoints[n - 1].first
                curY = mKeyPoints[n - 1].second
            }
            spinePath.quadTo(mKeyPoints[i].first, mKeyPoints[i].second, curX, curY)
            prevX = curX
            prevY = curY
            mPrevRadius = mRadius
        }
        spinePath.quadTo(mKeyPoints[n - 1].first, mKeyPoints[n - 1].second, curX, curY)
    }

    private fun extendPath2(){
        var normalX = -(curY - prevY)
        var normalY = (curX - prevX)
        val norm = sqrt( normalX * normalX + normalY * normalY)
        if (norm > mRadius /5000f) {
            normalX /= norm
            normalY /= norm
            contourPath.moveTo(prevX - normalX * mPrevRadius, prevY - normalY * mPrevRadius)
            contourPath.lineTo(prevX + normalX * mPrevRadius, prevY + normalY * mPrevRadius)
            contourPath.lineTo(curX + normalX * mRadius, curY + normalY * mRadius)
            contourPath.lineTo(curX - normalX * mRadius, curY - normalY * mRadius)
            contourPath.close()
        } else if (norm < mRadius / 20f + 10f) {
            return
        }

        contourPath.addCircle(curX, curY, mRadius, mDirection)
        prevX = curX
        prevY = curY
        mPrevRadius = mRadius
    }




    private fun extendPath(){
        var normalX = -(tan[1])
        var normalY = (tan[0])
        val norm = sqrt( normalX * normalX + normalY * normalY)
        if (norm > mRadius /5000f) {
            normalX /= norm
            normalY /= norm
            contourPath.moveTo(prevX - normalX * mPrevRadius, prevY - normalY * mPrevRadius)
            contourPath.lineTo(prevX + normalX * mPrevRadius, prevY + normalY * mPrevRadius)
            contourPath.lineTo(curX + normalX * mRadius, curY + normalY * mRadius)
            contourPath.lineTo(curX - normalX * mRadius, curY - normalY * mRadius)
            contourPath.close()
        } else if (norm < mRadius / 20f + 10f) {
            return
        }

/*
        leftPath.lineTo(lastX - normalX * mRadius, lastY - normalY * mRadius)
        rightPath.lineTo(lastX + normalX * mRadius, lastY + normalY * mRadius)
 */


        //contourPath.moveTo(prevX - 1f * mPrevRadius, prevY - normalY * mPrevRadius)
        //contourPath.lineTo(lastX + 1f * mRadius, lastY + 2f * mRadius)
        //contourPath.addPath(tempPath)
        contourPath.addCircle(curX, curY, mRadius, mDirection)
        //mTempPath.addCircle(mCurTouchX, mCurTouchY, mCurStrokeRadius, Path.Direction.CW)
        //mTempPath.op(p, Path.Op.UNION)

        prevX = curX
        prevY = curY
        mPrevRadius = mRadius
    }

    /*
        private fun extendPath(){
        var normalX = -(curY - prevY)
        var normalY = (curX - prevX)
        val norm = sqrt( normalX * normalX + normalY * normalY)
        if (norm > mRadius /5000f) {
            normalX /= norm
            normalY /= norm
            contourPath.moveTo(prevX - normalX * mPrevRadius, prevY - normalY * mPrevRadius)
            contourPath.lineTo(prevX + normalX * mPrevRadius, prevY + normalY * mPrevRadius)
            contourPath.lineTo(curX + normalX * mRadius, curY + normalY * mRadius)
            contourPath.lineTo(curX - normalX * mRadius, curY - normalY * mRadius)
            contourPath.close()
        } else if (norm < mRadius / 20f + 10f) {
            return
        }

/*
        leftPath.lineTo(lastX - normalX * mRadius, lastY - normalY * mRadius)
        rightPath.lineTo(lastX + normalX * mRadius, lastY + normalY * mRadius)
 */


        //contourPath.moveTo(prevX - 1f * mPrevRadius, prevY - normalY * mPrevRadius)
        //contourPath.lineTo(lastX + 1f * mRadius, lastY + 2f * mRadius)
        //contourPath.addPath(tempPath)
        contourPath.addCircle(curX, curY, mRadius, mDirection)
        //mTempPath.addCircle(mCurTouchX, mCurTouchY, mCurStrokeRadius, Path.Direction.CW)
        //mTempPath.op(p, Path.Op.UNION)

        prevX = curX
        prevY = curY
        mPrevRadius = mRadius
    }
     */


    companion object {
        const val SMOOTHING_OFF = 0
        const val SMOOTHING_WEAK = 1
        const val SMOOTHING_NORMAL = 3
        const val SMOOTHING_STRONG = 9
        const val SMOOTHING_HUGE = 27
        const val SMOOTHING_MAX = 81

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