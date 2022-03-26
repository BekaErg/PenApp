package com.example.drawingapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.iterator
import androidx.preference.PreferenceManager
import com.example.drawingapp.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.jarti.ColorPickerView.ColorPickerView
import java.io.IOException
import java.util.*


class MainActivity : AppCompatActivity(){

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ){
        if (it != null) {
            val imageView = ImageView(this)
            imageView.setImageURI(it)
            //TODO
            //binding.frameForCanvas.addView(imageView)
        }
        Snackbar.make(binding.root, "this is the URI: $it", Snackbar.LENGTH_INDEFINITE)
            .setAction("Cancel") {
        }.show()
        //Toast.makeText(this, imageURI.toString(), Toast.LENGTH_SHORT).show()
    }

    private val savePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument()) {
        if (it != null) {
            Log.i("uri", it.path!!)

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
        moreOptions()

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



    private fun loadBitmap() {
        pickPhotoLauncher.launch("image/*")
        //imageView.setImageURI(imageURI)
        //binding.frameForCanvas.addView(imageView)
        /*
        val pickPhotoIntent = Intent(Intent.ACTION_PICK,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(pickPhotoIntent, 0)
         */
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
                //Toast.makeText(this, "The feature has not been added yet", Toast.LENGTH_SHORT).show()
                //requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                /*
                val result = saveBitmap()
                Snackbar.make(binding.root, result, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Cancel") {
                    }.show()

                 */
                //loadBitmap()
                true
            }

            R.id.lock_zoom -> {
                item.isChecked = !item.isChecked
                binding.canvasContainer.lockZoom = item.isChecked

                true
            }
            R.id.load_image -> {
                loadBitmap()
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

    override fun onStop() {
        super.onStop()
        saveDrawingParams()
    }

    override fun onWindowFocusChanged(hasFocus : Boolean) {
        super.onWindowFocusChanged(hasFocus)
        loadGeneralSettings()
    }

    private fun loadGeneralSettings(){
        val manager = PreferenceManager.getDefaultSharedPreferences(this)
        val penMode = manager.getBoolean("pen_mode", false)
        drawView.penMode = penMode
        //Toast.makeText(this, "settings has been updated penMode: $penMode", Toast.LENGTH_SHORT).show()
    }

    private fun initDrawView() {
        colorPalette = binding.colorPaletteMain
        colorPicker = binding.colorPickerView
        toolSelector = binding.drawingTools
        brushSlider = binding.brushSlider
        opacitySlider = binding.opacitySlider
        moreOptions = binding.moreOptionsLayout

        //drawView = binding.canvasContainer
        drawView = DrawView(this)
        //drawView.layoutParams = ViewGroup.LayoutParams(200, 333)
        drawView.setBackgroundColor(Color.WHITE)


        binding.canvasContainer.addView(drawView)
        //binding.canvasContainer.addView(View(this))
    }

    private fun saveDrawingParams() {
        val sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE)
        parametersAdapter.copyFromDrawView(drawView)
        parametersAdapter.copyFromPalette(colorPalette)
        parametersAdapter.copyFromToolSelector(toolSelector)

        val json = Gson().toJson(parametersAdapter)
        val editor = sharedPreferences.edit()
        editor.putString("drawingParameters", json)
        editor.apply()
    }

    private fun loadDrawingParams() {
        //TODO first loading crashes
        val sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE)

        val json = sharedPreferences.getString("drawingParameters", "")
        if (json != "") {
            parametersAdapter = Gson().fromJson(json, ParametersAdapter::class.java)
        }
        parametersAdapter.copyToBrushSlider(brushSlider)
        parametersAdapter.copyToColorPalette(colorPalette)
        parametersAdapter.copyToDrawView(drawView)
        parametersAdapter.copyToTool(toolSelector)

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
            drawView.selectedTool = it
        }
    }

    private fun moreOptions() {
        for (view in moreOptions){
            view.setOnClickListener {
                when (it.tag) {
                    "settings" -> {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        moreOptions.visibility = View.GONE
                    }
                    "clear_image" -> {
                        drawView.clearCanvas()
                    }
                }
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
                //Toast.makeText(this, "opacity is $value", Toast.LENGTH_SHORT).show()
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
        var tool = ToolSelectorLayout.PEN

        fun copyToDrawView(drawView: DrawView) {
            drawView.strokeSize = strokeSize
            drawView.selectedTool = tool
            drawView.brushColor = colorList[colorIdx]
        }

        fun copyToColorPalette(palette: ColorPalette) {
            palette.setColors(colorList)
            if (colorIdx >= colorList.size) {
                throw Error ("colorIdx is bigger than colorList")
            }
            palette.selectedIdx = colorIdx
        }
        fun copyToTool(toolSelector: ToolSelectorLayout){
            toolSelector.setTool(tool)
            //Todo why does using parent class members cause an error (commenting Gson in loadParameters fixes)
            /*if (drawViewIsInitialized) {
                Toast.makeText(baseContext, "gela", Toast.LENGTH_SHORT).show()
            }
            */
        }

        fun copyToBrushSlider(view: Slider) {
            view.value = strokeSize
        }

        fun copyFromDrawView(drawView: DrawView) {
            strokeSize = drawView.strokeSize
            tool = drawView.selectedTool
        }
        fun copyFromPalette(palette : ColorPalette) {
            colorList = palette.getColors()
            colorIdx = palette.selectedIdx
        }
        fun copyFromToolSelector(toolSelector: ToolSelectorLayout) {
            tool = toolSelector.getTool()
        }

    }

    companion object {
        private const val SHARED_PREFS = "sharedPrefs"
    }
}













/*
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ){
        if (it) {
            Log.i("permission: ", "granted")
        } else {
            Log.i("permission: ", "Denied")
        }
    }

    private fun requestPermission(permission: String) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                Toast.makeText(this, "permission is granted", Toast.LENGTH_SHORT).show()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, permission
            ) -> {
            Snackbar.make(binding.root, "You need to grant permission in order to save the image", Snackbar.LENGTH_LONG)
                .setAction("Review Permission") {
                    requestPermissionLauncher.launch(
                        permission)
                }.show()
            }
            else -> {
                requestPermissionLauncher.launch(
                    permission)
            }
        }
    }











    private fun saveBitmap(uri: Uri): String {
        val bitmap = drawView.getBitmap()
        var result = ""
        try {
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bytes)
            //val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString();
            //val f = File(externalCacheDir!!.absolutePath + File.separator + "gela.jpg")

            val f = File(externalCacheDir!!.absolutePath + File.separator + "gela.jpg")
            Log.i("uri2", f.path)

            //val f = File(uri.path!! + ".jpg")
            val fos = FileOutputStream(f)
            fos.write(bytes.toByteArray())
            fos.close()

            result = f.absolutePath
        } catch (e: Exception) {
            result = "failed" + uri.path!!
            e.printStackTrace()
        }
        return result
    }

    fun updateOrRequestPermission(){
        readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        writePermission = writePermission || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

        val permissionRequests = mutableListOf<String>()
        if (!readPermission) {
            permissionRequests.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (!writePermission) {
            permissionRequests.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, permissionRequests.toTypedArray(), 0)

    }

 */