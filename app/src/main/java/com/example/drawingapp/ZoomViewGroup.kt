package com.example.drawingapp

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.*
import android.widget.LinearLayout
import android.widget.Toast

open class ZoomViewGroup (context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs)  {

    var lockZoom = false
    var maxZoom = 20f
    var minZoom = 0.3f
    private var mScaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var mScale = 1f
    private var mScaleFactor = 1f

    private var mPrevX = 0f
    private var mPrevY = 0f
    private var mCurX = 0f
    private var mCurY = 0f

    private var mPivotX = 0f
    private var mPivotY = 0f

    private var mPointerCount = 0
    private var mTransX = 0f
    private var mTransY = 0f

    private var mCenterX = 0f
    private var mCenterY = 0f


    private var multiTouchTriggered = false
    var multiTouchEnded = false
    private var mLastTimeClick: Long = 0

    override fun onSizeChanged(w : Int, h : Int, oldw : Int, oldh : Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mCenterX = w.toFloat()/2
        mCenterY = h.toFloat()/2
    }

    override fun drawChild(canvas : Canvas, child : View, drawingTime : Long) : Boolean {
        child.translationX = mTransX
        child.translationY = mTransY
        child.scaleX = mScale
        child.scaleY = mScale
        child.invalidate()
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun onInterceptTouchEvent(ev : MotionEvent) : Boolean {
        //double Tap
        if (ev.action and ev.actionMasked == MotionEvent.ACTION_DOWN && ev.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
            if (System.currentTimeMillis() - mLastTimeClick < 300) {
                mScale = 1f
                mTransX = 0f
                mTransY = 0f
                invalidate()
                return true
            }
            mLastTimeClick = System.currentTimeMillis()
        }

        return if (ev.pointerCount == 2 && ev.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
            //Toast.makeText(context, "Double down outside", Toast.LENGTH_SHORT).show()
            true
        } else {
            multiTouchTriggered = false
            false
        }
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {
        this.requestFocus()
        super.onTouchEvent(event)
        //if Pressed outside child View, here we end up after child ontouchview
        //TODO when pen enters, multitouchEnded stays false
        mPointerCount = event.pointerCount
        if (mPointerCount  <= 1 || event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            return false
        }
        mCurX = event.getX(0)
        mCurY = event.getY(0)
        mPivotX = (mCurX + event.getX(1))/2
        mPivotY = (mCurY + event.getX(1))/2

        if (!lockZoom) {
            mScaleDetector.onTouchEvent(event)
        }

        when (event.action and event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (multiTouchTriggered) {
                    val dx = pivotX - (mCenterX + mTransX)
                    val dy = pivotY - (mCenterY + mTransY)
                    mTransX += (mCurX - mPrevX) - dx*(mScaleFactor - 1)
                    mTransY += (mCurY - mPrevY) - dy*(mScaleFactor - 1)
                }
                multiTouchTriggered = true
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                multiTouchTriggered = false
                multiTouchEnded = true
            }
        }

        multiTouchTriggered = true
        mPrevX = mCurX
        mPrevY = mCurY
        invalidate()
        return true
    }



    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector : ScaleGestureDetector) : Boolean {
            //mPivotX = detector.focusX
            //mPivotY = detector.focusY
            mScaleFactor = detector.scaleFactor
            mScaleFactor = (minZoom / mScale).coerceAtLeast(mScaleFactor.coerceAtMost(maxZoom / mScale))

            mScale *= mScaleFactor
            // Don't let the object get too small or too large.
            mScale = minZoom.coerceAtLeast(mScale.coerceAtMost(maxZoom))
            invalidate()
            return true
        }

    }

    //val mDens = context.resources.displayMetrics.density
}