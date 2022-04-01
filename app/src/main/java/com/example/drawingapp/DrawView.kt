package com.example.drawingapp

import android.content.Context
import android.graphics.*
import android.graphics.drawable.PictureDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.widget.Toast
import kotlin.math.pow
import kotlin.math.sqrt

class DrawView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var selectedTool = TOOL_PEN
    var strokeSize = 40f
    var toolBackUp = selectedTool
    var brushColor = Color.BLACK
    var opacity = 1f

    var penMode = true
    /*
    val op = opacity.coerceAtMost(1f).coerceAtLeast(0f)
    val mask = ((255 * op).toInt() shl 24) + 0x00FFFFFF
    brushColor = brushColor and mask
     */

    private var pictureDrawable =PictureDrawable(Picture())
    private var picture = Picture()
    private var finalPicture = Picture()

    private var drawingStarted = false
    private var mCurTouchX = 0f
    private var mCurTouchY = 0f
    private var mCurStrokeRadius = 0f


    private var mDynPath = DynPath()

    private val mEraserRadius = 25f
    private var mEraser = Path()
    private var mErasedIndices = arrayListOf<Int>()
    private var mPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var mEraserPaint = Paint().apply{
        isAntiAlias = true
        strokeWidth = 4f
        style = Paint.Style.STROKE
        brushColor = Color.BLACK
        color = Color.BLACK
    }

    private var mPath = Path().apply{fillType = Path.FillType.WINDING}
    private var mTempPath = Path().apply{}

    private var mRegion = Region()

    //Arrays
    private val mStrokeHistory = arrayListOf<DrawingParameters>()
    private val mStrokeFuture = arrayListOf<DrawingParameters>()

    init {
        this.isFocusableInTouchMode = true
    }


    override fun onDraw(canvas : Canvas) {
        super.onDraw(canvas)

        for (parameters in mStrokeHistory) {
            if (parameters.isErased || parameters.toolType == TOOL_ERASER) {continue}
            updatePaint(parameters)
            //parameters.dynPath.drawPicture(canvas)
            parameters.dynPath.draw(canvas, mPaint)
            //parameters.dynPath.drawSpine(canvas, mPaint)
        }
        //canvas.drawPath(mEraser, mEraserPaint)
        //canvas.restore()
    }

    private fun cancelStroke() {
        if (drawingStarted) {
            drawingStarted = false
        } else {
            return
        }
        when (selectedTool) {
            TOOL_PEN, TOOL_LINE -> {
                mStrokeHistory.removeLast()
                mDynPath.rewind()
                Toast.makeText(context, "stroke has been cancelled", Toast.LENGTH_SHORT).show()
            }
            TOOL_ERASER -> {
                if (mErasedIndices.isNotEmpty()) {
                    for (i in mErasedIndices) {
                        mStrokeHistory[i].isErased = false
                    }
                }
                mEraser.rewind()
            }
            else -> throw Error ("nonexistent tool value")
        }
        invalidate()
    }

    override fun onGenericMotionEvent(event : MotionEvent) : Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER && drawingStarted) {
            cancelStroke()
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {
        this.requestFocus()

        if (event.pointerCount > 1 && drawingStarted) {
            cancelStroke()
            return true
        }
        //mScaleDetector.onTouchEvent(event);
        mCurTouchX = event.x
        mCurTouchY = event.y

        if (event.action == 211) {
            toolBackUp = selectedTool
            selectedTool = TOOL_ERASER
        }

        return when (selectedTool) {
            TOOL_PEN -> drawWithPen(event)
            TOOL_LINE -> drawLine(event)
            TOOL_ERASER -> erase(event)
            else -> throw Error ("nonexistent tool value")
        }
    }

    private fun drawLine(event : MotionEvent): Boolean {
        //TODO add Lock pressure mode
        when (event.action) {
            MotionEvent.ACTION_DOWN-> {
                mStrokeFuture.clear()
                mStrokeHistory.add(DrawingParameters())
                drawingStarted = true
                mCurStrokeRadius = strokeSize / 2
                mDynPath.moveTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)
            }
            MotionEvent.ACTION_MOVE -> {
                mDynPath.restart()
                applyPressure(event.getPressure(0))
                mDynPath.lineTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)
            }
            MotionEvent.ACTION_UP -> {
                mDynPath.updateContour()
                mDynPath = DynPath()
                drawingStarted = false
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun drawWithPen(event : MotionEvent): Boolean {
        if (penMode && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
            //Toast.makeText(context,"finger is turned off ", Toast.LENGTH_SHORT).show()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN-> {
                mStrokeFuture.clear()
                mStrokeHistory.add(DrawingParameters())
                drawingStarted = true
                applyPressure(event.getPressure(0))
                mDynPath.moveTo(mCurTouchX,mCurTouchY, mCurStrokeRadius)
                //At this stage we only need to apply pressure to store in the mPrevPressure
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) {
                    mCurTouchX = event.getHistoricalX(i)
                    mCurTouchY = event.getHistoricalY(i)
                    applyPressure(event.getHistoricalPressure(i))

                    mDynPath.lineTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)
                    //TODO gap is too small
                }
            }
            MotionEvent.ACTION_HOVER_ENTER -> {
                Toast.makeText(context,"hover Entered", Toast.LENGTH_SHORT).show()
                if (drawingStarted) {
                    cancelStroke()
                }
            }

            MotionEvent.ACTION_UP -> {
                //mDynPath.quadSmooth(1)
                mDynPath.updateContour()
                mDynPath = DynPath()
                drawingStarted = false
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun erase(event : MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, 211 -> {
                mEraser.addCircle(mCurTouchX, mCurTouchY, mEraserRadius, Path.Direction.CW)
                drawingStarted = true
                eraserHelper()
            }

            MotionEvent.ACTION_MOVE, 213 -> {
                //Check past paths if they are close to the eraser
                mEraser.rewind()
                mEraser.addCircle(mCurTouchX, mCurTouchY, mEraserRadius, Path.Direction.CW)
                eraserHelper()
            }

            MotionEvent.ACTION_UP, 212 -> {
                if (mErasedIndices.isNotEmpty()) {
                    mStrokeHistory.add(DrawingParameters())
                    mErasedIndices = arrayListOf()
                }
                mEraser.rewind()
                if (event.action == 212) {
                    selectedTool = toolBackUp
                }
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun eraserHelper() {
        val clip = Region((mCurTouchX - mEraserRadius).toInt(),(mCurTouchY - mEraserRadius).toInt(),(mCurTouchX + mEraserRadius).toInt(), (mCurTouchY + mEraserRadius).toInt())
        val n = mStrokeHistory.size
        for (i in 0 until n) {
            val parameters = mStrokeHistory[i]
            if (parameters.toolType == TOOL_ERASER || parameters.isErased) continue
            mRegion.setEmpty()
            mRegion.setPath(parameters.dynPath.contourPath, clip)
            if (!mRegion.isEmpty) {
                parameters.isErased = true
                mErasedIndices.add(i)
                //mStrokeHistory.add(DrawingParameters())
            }
        }
    }

    private fun applyPressure(pressure: Float) {
        //mCurStrokeRadius = 0.5f * strokeSize * (1f - (1f - pressure).pow(2))
        mCurStrokeRadius = 0.000f * strokeSize + 0.500f * strokeSize * (1f - (1f - pressure).pow(2))
    }

    private fun updatePaint(parameters: DrawingParameters) {
        mPaint.color = parameters.color
        //mPaint.strokeWidth = parameters.brushSize
        mPaint.strokeWidth = 0.2f
        mPaint.alpha = (parameters.alpha * 255).toInt()
    }

    fun undo() {
        if (mStrokeHistory.isEmpty()) return
        val params = mStrokeHistory.removeLast()

        assert(!params.isErased)
        if (params.toolType == TOOL_ERASER) {
            for (i in params.eraserIdx) {
                mStrokeHistory[i].isErased = false
            }
        }
        mStrokeFuture.add(params)
        invalidate()
    }

    fun redo() {
        if (mStrokeFuture.isEmpty()) return
        val params = mStrokeFuture.removeLast()

        mStrokeHistory.add(params)
        if (params.toolType == TOOL_ERASER) {
            for (i in params.eraserIdx) {
                mStrokeHistory[i].isErased = true
            }
        }
        invalidate()
    }

    fun clearCanvas() {
        mStrokeHistory.clear()
        invalidate()
    }

    fun getBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap!!)
        this.draw(canvas)
        return bitmap
    }

    inner class DrawingParameters() {
        val color = brushColor
        val alpha = opacity
        val toolType = selectedTool
        val dynPath = mDynPath
        val eraserIdx = mErasedIndices
        var isErased = false
    }


    companion object {
        const val TOOL_PEN = 0
        const val TOOL_LINE = 1
        const val TOOL_ERASER = 2
    }
}
