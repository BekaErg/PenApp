package com.example.drawingapp

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.get

class ToolSelectorLayout (context: Context, attrs: AttributeSet): LinearLayout(context, attrs) {
    companion object {
        const val PEN = 0
        const val LINE = 1
        const val ERASER = 2
    }


    var frameList = arrayListOf<FrameLayout>()
    private var mSelectedIdx = 0

    var switcher: (toolType: Int) -> Unit = {}

    fun getTool(): Int{
        return mSelectedIdx
    }
    fun setTool(i: Int) {
        //mSelectedIdx = i
        frameList[i].performClick()
    }

    init {
        inflate(context, R.layout.drawing_tools, this)

        frameList.add(findViewById(R.id.pen_frame))
        frameList.add(findViewById(R.id.line_frame))
        frameList.add(findViewById(R.id.eraser_icon))
        frameList[mSelectedIdx][0].visibility = VISIBLE

        for (i in 0 until frameList.size) {
            frameList[i].setOnClickListener(){
                mSelectedIdx = i
                deselectAll()
                frameList[i][0].visibility = VISIBLE
                switcher(i)
            }
        }
    }

    private fun deselectAll() {
        for (frame in frameList) {
            frame[0].visibility = INVISIBLE
        }
    }
}