package com.zunelo.pdfreader.application

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainClassName = MainActivity::class.java.name
        val mainTask = (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
            .appTasks
            .firstOrNull { it.taskInfo.baseActivity?.className == mainClassName }
        if (mainTask != null) {
            mainTask.moveToFront()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
