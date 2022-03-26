package com.example.drawingapp

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast

class CanvasContainer (context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs)  {


    private var mScaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var mScaleFactor = 1f

    private var mdX = 0f
    private var mdY = 0f

    private var mPrevX = 0f
    private var mPrevY = 0f
    private var mCurX = 0f
    private var mCurY = 0f

    private var mPivotX = 0f
    private var mPivotY = 0f

    private var mIsInProgress = false
    private var mPointerCount = 0

    private var mTransX = 0f
    private var mTransY = 0f

    private var mPointerId = 0

    private var cancelled = false


    override fun dispatchDraw(canvas : Canvas) {
        canvas.save()
        canvas.translate(mTransX + mdX, mTransY + mdY)
        canvas.scale(mScaleFactor, mScaleFactor, mPivotX, mPivotY)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun onDraw(canvas : Canvas) {

        canvas.save()
        canvas.translate(mTransX + mdX, mTransY + mdY)

        canvas.scale(mScaleFactor, mScaleFactor, mPivotX, mPivotY)
        super.onDraw(canvas)
        canvas.restore()
    }

/*
    override fun onInterceptTouchEvent(ev : MotionEvent) : Boolean {
        //Toast.makeText(context, mScaleFactor.toString(), Toast.LENGTH_SHORT).show()
        if (ev.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {

            if (ev.pointerCount == 2) {
                mCurTouchesX = arrayListOf(ev.getX(0), ev.getX(1))
                mCurTouchesY = arrayListOf(ev.getY(0), ev.getY(1))
                if (mPrevTouchesX.isEmpty()) {
                    mPrevTouchesX = mCurTouchesX.toMutableList()
                    mPrevTouchesY = mCurTouchesY.toMutableList()
                } else {
                    mShiftX = (mCurTouchesX.sum() - mPrevTouchesX.sum()) / 2
                    mShiftY = (mCurTouchesY.sum() - mPrevTouchesY.sum()) / 2
                }
            }

            onTouchEvent(ev)
            //return true
        } else {
            mPrevTouchesX = arrayListOf()
            mPrevTouchesY = arrayListOf()
        }
        return super.onInterceptTouchEvent(ev)
    }
 */

    override fun onTouchEvent(event : MotionEvent) : Boolean {
        this.requestFocus()
        super.onTouchEvent(event)

        mPointerCount = event.pointerCount
        mScaleDetector.onTouchEvent(event)

        if (mPointerCount == 2) {
            mCurX = event.getX(0)
            mCurY = event.getY(0)
        }

        when (event.action and event.actionMasked) {

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mPointerCount == 2) {
                    mPrevX = mCurX
                    mPrevY = mCurY

                    Toast.makeText(context, "Second pointer down \n mTransx: $mPointerId" +
                            "", Toast.LENGTH_SHORT).show()
                } else {
                    cancelled = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mPointerCount == 2) {
                    mdX = mCurX - mPrevX
                    mdY = mCurY - mPrevY
                }

            }

            MotionEvent.ACTION_UP -> {

                mTransX += mdX
                mTransY += mdY
                mdX = 0f
                mdY= 0f

            }
        }
        return true
    }


    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector : ScaleGestureDetector) : Boolean {

            mPivotX = detector.focusX
            mPivotY = detector.focusY

            mIsInProgress = true

            mScaleFactor *= detector.scaleFactor
            // Don't let the object get too small or too large.
            mScaleFactor = 0.1f.coerceAtLeast(mScaleFactor.coerceAtMost(5.0f))
            invalidate()
            return true
        }

    }

    //val mDens = context.resources.displayMetrics.density
}