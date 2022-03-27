package com.example.drawingapp

import android.graphics.*
import kotlin.math.sqrt


class DynPath{

    var lastX = 0f
    var prevX = 0f
    var lastY = 0f
    var prevY = 0f

    var firstX = 0f
    var firstY = 0f
    var firstRad = 0f

    var mPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        style = Paint.Style.STROKE

    }

    var mRadius = 0f
    var mPrevRadius = 0f

    var mDirection = Path.Direction.CCW

    var spinePath = Path().apply { fillType = Path.FillType.WINDING }
    var pathMeasrure = PathMeasure(spinePath, false)
    var tempPath = Path().apply { fillType = Path.FillType.WINDING }


    var mKeyPoints = mutableListOf<Float>()
    var mRadiuses = mutableListOf<Float>()

    var contourPath = Path()


    fun moveTo(x: Float, y: Float, radius: Float) {

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
        spinePath.lineTo(x,y)
        //if (contourPath.isEmpty) {
            //throw Error( "you can not use lineTo before setting starting point of the path")
        //}
        lastX = x
        lastY = y
        mRadius = radius
        extendPath()

        prevX = lastX
        prevY = lastY
        mPrevRadius = mRadius
    }


    fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawPath(contourPath, paint)
    }

    fun drawSpine(canvas: Canvas, paint: Paint) {
        canvas.drawPath(spinePath, paint)
    }


    fun rewind() {
        spinePath.rewind()
        contourPath.rewind()
        mKeyPoints.clear()
        mRadiuses.clear()
    }

    fun restart() {
        rewind()

        this.moveTo(firstX, firstY, firstRad)
    }




    private fun extendPath(){
        var normalX = -(lastY - prevY)
        var normalY = (lastX - prevX)
        val norm = sqrt( normalX * normalX + normalY * normalY)
        normalX /= norm
        normalY /= norm

        tempPath.rewind()
        tempPath.moveTo(prevX - normalX * mPrevRadius, prevY - normalY * mPrevRadius)
        tempPath.lineTo(prevX + normalX * mPrevRadius, prevY + normalY * mPrevRadius)
        tempPath.lineTo(lastX + normalX * mRadius, lastY + normalY * mRadius)
        tempPath.lineTo(lastX - normalX * mRadius, lastY - normalY * mRadius)
        tempPath.close()


        contourPath.addPath(tempPath)
        //contourPath.addCircle(lastX, lastY, mRadius, Path.Direction.CCW)
        //mTempPath.addCircle(mCurTouchX, mCurTouchY, mCurStrokeRadius, Path.Direction.CW)
        //mTempPath.op(p, Path.Op.UNION)

        //mPath.addPath(mTempPath)
    }




}

/*
    private fun gapIsTooSmall(): Boolean {
        mGap = mEpsilon * mCurStrokeRadius
        return mCurTouchX - mPrevTouchX < mGap && mCurTouchY - mPrevTouchY < mGap && mPrevTouchX - mCurTouchX < mGap && mPrevTouchY - mCurTouchY < mGap
    }
 */