package com.leviz.touchtech

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Model.start(applicationContext)
        Music.load(applicationContext)
        setContent { TouchApp() }
    }

    override fun onResume() {
        super.onResume()
        Model.recheckNow(applicationContext)
    }
}
