package com.schedule.app.utils

import android.util.Log

object Logger {

    private const val TAG = "ScheduleApp"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String) {
        Log.e(TAG, message)
    }
}
