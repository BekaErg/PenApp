package com.example.drawingapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.transform
import androidx.core.view.doOnLayout
import kotlin.math.sqrt

class DrawView(context : Context, attrs : AttributeSet? = null) : View(context, attrs) {
    var selectedTool = ToolType.BRUSH
        set(value) {
            field = value
            setPenPreset(value)
        }
    var strokeSize = 0f
    private var toolBackUp = selectedTool
    var brushColor = Color.BLACK
    var opacity = 1f
    var penMode = true
    private var penPathSettings = BrushSettings()
    var fillType = Paint.Style.FILL
        set(value) {
            mPaint.style = value
            field = value
            redrawEverything(true)
        }
    var drawingEngine = DrawingEngine.LAST_SEGMENT


    private var mGlobalCanvas = Canvas()
    private var mFrameCanvas = Canvas()
    private lateinit var mGlobalBitmap : Bitmap
    private lateinit var mFrameBitmap : Bitmap
    private lateinit var mCurStrokeBimap : Bitmap
    private var mCurStrokeCanvas = Canvas()
    private var mBoundaryRect = RectF()

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
        style = fillType
        strokeWidth = 0.5f
        //shader = BitmapShader(BitmapFactory.decodeResource(resources, R.drawable.brush200).scale(2, 2, false), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        //maskFilter = BlurMaskFilter(0.00001f, BlurMaskFilter.Blur.NORMAL)
    }
    private var mEraserPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 4f
        style = Paint.Style.STROKE
        brushColor = Color.BLACK
        color = Color.BLACK
    }
    private var mGhostPaint = Paint().apply{
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeWidth = 0.5f
        //xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        color = 0xDDFFFFFF.toInt()
    }
    private val mClearPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val mBlackPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeWidth = 0.01f
        color = Color.BLACK
        //maskFilter = BlurMaskFilter(0.00001f, BlurMaskFilter.Blur.NORMAL)
    }

    private var mPenPath = PenPath(PenPath.Type.JOIN_WITH_TANGENTS)
    private var mRegion = Region()

    //Arrays
    private val mStrokeHistory = arrayListOf<DrawingParameters>()
    private val mStrokeFuture = arrayListOf<DrawingParameters>()

    init {
        this.isFocusableInTouchMode = true
        this.doOnLayout {
            mCurStrokeBimap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
            mCurStrokeCanvas = Canvas(mCurStrokeBimap)
            mFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            mFrameCanvas = Canvas(mFrameBitmap)
            mGlobalBitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
            mGlobalCanvas = Canvas(mGlobalBitmap)

            mFrameBitmap.prepareToDraw()
            mGlobalBitmap.prepareToDraw()

            frameRectF.set(0f, 0f, width.toFloat(), height.toFloat())
            container = this.parent as ZoomViewGroup
            setPenPreset(ToolType.BRUSH)
        }
    }

    override fun onDraw(canvas : Canvas) {
        super.onDraw(canvas)
        if (container.multiTouchEnded) {
            container.multiTouchEnded = false
            redrawEverything(false)
        }

        if (container.multiTouchTriggered) {
            canvas.save()
            canvas.clipRect(0f, 0f, frameRectF.left, frameRectF.bottom)
            canvas.drawBitmap(mGlobalBitmap, 0f, 0f, null)
            canvas.restore()

            canvas.save()
            canvas.clipRect(frameRectF.left, 0f, this.width.toFloat(), frameRectF.top)
            canvas.drawBitmap(mGlobalBitmap, 0f, 0f, null)
            canvas.restore()

            canvas.save()
            canvas.clipRect(frameRectF.right, frameRectF.top, this.width.toFloat(), this.height.toFloat())
            canvas.drawBitmap(mGlobalBitmap, 0f, 0f, null)
            canvas.restore()

            canvas.save()
            canvas.clipRect(0f, frameRectF.bottom, frameRectF.right, this.height.toFloat())
            canvas.drawBitmap(mGlobalBitmap, 0f, 0f, null)
            canvas.restore()

            canvas.drawBitmap(mFrameBitmap, null, frameRectF, null)
        } else {
            canvas.drawBitmap(mFrameBitmap, null, frameRectF, null)
            if (selectedTool == ToolType.LINE || drawingEngine == DrawingEngine.PENPATH_DRAW) {
                mPenPath.draw(canvas, mPaint)
            } else {
                canvas.drawBitmap(mCurStrokeBimap, null, frameRectF, mPaint)
            }
        }

        /*
        canvas.drawBitmap(mFrameBitmap, null, frameRectF, null)
        if (selectedTool == ToolType.LINE || drawingEngine == DrawingEngine.PENPATH_DRAW) {
            mBrushPath.draw(canvas, mPaint)
        } else {
            canvas.drawBitmap(mCurStrokeBimap, null, frameRectF, mPaint)
        }
         */

        mEraserPaint.strokeWidth = 4f / scaleX
        canvas.drawPath(mEraser, mEraserPaint)

        //draw Eraser
    }

    override fun onGenericMotionEvent(event : MotionEvent) : Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
            if (drawingStarted) {
                cancelStroke()
            }
        }
        return super.onGenericMotionEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event : MotionEvent) : Boolean {
        if (event.pointerCount > 1 && drawingStarted) {
            cancelStroke()
            return true
        }
        mCurTouchX = event.x
        mCurTouchY = event.y
        if (event.action == 211) {
            toolBackUp = selectedTool
            selectedTool = ToolType.TOOL_ERASER
        }

        return when (selectedTool) {
            ToolType.BRUSH, ToolType.PEN_BALL, ToolType.PEN_FOUNTAIN -> drawWithPen(event)
            ToolType.LINE -> drawLine(event)
            ToolType.TOOL_ERASER -> erase(event)
        }
    }

    private fun drawLine(event : MotionEvent) : Boolean {
        //TODO add Lock pressure mode
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startStroke(null)
            }
            MotionEvent.ACTION_MOVE -> {
                mPenPath.restart()
                mPenPath.lineTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)
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
        mCurStrokeCanvas.save()
        mCurStrokeCanvas.setMatrix(matrix)

        if (penMode && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startStroke(event.getPressure(0))
                mPenPath.drawLastSegment(mCurStrokeCanvas, mBlackPaint)
            }
            MotionEvent.ACTION_MOVE -> {

                for (i in 0 until event.historySize) {
                    mCurTouchX = event.getHistoricalX(i)
                    mCurTouchY = event.getHistoricalY(i)
                    applyPressure(event.getHistoricalPressure(i))
                    mPenPath.lineTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)

                    mPenPath.drawLastSegment(mCurStrokeCanvas, mBlackPaint)
                }
            }
            MotionEvent.ACTION_UP -> {
                applyPressure(event.getPressure(0))
                mPenPath.lineTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)
                finishStroke()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (drawingStarted) {
                    cancelStroke()
                }
            }
            else -> {
                mCurStrokeCanvas.restore()
                return false
            }
        }

        mCurStrokeCanvas.restore()
        invalidate()
        return true
    }


    private fun startStroke(pressure : Float? = null) {
        updatePaint(DrawingParameters())
        if (pressure != null) {
            applyPressure(pressure)
        } else {
            mCurStrokeRadius = strokeSize / 2
        }
        mStrokeFuture.clear()
        //mStrokeHistory.add(DrawingParameters())
        mStrokeHistory.add(DrawingParameters())
        drawingStarted = true
        penPathSettings.setPathSettings(mPenPath)
        mPenPath.moveTo(mCurTouchX, mCurTouchY, mCurStrokeRadius)
    }

    private fun finishStroke() {
        mPenPath.draw(mGlobalCanvas, mPaint)
        mPenPath.finish()

        mCurStrokeCanvas.drawPaint(mClearPaint)

        mFrameCanvas.save()
        mFrameCanvas.setMatrix(matrix)
        mPenPath.draw(mFrameCanvas, mPaint)
        mFrameCanvas.restore()

        mPenPath = PenPath()
        drawingStarted = false
    }

    private fun cancelStroke() {
        if (drawingStarted) {
            mCurStrokeCanvas.drawPaint(mClearPaint)
            drawingStarted = false
        } else {
            return
        }
        when (selectedTool) {
            ToolType.PEN_BALL,ToolType.PEN_FOUNTAIN, ToolType.BRUSH, ToolType.LINE -> {
                mStrokeHistory.removeLast()
                mPenPath.rewind()
            }
            ToolType.TOOL_ERASER -> {
                if (drawingStarted) {
                    for (i in mErasedIndices) {
                        mStrokeHistory[i].isErased = false
                    }
                }
                mEraser.rewind()
            }
        }
        invalidate()
    }

    private fun erase(event : MotionEvent) : Boolean {
        when (event.action and event.actionMasked) {
            MotionEvent.ACTION_DOWN, 211 -> {
                mBoundaryRect = RectF(0f, 0f, 0f, 0f)
                mEraser.addCircle(mCurTouchX, mCurTouchY, mEraserRadius, Path.Direction.CW)
                eraserHelper()
            }
            MotionEvent.ACTION_MOVE, 213 -> {
                mEraser.rewind()
                mEraser.addCircle(mCurTouchX, mCurTouchY, mEraserRadius / scaleX, Path.Direction.CW)
                eraserHelper()
            }
            MotionEvent.ACTION_UP, 212, MotionEvent.ACTION_CANCEL -> {
                if (mErasedIndices.isNotEmpty()) {
                    mStrokeHistory.add(DrawingParameters())
                    mErasedIndices = arrayListOf()
                }
                mEraser.rewind()
                if (event.action == 212) {
                    selectedTool = toolBackUp
                }
                drawingStarted = false
                if (mBoundaryRect != RectF(0f, 0f, 0f, 0f)) {
                    redrawEverything(true)
                }
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun eraserHelper() {
        //Could be changed to circle for more accuracy
        mFrameCanvas.save()
        mFrameCanvas.setMatrix(matrix)
        val clip = Region(
            (mCurTouchX - mEraserRadius / scaleX).toInt(),
            (mCurTouchY - mEraserRadius / scaleX).toInt(),
            (mCurTouchX + mEraserRadius / scaleX).toInt(),
            (mCurTouchY + mEraserRadius / scaleX).toInt()
        )
        val n = mStrokeHistory.size
        for (i in 0 until n) {
            val parameters = mStrokeHistory[i]
            if (parameters.toolType == ToolType.TOOL_ERASER || parameters.isErased) continue
            mRegion.setEmpty()
            mRegion.setPath(parameters.penPath.contourPath, clip)
            if (!mRegion.isEmpty) {
                drawingStarted = true
                parameters.isErased = true
                mErasedIndices.add(i)

                rectUnion(mBoundaryRect, parameters.penPath.boundaryRectF)
                parameters.penPath.draw(mFrameCanvas, mGhostPaint)
                //redrawEverything(true)
                invalidate()
                //mStrokeHistory.add(DrawingParameters())
            }
        }
        mFrameCanvas.restore()
    }

    private fun applyPressure(pressure : Float) {
        /*
        mCurStrokeRadius = 0.5f * strokeSize * (
                penPathSettings.minPressure   +  (1f - penPathSettings.minPressure) * (1f - (1f - pressure).pow(2))
                )

         */
        mCurStrokeRadius =  (penPathSettings.minPressure   +  (1f - penPathSettings.minPressure) * pressure) *
                strokeSize *
                when (selectedTool) {
                    ToolType.BRUSH -> 1f
                    ToolType.PEN_BALL -> 0.6f
                    ToolType.PEN_FOUNTAIN -> 0.5f
                    else -> 1f
                }
    }

    private fun updatePaint(parameters : DrawingParameters) {
        mPaint.color = parameters.color
        //mPaint.strokeWidth = parameters.brushSize
        mPaint.strokeWidth = 0.1f
        mPaint.alpha = (parameters.alpha * 255).toInt()
    }


    private fun redrawEverything(updateGlobal: Boolean) {
        //After Erase: updateGlobal = true and Crop
        //After zooming: updateGlobal = false and no Crop
        //After Redo, undo: updateGlobal = true and no Crop
        mFrameCanvas.save()
        mGlobalCanvas.save()
        mFrameCanvas.setMatrix(matrix)
        if (mBoundaryRect != RectF(0f, 0f, 0f, 0f)) {
            mFrameCanvas.clipRect(mBoundaryRect)
            mGlobalCanvas.clipRect(mBoundaryRect)
        }
        mFrameCanvas.drawPaint(mClearPaint)
        if (updateGlobal) {
            mGlobalCanvas.drawPaint(mClearPaint)
        }

        for (parameters in mStrokeHistory) {
            if (parameters.isErased || parameters.toolType == ToolType.TOOL_ERASER) {
                continue
            }
            updatePaint(parameters)
            parameters.penPath.draw(mFrameCanvas, mPaint)
            if (updateGlobal) {
                parameters.penPath.draw(mGlobalCanvas, mPaint)
            }
        }
        mFrameCanvas.restore()
        mGlobalCanvas.restore()
        mBoundaryRect = RectF(0f, 0f, 0f, 0f)

        //Prepare frame for drawing
        frameRectF.set(0f, 0f, width.toFloat(), height.toFloat())
        frameMatrix = matrix
        frameMatrix.invert(matrix)
        frameRectF.transform(frameMatrix)
    }

    fun undo() {
        if (mStrokeHistory.isEmpty()) return
        val params = mStrokeHistory.removeLast()
        assert(!params.isErased)
        if (params.toolType == ToolType.TOOL_ERASER) {
            for (i in params.eraserIdx) {
                mStrokeHistory[i].isErased = false
            }
        }
        mStrokeFuture.add(params)
        redrawEverything(true)
        invalidate()
    }

    fun redo() {
        if (mStrokeFuture.isEmpty()) return
        val params = mStrokeFuture.removeLast()
        mStrokeHistory.add(params)
        if (params.toolType == ToolType.TOOL_ERASER) {
            for (i in params.eraserIdx) {
                mStrokeHistory[i].isErased = true
            }
        }
        redrawEverything(true)
        invalidate()
    }

    fun clearCanvas() {
        mStrokeHistory.clear()
        redrawEverything(true)
        invalidate()
    }

    fun getBitmap() : Bitmap {
        val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap!!)
        this.draw(canvas)
        return bitmap
    }

    private fun rectUnion(source: RectF, other: RectF) {
        if (source == RectF(0f, 0f, 0f, 0f)) {
            source.left = other.left
            source.top = other.top
            source.right = other.right
            source.bottom = other.bottom
            return
        }
        source.bottom = source.bottom.coerceAtLeast(other.bottom)
        source.top = source.top.coerceAtMost(other.top)
        source.right = source.right.coerceAtLeast(other.right)
        source.left = source.left.coerceAtMost(other.left)
    }

    inner class DrawingParameters {
        val color = brushColor
        val alpha = opacity
        val toolType = selectedTool
        val penPath = mPenPath
        val eraserIdx = mErasedIndices
        var isErased = false
    }

    enum class DrawingEngine {
        LAST_SEGMENT,
        PENPATH_DRAW,
    }


    private fun setPenPreset(preset: ToolType) {
        when (preset) {
            ToolType.BRUSH -> {
                penPathSettings.minPressure = 0f
                penPathSettings.directionBiasLevel = 0f
                penPathSettings.bufferSize = 4
                penPathSettings.penPathType = PenPath.Type.CIRCLE_SEQUENCE
            }
            ToolType.PEN_FOUNTAIN -> {
                penPathSettings.minPressure = 0.7f
                penPathSettings.directionBiasLevel = 0.7f
                penPathSettings.directionBiasVector = Pair(1f, -1f)
                penPathSettings.bufferSize = 5
                penPathSettings.penPathType = PenPath.Type.CIRCLE_SEQUENCE
            }
            ToolType.PEN_BALL -> {
                penPathSettings.minPressure = 0.25f
                penPathSettings.directionBiasLevel = 0.4f
                penPathSettings.directionBiasVector = Pair(0f, 1f)
                penPathSettings.bufferSize = 3
                penPathSettings.penPathType = PenPath.Type.CIRCLE_SEQUENCE
            }
            ToolType.LINE -> {
                penPathSettings.directionBiasLevel = 0f

            }
            ToolType.TOOL_ERASER -> {

            }
        }
        penPathSettings.setPathSettings(mPenPath)
    }

    inner class BrushSettings {
        var minGapFactor = 0.1f
        var directionBiasVector = Pair(1f / sqrt(2f), -1f / sqrt(2f))
        var directionBiasLevel = 1f
        var minPressure = 0.01f
            set(value) {
                field = value.coerceAtLeast(0f).coerceAtMost(0.5f)
            }
        var bufferSize = 1
        var penPathType = PenPath.Type.CIRCLE_SEQUENCE

        fun setPathSettings(penPath: PenPath) {
            penPath.minGapFactor = minGapFactor
            penPath.directionBiasVector = directionBiasVector
            penPath.directionBiasLevel = directionBiasLevel
            penPath.inputBufferSize = bufferSize
            penPath.contourType = penPathType
        }
    }

}
