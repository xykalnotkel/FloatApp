package io.xystudio.floatspace

import android.graphics.drawable.Drawable

data class AppEntry(
    val label: String,
    val component: String,
    val packageName: String,
    val icon: Drawable
)
