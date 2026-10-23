package com.example.dimanow.work

import com.example.dimanow.meal.DormitoryMealData
import java.time.ZonedDateTime

object RefreshPolicy {
    fun shouldRefreshDormitory(data: DormitoryMealData, now: ZonedDateTime): Boolean =
        !data.hasCurrentWeek(now.toLocalDate())
}
