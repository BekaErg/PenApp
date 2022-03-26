package com.example.drawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
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

    private var drawingStarted = false
    private var mPrevTouchX = 0f
    private var mPrevTouchY = 0f
    private var mCurTouchX = 0f
    private var mCurTouchY = 0f
    private var mPrevStrokeRadius = 0f
    private var mCurStrokeRadius = 0f

    private val mEpsilon = 0.1f
    private val mEraserRadius = 25f
    private var mGap = 0f

    private var mEraser = Path()
    private var mErasedIndices = arrayListOf<Int>()
    private var mPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeWidth = 0.5f
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
    private var mTempPath = Path().apply{fillType = Path.FillType.WINDING}

    private var mRegion = Region()

    //Arrays
    private val mStrokeHistory = arrayListOf<DrawingParameters>()
    private val mStrokeFuture = arrayListOf<DrawingParameters>()

    init {
        this.isFocusableInTouchMode = true
    }

    override fun onDraw(canvas : Canvas) {
        super.onDraw(canvas)
        //canvas.save();
        //canvas.scale(mScaleFactor, mScaleFactor);
        //canvas.drawCircle(222f, 222f, 200f, mPaint)
        for (parameters in mStrokeHistory) {
            if (parameters.isErased || parameters.toolType == TOOL_ERASER) {continue}
            updatePaint(parameters)
            canvas.drawPath(parameters.path, mPaint)
        }
        canvas.drawPath(mEraser, mEraserPaint)
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
                mPath = Path().apply{fillType = Path.FillType.WINDING}
                //Toast.makeText(context, "stroke has been cancelled", Toast.LENGTH_SHORT).show()
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
                mPrevStrokeRadius = mCurStrokeRadius
                mPrevTouchX = mCurTouchX
                mPrevTouchY = mCurTouchY
                extendPath()
            }
            MotionEvent.ACTION_MOVE -> {
                mPath.rewind()
                mPath.addCircle(mPrevTouchX, mPrevTouchY, mCurStrokeRadius, Path.Direction.CCW)
                extendPath()
            }
            MotionEvent.ACTION_UP -> {
                mPath = Path().apply{fillType = Path.FillType.WINDING}
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

        when (event.action) {
            MotionEvent.ACTION_DOWN-> {
                mStrokeFuture.clear()
                mStrokeHistory.add(DrawingParameters())
                drawingStarted = true
                mPath.moveTo(mCurTouchX,mCurTouchY)

                //At this stage we only need to apply pressure to store in the mPrevPressure
                applyPressure(event.getPressure(0))
                setPrevVariables()
            }
            MotionEvent.ACTION_MOVE -> {

                for (i in 0 until event.historySize) {
                    mCurTouchX = event.getHistoricalX(i)
                    mCurTouchY = event.getHistoricalY(i)

                    //if cur coordinates id too close to prev, we do not draw at all
                    if (!gapIsTooSmall()) {
                        applyPressure(event.getHistoricalPressure(i))
                        extendPath()
                        setPrevVariables()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                mPath = Path().apply{fillType = Path.FillType.WINDING}
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
            mRegion.setPath(parameters.path, clip)
            if (!mRegion.isEmpty) {
                parameters.isErased = true
                mErasedIndices.add(i)
                //mStrokeHistory.add(DrawingParameters())
            }
        }
    }

    private fun applyPressure(pressure: Float) {
        //mCurStrokeRadius = 0.5f * strokeSize * (1f - (1f - pressure).pow(2))
        mCurStrokeRadius = 0.001f * strokeSize + 0.499f * strokeSize * (1f - (1f - pressure).pow(2))
    }

    private fun updatePaint(parameters: DrawingParameters) {
        mPaint.color = parameters.color
        mPaint.strokeWidth = parameters.brushSize
        mPaint.alpha = (parameters.alpha * 255).toInt()
    }

    private fun setPrevVariables() {
        mPrevTouchX = mCurTouchX
        mPrevTouchY = mCurTouchY
        mPrevStrokeRadius = mCurStrokeRadius
    }

    private fun gapIsTooSmall(): Boolean {
        mGap = mEpsilon * mCurStrokeRadius
        return mCurTouchX - mPrevTouchX < mGap && mCurTouchY - mPrevTouchY < mGap && mPrevTouchX - mCurTouchX < mGap && mPrevTouchY - mCurTouchY < mGap
    }

    private fun extendPath(){
        var normalX = -(mCurTouchY - mPrevTouchY)
        var normalY = (mCurTouchX - mPrevTouchX)
        val norm = sqrt( normalX * normalX + normalY * normalY)
        normalX /= norm
        normalY /= norm

        mPath.moveTo(mPrevTouchX - normalX * mPrevStrokeRadius, mPrevTouchY - normalY * mPrevStrokeRadius)
        mPath.lineTo(mPrevTouchX + normalX * mPrevStrokeRadius, mPrevTouchY + normalY * mPrevStrokeRadius)
        mPath.lineTo(mCurTouchX + normalX * mCurStrokeRadius, mCurTouchY + normalY * mCurStrokeRadius)
        mPath.lineTo(mCurTouchX - normalX * mCurStrokeRadius, mCurTouchY - normalY * mCurStrokeRadius)
        mPath.close()

        //mTempPath.addCircle(mCurTouchX, mCurTouchY, mCurStrokeRadius, Path.Direction.CW)
        //mTempPath.op(p, Path.Op.UNION)
        mPath.addCircle(mCurTouchX, mCurTouchY, mCurStrokeRadius, Path.Direction.CCW)
        mPath.addPath(mTempPath)
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
        val brushSize = strokeSize
        val alpha = opacity
        val toolType = selectedTool
        val path = mPath
        val eraserIdx = mErasedIndices
        var isErased = false
    }

    /*
    private var mScaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var mScaleFactor = 1f
    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScale(detector : ScaleGestureDetector) : Boolean {
            mScaleFactor *= detector.scaleFactor

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f))
            invalidate()
            return true
        }
    }

     */


    companion object {
        const val TOOL_PEN = 0
        const val TOOL_LINE = 1
        const val TOOL_ERASER = 2
    }
}















/*




    private fun lineDrawing(action : Int): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN-> {
                mStrokeFuture.clear()
                mStrokeHistory.add(DrawingParameters())

                mPrevTouchX = mCurTouchX
                mPrevTouchY = mCurTouchY
                mPath.moveTo(mPrevTouchX, mPrevTouchY)
                mPath.lineTo(mCurTouchX, mCurTouchY)
            }
            MotionEvent.ACTION_MOVE -> {
                mPath.rewind()
                mPath.moveTo(mPrevTouchX, mPrevTouchY)
                mPath.lineTo(mCurTouchX, mCurTouchY)
            }
            MotionEvent.ACTION_UP -> {
                mPath = Path()
            }
            else -> return false
        }
        invalidate()
        return true
    }



private fun erasing(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN-> {

                //Clear Redo array and add current parameters to the History array
                mStrokeFuture.clear()
                mStrokeHistory.add(DrawingParameters())

                mPath.moveTo(mCurTouchX, mCurTouchY)
                mTempPath = Path(mPath)

                mTempStrokeSize = strokeSize * event.getPressure(0)
                mPrevTouchX = mCurTouchX
                mPrevTouchY = mCurTouchY
                mPrevDx = 5*mTempStrokeSize
                mPrevDy = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val historySize : Int = event.historySize
                for (i in 0 until historySize) {
                    mTempStrokeSize = strokeSize * event.getHistoricalPressure(0, i)
                    mCurTouchX = event.getHistoricalX(i)
                    mCurTouchY = event.getHistoricalY(i)

                    if (gapIsTooSmall()) continue
                    //calculate cur dx and dy
                    mdx = 5*mTempStrokeSize
                    mdy = 0f

                    //Build TempPath Loop
                    newChunkOfPath()

                    //Add TempPath to mPath and clear it
                    mPath.addPath(mTempPath)
                    //mPath.op(mTempPath, Path.Op.UNION)
                    mTempPath.rewind()

                    //Replace prevVariables with current
                    setPrevVariables()
                }

            }
            MotionEvent.ACTION_UP -> {
                mTempStrokeSize = strokeSize * event.getPressure(0)
                mdx = mTempStrokeSize
                mdy = 0f
                newChunkOfPath()
                //mPath.op(mTempPath, Path.Op.UNION)
                mPath = Path().apply{fillType = Path.FillType.WINDING}
            }
            else -> return false
        }
        invalidate()
        return true

    }



        fun erase() {
            startX = null
            startY = null
            endX = null
            endY = null
        }

private lateinit var mCanvasBitmap: Bitmap
private var canvas: Canvas? = null
override fun onSizeChanged(w : Int, h : Int, oldw : Int, oldh : Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    mCanvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    canvas = Canvas(mCanvasBitmap!!)
}


override fun onSizeChanged(w: Int, h: Int, wprev: Int, hprev: Int) {
        super.onSizeChanged(w, h, wprev, hprev)
        val mCanvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(mCanvasBitmap!!)

        mRect2.left = 700f - mBrushSize
        mRect2.right = 700f + mBrushSize
        mRect2.bottom = 900f + mBrushSize
        mRect2.top = 900f - mBrushSize
        canvas.drawBitmap(mBitmap, null, mRect2, mPaint)
    }




        private fun customLine(x1: Float, y1: Float, x2: Float, y2: Float, r1: Float, r2: Float){
        var normalX = -(y2 - y1)
        var normalY = (x2 - x1)
        val norm = sqrt( normalX * normalX + normalY * normalY)
        normalX /= norm
        normalY /= norm

        mPath.moveTo(x1 - normalX * r1, y1 - normalY * r1)
        mPath.lineTo(x1 + normalX * r1, y1 + normalY * r1)
        mPath.lineTo(x2 + normalX * r2, y2 + normalY * r2)
        mPath.lineTo(x2 - normalX * r2, y2 - normalY * r2)
        mPath.close()

        //mTempPath.addCircle(x1, y1, r1, Path.Direction.CW)
        mPath.addCircle(x2, y2, r2, Path.Direction.CCW)
        //mTempPath.op(mTempTempPath, Path.Op.UNION)
    }
 */