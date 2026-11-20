package com.focustracker

import android.app.Application
import com.focustracker.data.AppDatabase

class FocusApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
}
