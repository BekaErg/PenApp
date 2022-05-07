package com.example.drawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.get

class ColorPalette(context: Context, attrs: AttributeSet): LinearLayout(context, attrs){
    private var frameList = arrayListOf<FrameLayout>()
    var selectedIdx = 0
        set(value){
            frameList[field][0].visibility = INVISIBLE
            field = value
            frameList[field][0].visibility = VISIBLE
        }

    var secondPress = false
    private var colorList = arrayListOf(0xFFAA0000.toInt(), 0xFF0044AA.toInt(), Color.BLACK)
    var switcher: (color: Int) -> Unit = {}

    fun setColor (color: Int) {
        (frameList[selectedIdx][1] as ImageView).setColorFilter(color)
        colorList[selectedIdx] = color
    }

    fun setColors (arr: ArrayList<Int>) {
        if (arr.size != colorList.size) {
            throw Exception("array size does not match")
        }
        for ((i, num) in arr.withIndex()) {
            colorList[i] = num
            (frameList[i][1] as ImageView).setColorFilter(colorList[i])
        }
    }
    fun getColors (): ArrayList<Int> {
        val ans = arrayListOf<Int>()
        for ( num in colorList) {
            ans.add(num)
        }
        return ans
    }

    init {
        inflate(context, R.layout.color_palette, this)

        frameList.add(findViewById(R.id.color_circle_frame_0))
        frameList.add(findViewById(R.id.color_circle_frame_1))
        frameList.add(findViewById(R.id.color_circle_frame_2))


        for ((i,frame) in frameList.withIndex()) {
            (frame[1] as ImageView).setColorFilter(colorList[i])
        }

        frameList[selectedIdx][0].visibility = VISIBLE

        for (i in 0 until frameList.size) {
            frameList[i].setOnClickListener{
                secondPress = (selectedIdx == i)
                selectedIdx = i
                deselectAll()

                frameList[i][0].visibility = VISIBLE
                switcher(colorList[i])

                if (secondPress) {
                    (frameList[i][1] as ImageView).setColorFilter(colorList[i])
                }
            }
        }
    }


    private fun deselectAll() {
        for (frame in frameList) {
            frame[0].visibility = INVISIBLE
        }
    }

}