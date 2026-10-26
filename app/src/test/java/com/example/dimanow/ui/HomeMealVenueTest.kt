package com.example.dimanow.ui

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.meal.MealServiceState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-134: Home's 식단 card leans to the 기숙사 for 예인관 residents. */
class HomeMealVenueTest {
    @Test
    fun theDormZoneAlwaysShowsTheDormitory() {
        assertTrue(homeShowsDormitoryMeal(CampusZoneId.YEIN, livesInDorm = false, cafeteria = MealServiceState.OPEN))
    }

    @Test
    fun aResidentAwayFromTheDormSeesTheDormitoryUnlessTheCafeteriaStillServesOnCampus() {
        assertTrue(homeShowsDormitoryMeal(CampusZoneId.OUTSIDE, livesInDorm = true, cafeteria = MealServiceState.OPEN))
        assertTrue(homeShowsDormitoryMeal(CampusZoneId.ONE_ROOM, livesInDorm = true, cafeteria = MealServiceState.CLOSED))
        assertTrue(homeShowsDormitoryMeal(CampusZoneId.MAIN, livesInDorm = true, cafeteria = MealServiceState.CLOSED))
        assertTrue(homeShowsDormitoryMeal(CampusZoneId.MAIN, livesInDorm = true, cafeteria = MealServiceState.NO_MENU))
        assertTrue(homeShowsDormitoryMeal(CampusZoneId.MAIN, livesInDorm = true, cafeteria = null))
        assertFalse(homeShowsDormitoryMeal(CampusZoneId.MAIN, livesInDorm = true, cafeteria = MealServiceState.OPEN))
        assertFalse(homeShowsDormitoryMeal(CampusZoneId.MAIN, livesInDorm = true, cafeteria = MealServiceState.BEFORE_OPEN))
    }

    @Test
    fun othersOutsideTheDormSeeTheCafeteria() {
        assertFalse(homeShowsDormitoryMeal(CampusZoneId.OUTSIDE, livesInDorm = false, cafeteria = MealServiceState.CLOSED))
        assertFalse(homeShowsDormitoryMeal(CampusZoneId.MAIN, livesInDorm = false, cafeteria = MealServiceState.CLOSED))
    }
}
