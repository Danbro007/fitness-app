package com.shanqijie.fitnessapp.domain

enum class HorizontalSwipe {
    Left,
    Right,
}

object GestureNavigation {
    fun directionFromDrag(totalDragX: Float, threshold: Float): HorizontalSwipe? =
        when {
            totalDragX <= -threshold -> HorizontalSwipe.Left
            totalDragX >= threshold -> HorizontalSwipe.Right
            else -> null
        }

    fun nextIndex(currentIndex: Int, itemCount: Int, swipe: HorizontalSwipe): Int {
        if (itemCount <= 0) return 0
        val safeIndex = currentIndex.coerceIn(0, itemCount - 1)
        return when (swipe) {
            HorizontalSwipe.Left -> (safeIndex + 1).coerceAtMost(itemCount - 1)
            HorizontalSwipe.Right -> (safeIndex - 1).coerceAtLeast(0)
        }
    }
}
