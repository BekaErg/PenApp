package jrt.bekaerg.penapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.preference.PreferenceManager
import com.example.penapp.R
import com.example.penapp.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.jarti.ColorPickerView.ColorPickerView
import java.io.IOException
import java.util.*



class MainActivity : AppCompatActivity(){
    private val savePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument()) {
        if (it != null) {
            val bitmap = drawView.getBitmap()
            contentResolver.openOutputStream(it).use{ bytes ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bytes)) {
                    throw IOException("Could not save image")
                } else {
                    Snackbar.make(binding.root, "file saved successfully", Snackbar.LENGTH_LONG)
                        .setAction("Cancel") {
                        }.show()
                }
            }
        }
    }

    private var activityIsRunning = false

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawView: DrawView
    private var parametersAdapter = ParametersAdapter()

    private lateinit var colorPalette: ColorPalette
    private lateinit var toolSelector: ToolSelectorLayout
    private lateinit var colorPicker: ColorPickerView
    private lateinit var brushSlider: Slider
    private lateinit var opacitySlider: Slider
    private lateinit var moreOptions: LinearLayout

    @SuppressLint("ClickableViewAccessibility")

    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //TODO fix rotation issue using fragments
        //Toast.makeText(this, "onCreate()", Toast.LENGTH_LONG).show()
        val toolbar = binding.toolbarBackground
        toolbar.title =""
        toolbar.setTitleTextColor(Color.WHITE)
        setSupportActionBar(toolbar)

        initDrawView()
        selectColor()
        selectTool()
        brushSizeSelecting()
        undoRedo()
        opacitySlider()

        //make invisible
        drawView.setOnTouchListener { _ : View, event : MotionEvent ->
            drawView.requestFocus()
            if (event.action == MotionEvent.ACTION_DOWN) {
                allInvisible()
                false
            } else {false}
        }
        loadDrawingParams()
    }

    override fun onStart() {
        super.onStart()
        activityIsRunning = true
    }

    override fun onStop() {
        super.onStop()
        activityIsRunning = false
        saveDrawingParams()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.more_options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item : MenuItem) : Boolean {
        return when (item.itemId) {
            R.id.save_image -> {
                val name = UUID.randomUUID().toString() + "-jarti.jpg"
                savePhotoLauncher.launch(name)
                true
            }
            R.id.lock_zoom -> {
                item.isChecked = !item.isChecked
                binding.canvasContainer.lockZoom = item.isChecked
                true
            }
            R.id.show_skeleton -> {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    drawView.fillType = Paint.Style.STROKE
                    drawView.drawingEngine = DrawView.DrawingEngine.PENPATH_DRAW
                    Snackbar.make(this.drawView,"This will decrease performance when drawing long continuous path", Snackbar.LENGTH_LONG).show()
                } else {
                    drawView.fillType = Paint.Style.FILL
                    drawView.drawingEngine = DrawView.DrawingEngine.LAST_SEGMENT
                }
                drawView.invalidate()
                true
            }
            R.id.clear_canvas -> {
                drawView.clearCanvas()
                true
            }
            R.id.open_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    //TODO change this by popUp view
    @SuppressLint("RestrictedApi")
    private fun penSelectorMenu(context: Context, v : View) {
        val popup = PopupMenu(context, v)
        //val inflater = this@MainActivity.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        popup.inflate(R.menu.brush_menu)

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.brush -> {
                    toolSelector.setTool(ToolType.BRUSH)
                    drawView.selectedTool = ToolType.BRUSH
                    true
                }
                R.id.fountain_pen -> {
                    toolSelector.setTool(ToolType.PEN_FOUNTAIN)
                    drawView.selectedTool = ToolType.PEN_FOUNTAIN
                    true
                }
                R.id.ball_pen -> {
                    drawView.selectedTool = ToolType.PEN_BALL
                    toolSelector.setTool(ToolType.PEN_BALL)
                    true
                }
                else -> {
                    false
                }
            }
        }
        val menuHelper = MenuPopupHelper(this,  popup.menu as MenuBuilder, v)
        menuHelper.setForceShowIcon(true)
        menuHelper.show()
    }


    override fun onWindowFocusChanged(hasFocus : Boolean) {
        super.onWindowFocusChanged(hasFocus)
        loadGeneralSettings()
    }

    private fun loadGeneralSettings(){
        val manager = PreferenceManager.getDefaultSharedPreferences(this)
        val penMode = manager.getBoolean("pen_mode", false)
        drawView.penMode = penMode
    }

    private fun initDrawView() {
        colorPalette = binding.colorPaletteMain
        colorPicker = binding.colorPickerView
        toolSelector = binding.drawingTools
        brushSlider = binding.brushSlider
        opacitySlider = binding.opacitySlider
        moreOptions = binding.moreOptionsLayout

        drawView = DrawView(this)
        drawView.setBackgroundColor(Color.WHITE)

        binding.canvasContainer.addView(drawView)
        drawView.selectedTool = ToolType.PEN_BALL
    }

    private fun saveDrawingParams() {
        val sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE)
        parametersAdapter.copyFromDrawView(drawView)
        parametersAdapter.copyFromPalette(colorPalette)

        val json = Gson().toJson(parametersAdapter)
        val editor = sharedPreferences.edit()
        editor.putString("drawingParameters", json)
        editor.apply()
    }

    private fun loadDrawingParams() {
        val sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE)

        val json = sharedPreferences.getString("drawingParameters", "")
        if (json != "") {
            parametersAdapter = Gson().fromJson(json, ParametersAdapter::class.java)
        }
        parametersAdapter.copyToBrushSlider(brushSlider)
        parametersAdapter.copyToColorPalette(colorPalette)
        parametersAdapter.copyToDrawView(drawView)
        //parametersAdapter.copyToTool(toolSelector)
    }

    private fun selectColor() {
        colorPalette.switcher = {
            colorPicker.color = it
            if (colorPalette.secondPress) {
                changeVisibility(colorPicker)
            }
            drawView.brushColor = it
        }
        colorPicker.setOnColorChange { _, cur ->
            colorPalette.setColor(cur)
            drawView.brushColor = cur
            colorPalette.invalidate()
        }
    }

    private fun selectTool() {
        toolSelector.switcher = {
            tool, toolGroup, alreadySelected, view ->
            drawView.selectedTool = tool
            if (activityIsRunning && alreadySelected && toolGroup == 0) {
                penSelectorMenu(this@MainActivity, view)
            }
        }
    }


    private fun brushSizeSelecting() {
        val brushSizeDummy = binding.dummyBrushSize
        brushSlider.value = drawView.strokeSize

        brushSizeDummy.setOnClickListener {
            changeVisibility(brushSlider)
        }

        brushSlider.addOnChangeListener(
            Slider.OnChangeListener { _, value, _ ->
                drawView.strokeSize = value
                binding.brushSizeIcon.scaleX = value / 100
                binding.brushSizeIcon.scaleY = value / 100
            }
        )
    }

    private fun opacitySlider() {
        opacitySlider.value = 100f

        binding.opacityIcon.setOnClickListener {
            changeVisibility(opacitySlider)
        }

        opacitySlider.addOnChangeListener(
            Slider.OnChangeListener { _, value, _ ->
                drawView.opacity = value / 100
                binding.opacityIcon.progress = value.toInt()
            }

        )
    }

    private fun changeVisibility(view: View) {
        if (view.visibility == View.VISIBLE) {
            view.visibility = View.INVISIBLE
        } else {
            allInvisible()
            view.visibility = View.VISIBLE
        }
    }

    private fun undoRedo() {
        binding.undoButton.setOnClickListener{
            drawView.undo()
        }
        binding.redoButton.setOnClickListener{
            drawView.redo()
        }
    }

    private fun allInvisible() {
        brushSlider.visibility = View.INVISIBLE
        colorPicker.visibility = View.INVISIBLE
        opacitySlider.visibility = View.INVISIBLE
        moreOptions.visibility = View.INVISIBLE
    }

    private inner class ParametersAdapter {
        var strokeSize = 10f
        var colorList = arrayListOf(Color.RED, Color.GREEN, Color.BLUE)
        var colorIdx = 0

        fun copyToDrawView(drawView: DrawView) {
            drawView.strokeSize = strokeSize
            drawView.brushColor = colorList[colorIdx]
        }

        fun copyToColorPalette(palette: ColorPalette) {
            palette.setColors(colorList)
            if (colorIdx >= colorList.size) {
                throw Error ("colorIdx is bigger than colorList")
            }
            palette.selectedIdx = colorIdx
        }

        fun copyToBrushSlider(view: Slider) {
            view.value = strokeSize
        }

        fun copyFromDrawView(drawView: DrawView) {
            strokeSize = drawView.strokeSize
        }
        fun copyFromPalette(palette : ColorPalette) {
            colorList = palette.getColors()
            colorIdx = palette.selectedIdx
        }
    }

    companion object {
        private const val SHARED_PREFS = "sharedPrefs"
    }
}



