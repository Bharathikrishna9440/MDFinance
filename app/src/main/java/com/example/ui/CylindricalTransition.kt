package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun <T> CylindricalTurnContainer(
    targetState: T,
    modifier: Modifier = Modifier,
    directionProvider: (T, T) -> Int, // returns 1 for next (swipe left), -1 for prev (swipe right)
    content: @Composable (T) -> Unit
) {
    var previousState by remember { mutableStateOf(targetState) }
    var direction by remember { mutableStateOf(1) }

    if (targetState != previousState) {
        direction = directionProvider(previousState, targetState)
        previousState = targetState
    }

    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            if (direction >= 0) {
                // Swipe Left (Next): new slides in from right, old slides out to left
                slideInHorizontally(animationSpec = tween(450, easing = FastOutSlowInEasing)) { width -> width } + 
                        fadeIn(animationSpec = tween(450, easing = FastOutSlowInEasing)) togetherWith
                slideOutHorizontally(animationSpec = tween(450, easing = FastOutSlowInEasing)) { width -> -width } + 
                        fadeOut(animationSpec = tween(450, easing = FastOutSlowInEasing))
            } else {
                // Swipe Right (Prev): new slides in from left, old slides out to right
                slideInHorizontally(animationSpec = tween(450, easing = FastOutSlowInEasing)) { width -> -width } + 
                        fadeIn(animationSpec = tween(450, easing = FastOutSlowInEasing)) togetherWith
                slideOutHorizontally(animationSpec = tween(450, easing = FastOutSlowInEasing)) { width -> width } + 
                        fadeOut(animationSpec = tween(450, easing = FastOutSlowInEasing))
            }
        },
        modifier = modifier,
        label = "PageSlideAndFadeTurn"
    ) { state ->
        Box(modifier = Modifier) {
            content(state)
        }
    }
}

