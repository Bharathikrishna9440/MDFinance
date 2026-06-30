package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale
import com.example.util.CurrencyFormatter


@Composable
fun AnimatedNumberText(
    targetValue: Double,
    modifier: Modifier = Modifier,
    prefix: String = "₹",
    suffix: String = "",
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    delayMillis: Int = 0,
    durationMillis: Int = 1200,
    allowDecimal: Boolean = false
) {
    val animatableValue = remember { Animatable(0f) }

    LaunchedEffect(targetValue) {
        animatableValue.snapTo(0f)
        animatableValue.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing
            )
        )
    }

    val formattedValue = if (allowDecimal) {
        CurrencyFormatter.format(animatableValue.value.toDouble(), 2)
    } else {
        CurrencyFormatter.format(animatableValue.value.toDouble(), 0)
    }
    val textToDisplay = "$prefix$formattedValue$suffix"

    Text(
        text = textToDisplay,
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        fontSize = fontSize,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}
