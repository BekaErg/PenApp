package com.example.drawingapp

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState : Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.title = "gela"
    }


    override fun onDestroy() {
        super.onDestroy()
        val manager = PreferenceManager.getDefaultSharedPreferences(this)
        val penMode = manager.getBoolean("pen_mode", false)
        Toast.makeText(this, "destroyed $penMode", Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item : MenuItem) : Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                // Another possibility is below
                //onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }




    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState : Bundle?, rootKey : String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

        }
    }
}