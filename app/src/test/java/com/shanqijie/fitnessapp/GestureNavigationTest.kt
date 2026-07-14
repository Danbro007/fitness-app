package com.shanqijie.fitnessapp

import com.shanqijie.fitnessapp.domain.GestureNavigation
import com.shanqijie.fitnessapp.domain.HorizontalSwipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureNavigationTest {
    @Test
    fun detectsSwipeDirectionFromDragDistance() {
        assertEquals(HorizontalSwipe.Left, GestureNavigation.directionFromDrag(totalDragX = -96f, threshold = 80f))
        assertEquals(HorizontalSwipe.Right, GestureNavigation.directionFromDrag(totalDragX = 96f, threshold = 80f))
        assertNull(GestureNavigation.directionFromDrag(totalDragX = 24f, threshold = 80f))
    }

    @Test
    fun clampsTabNavigationAtEdges() {
        assertEquals(0, GestureNavigation.nextIndex(currentIndex = 4, itemCount = 0, swipe = HorizontalSwipe.Left))
        assertEquals(1, GestureNavigation.nextIndex(currentIndex = -5, itemCount = 7, swipe = HorizontalSwipe.Left))
        assertEquals(5, GestureNavigation.nextIndex(currentIndex = 99, itemCount = 7, swipe = HorizontalSwipe.Right))
        assertEquals(1, GestureNavigation.nextIndex(currentIndex = 0, itemCount = 7, swipe = HorizontalSwipe.Left))
        assertEquals(0, GestureNavigation.nextIndex(currentIndex = 0, itemCount = 7, swipe = HorizontalSwipe.Right))
        assertEquals(5, GestureNavigation.nextIndex(currentIndex = 6, itemCount = 7, swipe = HorizontalSwipe.Right))
        assertEquals(6, GestureNavigation.nextIndex(currentIndex = 6, itemCount = 7, swipe = HorizontalSwipe.Left))
    }
}
