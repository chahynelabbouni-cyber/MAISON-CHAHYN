package com.maisonchahyn.app.launcher

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Squircle (super-ellipse) : la forme des icônes iPhone. */
class SquircleShape(private val n: Double = 5.0) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val a = size.width / 2f
        val b = size.height / 2f
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val ct = cos(t)
            val st = sin(t)
            val x = a + a * (sign(ct) * abs(ct).pow(2.0 / n)).toFloat()
            val y = b + b * (sign(st) * abs(st).pow(2.0 / n)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

object IconShapes {
    val options = listOf(
        "squircle" to "iPhone",
        "arrondi" to "Arrondi",
        "cercle" to "Cercle",
        "goutte" to "Goutte",
        "carre" to "Carré",
    )
    private val squircle = SquircleShape()

    fun of(id: String): Shape = when (id) {
        "cercle" -> CircleShape
        "arrondi" -> RoundedCornerShape(24)
        "goutte" -> RoundedCornerShape(50, 50, 12, 50)
        "carre" -> RoundedCornerShape(10)
        else -> squircle
    }
}
