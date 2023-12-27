package com.guhao.opensource.cutme.android

import android.animation.TypeEvaluator
import androidx.compose.ui.geometry.Offset

class OffsetValueAnimatorEvaluator: TypeEvaluator<Offset> {
    override fun evaluate(fraction: Float, startValue: Offset, endValue: Offset): Offset {
        val rangeX = (endValue).x - (startValue).x
        val rangeY = endValue.y - (startValue).y

        return Offset(
            x = startValue.x + rangeX * fraction,
            y = startValue.y  + rangeY * fraction
        )
    }
}