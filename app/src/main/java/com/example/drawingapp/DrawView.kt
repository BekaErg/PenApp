package com.example.drawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.transform
import androidx.core.view.doOnLayout
import kotlin.math.pow

class DrawView(context : Context, attrs : AttributeSet? = null) : View(context, attrs) {
    var selectedTool = TOOL_PEN
    var strokeSize = 0f
    var toolBackUp = selectedTool
    var brushColor = Color.BLACK
    var opacity = 1f
    var penMode = true
    var smoothingLevel = 0
    var minPressure = 0

    private var mCanvas = Canvas()
    private var mFrameCanvas = Canvas()
    lateinit var mBitmap : Bitmap
    lateinit var mFrameBitmap : Bitmap

    private var drawingStarted = false
    private var mCurTouchX = 0f
    private var mCurTouchY = 0f
    private var mCurStrokeRadius = 0f

    private lateinit var container : ZoomViewGroup

    private var frameRectF = RectF()
    private var frameMatrix = Matrix()

    private val mEraserRadius = 25f
    private var mEraser = Path()
    private var mErasedIndices = arrayListOf<Int>()
    private var mPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var mEraserPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 4f
        style = Paint.Style.STROKE
        brushColor = Color.BLACK
        color = Color.BLACK
    }
    private val mClearPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var mBrushPath = BrushPath()
    private var mRegion = Region()

    //Arrays
    private val mStrokeHistory = arrayListOf<DrawingParameters>()
    private val mStrokeFuture = arrayListOf<DrawingParameters>()

    init {
        this.isFocusableInTouchMode = true
        this.doOnLayout {
            mFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            mFrameCanvas = Canvas(mFrameBitmap)
            mBitmap = Bitmap.createBitmap(2 * this.width, 2 * this.height, Bitmap.Config.ARGB_8888)
            mCanvas = Canvas(mBitmap)
            frameRectF.set(0f, 0f, width.toFloat(), height.toFloat())
            container = this.parent as ZoomViewGroup
        }
    }

    override fun onDraw(canvas : Canvas) {
        super.onDraw(canvas)
        if (container.multiTouchEnded) {
            container.multiTouchEnded = false
            redrawEverything()
        }

        //Alternative to all this is clipOutRectangle, only working from API 26
        canvas.save()
        canvas.clipRect(0f, 0f, frameRectF.left, frameRectF.bottom)
        canvas.drawBitmap(mBitmap, 0f, 0f, null)
        canvas.restore()

        canvas.save()
        canvas.clipRect(frameRectF.left, 0f, this.width.toFloat(), frameRectF.top)
        canvas.drawBitmap(mBitmap, 0f, 0f, null)
        canvas.restore()

        canvas.save()
        canvas.clipRect(frameRectF.right, frameRectF.top, this.width.toFloat(), this.height.toFloat())
        canvas.drawBitmap(mBitmap, 0f, 0f, null)
        canvas.restore()

        canvas.save()
        canvas.clipRect(0f, frameRectF.bottom, frameRectF.right, this.height.toFloat())
        canvas.drawBitmap(mBitmap, 0f, 0f, null)
        canvas.restore()

        //draw frameCanvas
        canvas.drawBitmap(mFrameBitmap, null, frameRectF, null)
        mBrushPath.draw(canvas, mPaint)

        //draw Eraser
        mEraserPaint.strokeWidth = 4f / scaleX
        canvas.drawPath(mEraser, mEraserPaint)
    }

    override fun onGenericMotionEvent(event : MotionEvent) : Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
            if (drawingStarted) {
                cancelStroke()
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {
        if (event.pointerCount > 1 && drawingStarted) {
            cancelStroke()
            return true
        }
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
            else -> throw Error("nonexistent tool value")
        }
    }

    private fun drawLine(event : MotionEvent) : Boolean {
        //TODO add Lock pressure mode
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startStroke(null)
            }
            MotionEvent.ACTION_MOVE -> {
                mBrushPath.restart()
                mBrushPath.lineTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)
            }
            MotionEvent.ACTION_UP -> {
                finishStroke()
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun drawWithPen(event : MotionEvent) : Boolean {
        if (penMode && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startStroke(event.getPressure(0))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) {
                    mCurTouchX = event.getHistoricalX(i)
                    mCurTouchY = event.getHistoricalY(i)
                    applyPressure(event.getHistoricalPressure(i))
                    mBrushPath.lineTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)
                }
            }
            MotionEvent.ACTION_UP -> {
                finishStroke(smoothing = smoothingLevel)
            }
            MotionEvent.ACTION_CANCEL -> {
                if (drawingStarted) {
                    cancelStroke()
                }
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun cancelStroke() {
        Log.i("gela", "stroke was cancelled")
        if (drawingStarted) {
            drawingStarted = false
        } else {
            return
        }
        when (selectedTool) {
            TOOL_PEN, TOOL_LINE -> {
                mStrokeHistory.removeLast()
                mBrushPath.rewind()
                //Toast.makeText(context, "stroke has been cancelled", Toast.LENGTH_SHORT).show()
            }
            TOOL_ERASER -> {
                if (drawingStarted) {
                    for (i in mErasedIndices) {
                        mStrokeHistory[i].isErased = false
                    }
                }
                mEraser.rewind()
            }
            else -> throw Error("nonexistent tool value")
        }
        invalidate()
    }

    private fun startStroke(pressure : Float? = null) {
        updatePaint(DrawingParameters())
        if (pressure != null) {
            applyPressure(pressure)
        } else {
            mCurStrokeRadius = strokeSize / 2
        }
        mStrokeFuture.clear()
        mStrokeHistory.add(DrawingParameters())
        drawingStarted = true
        mBrushPath.moveTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)
    }

    private fun finishStroke(smoothing: Int = 0) {
        mBrushPath.draw(mCanvas, mPaint)
        mBrushPath.quadSmooth(smoothing)

        mFrameCanvas.save()
        mFrameCanvas.setMatrix(matrix)
        mBrushPath.draw(mFrameCanvas, mPaint)
        mFrameCanvas.restore()

        mBrushPath = BrushPath()
        drawingStarted = false
    }

    //TODO Change back to pen when event.action == Cancel
    private fun erase(event : MotionEvent) : Boolean {
        when (event.action and event.actionMasked) {
            MotionEvent.ACTION_DOWN, 211 -> {
                Log.i("gela","action down: $selectedTool")
                mEraser.addCircle(mCurTouchX, mCurTouchY, mEraserRadius, Path.Direction.CW)
                eraserHelper()
            }
            MotionEvent.ACTION_MOVE, 213 -> {
                mEraser.rewind()
                mEraser.addCircle(mCurTouchX, mCurTouchY, mEraserRadius / scaleX, Path.Direction.CW)
                eraserHelper()
            }
            MotionEvent.ACTION_UP, 212, MotionEvent.ACTION_CANCEL -> {
                Log.i("gela","action up: ${event.action} ${mStrokeHistory.size}")
                if (mErasedIndices.isNotEmpty()) {
                    mStrokeHistory.add(DrawingParameters())
                    mErasedIndices = arrayListOf()
                }
                mEraser.rewind()
                if (event.action == 212) {
                    selectedTool = toolBackUp
                }
                drawingStarted = false
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun eraserHelper() {
        //Could be changed to circle for more accuracy
        val clip = Region(
            (mCurTouchX - mEraserRadius / scaleX).toInt(),
            (mCurTouchY - mEraserRadius / scaleX).toInt(),
            (mCurTouchX + mEraserRadius / scaleX).toInt(),
            (mCurTouchY + mEraserRadius / scaleX).toInt()
        )
        val n = mStrokeHistory.size
        for (i in 0 until n) {
            val parameters = mStrokeHistory[i]
            if (parameters.toolType == TOOL_ERASER || parameters.isErased) continue
            mRegion.setEmpty()
            mRegion.setPath(parameters.dynPath.contourPath, clip)
            if (!mRegion.isEmpty) {
                drawingStarted = true
                parameters.isErased = true
                mErasedIndices.add(i)
                redrawEverything()
                invalidate()
                //mStrokeHistory.add(DrawingParameters())
            }
        }
    }

    private fun applyPressure(pressure : Float) {
        mCurStrokeRadius = minPressure * strokeSize + (0.5f - minPressure) * strokeSize * (1f - (1f - pressure).pow(2))
        //mCurStrokeRadius = mCurStrokeRadius.coerceAtLeast(0.5f)
    }

    private fun updatePaint(parameters : DrawingParameters) {
        mPaint.color = parameters.color
        //mPaint.strokeWidth = parameters.brushSize
        mPaint.strokeWidth = 0.2f
        mPaint.alpha = (parameters.alpha * 255).toInt()
    }

    private fun redrawEverything() {
        val pathMatrix = Matrix()
        //pathMatrix.setScale(2f, 0.5f, width.toFloat() / 2, height.toFloat() / 2)
        pathMatrix.setRotate(45f, width.toFloat() / 2, height.toFloat() / 2)

        Log.i("gela", "refreshed ${container.multiTouchEnded}")
        //draw on current Portion of the frame
        mFrameCanvas.drawPaint(mClearPaint)
        mFrameCanvas.save()
        mFrameCanvas.setMatrix(matrix)
        for (parameters in mStrokeHistory) {
            if (parameters.isErased || parameters.toolType == TOOL_ERASER) {
                continue
            }
            updatePaint(parameters)
            //parameters.dynPath.transform(pathMatrix)
            parameters.dynPath.draw(mFrameCanvas, mPaint)
        }
        mFrameCanvas.restore()

        //draw on a whole bitmap
        mCanvas.drawPaint(mClearPaint)
        for (parameters in mStrokeHistory) {
            if (parameters.isErased || parameters.toolType == TOOL_ERASER) {
                continue
            }
            updatePaint(parameters)
            parameters.dynPath.draw(mCanvas, mPaint)
        }
        //Prepare frame for drawing
        frameRectF.set(0f, 0f, width.toFloat(), height.toFloat())
        frameMatrix = matrix
        frameMatrix.invert(matrix)
        frameRectF.transform(frameMatrix)
    }

    fun getHistory(): MutableList<DrawingParameters> {
        val arr = mutableListOf<DrawingParameters>()
        //TODO change this to iterators
        for (params in mStrokeHistory) {
            if (params.toolType != TOOL_ERASER && !params.isErased) {
                arr.add(params)
            }
        }
        return arr
    }

    fun setHistory(arr: MutableList<DrawingParameters>?) {
        mStrokeHistory.clear()
        if (arr == null) return
        for (params in mStrokeHistory) {
            mStrokeHistory.add(params)
        }
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
        redrawEverything()
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
        redrawEverything()
        invalidate()
    }

    fun clearCanvas() {
        mStrokeHistory.clear()
        redrawEverything()
        invalidate()
    }

    fun getBitmap() : Bitmap {
        val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap!!)
        this.draw(canvas)
        return bitmap
    }

    inner class DrawingParameters {
        val color = brushColor
        val alpha = opacity
        val toolType = selectedTool
        val dynPath = mBrushPath
        val eraserIdx = mErasedIndices
        var isErased = false
    }

    companion object {
        const val TOOL_PEN = 0
        const val TOOL_LINE = 1
        const val TOOL_ERASER = 2
    }
}
