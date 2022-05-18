package jrt.bekaerg.penapp

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.get
import androidx.core.view.iterator
import com.example.penapp.R

class ToolSelectorLayout (context: Context, attrs: AttributeSet): LinearLayout(context, attrs) {
    private var selectedTool = ToolType.BRUSH
    private var selectedPen = ToolType.BRUSH

    var frameList = arrayListOf<FrameLayout>()
    private var mSelectedToolGroup = 0
        set(value) {
            field = value
            when (value) {
                1 -> {
                    selectedTool = ToolType.LINE
                }
                2 -> {
                    selectedTool = ToolType.TOOL_ERASER
                }
            }
        }

    var switcher: (selectedTool: ToolType, toolGroup: Int, alreadySelected: Boolean, view: View) -> Unit = { _, _, _, _ ->}

    fun setTool(toolType : ToolType) {
        selectedTool = toolType
        val i = when (toolType) {
            ToolType.BRUSH -> {
                selectedPen = toolType
                switchPen("brush")
                0
            }
            ToolType.PEN_BALL -> {
                selectedPen = toolType
                switchPen("ball_pen")
                0
            }
            ToolType.PEN_FOUNTAIN -> {
                selectedPen = toolType
                switchPen("fountain_pen")
                0
            }
            ToolType.LINE -> {
                frameList[1].performClick()
                1
            }
            ToolType.TOOL_ERASER -> {
                frameList[2].performClick()
                2
            }
        }
        mSelectedToolGroup = i
        //mSelectedIdx = i
    }

    private fun switchPen(penType: String) {
        for (view in frameList[0]) {
            if (view.tag == penType) {
                view.visibility = View.VISIBLE
            } else if (view.tag != "background"){
                view.visibility = View.INVISIBLE
            }
        }
    }

    init {
        inflate(context, R.layout.drawing_tools, this)
        frameList.add(findViewById(R.id.pen_frame))
        frameList.add(findViewById(R.id.line_frame))
        frameList.add(findViewById(R.id.eraser_icon))
        frameList[mSelectedToolGroup][0].visibility = VISIBLE

        for (i in 0 until frameList.size) {
            frameList[i].setOnClickListener(){
                deselectAll()
                frameList[i][0].visibility = VISIBLE
                val alreadySelected = mSelectedToolGroup == i
                mSelectedToolGroup = i
                if (i == 0) selectedTool = selectedPen
                switcher(selectedTool, i, alreadySelected, it)
            }
        }
    }

    private fun deselectAll() {
        for (frame in frameList) {
            frame[0].visibility = INVISIBLE
        }
    }
}