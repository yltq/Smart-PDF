package com.zunelo.pdfreader.application

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MainActivity is singleTask, so Android can reuse the existing task without an
        // ActivityManager task scan on the launch path.
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
