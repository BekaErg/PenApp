package com.example.drawingapp

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.LinearLayout

class PenTypes(context: Context, attrs: AttributeSet): LinearLayout(context, attrs) {
    companion object {
        const val BRUSH = 0
        const val FOUTNAIN_PEN = 1
        const val BALL_PEN = 2
    }

    var penList = arrayListOf<FrameLayout>()
}