package com.retrobit.office

import android.app.Application

/**
 * RetroBit Office Application
 * Optimized for Huawei MatePad Mini (8.8" OLED, 165Hz, M-Pencil Pro)
 */
class RetroBitApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Mode input kini keyboard-first; model tulisan tangan tidak lagi dimuat saat start.
    }

    companion object {
        lateinit var instance: RetroBitApplication
            private set
    }
}
