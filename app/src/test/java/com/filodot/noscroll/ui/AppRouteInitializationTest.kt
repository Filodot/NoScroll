package com.filodot.noscroll.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppRouteInitializationTest {
    @Test
    fun dashboardFirstInitializationProducesCompleteTopLevelRoutes() {
        val dashboard = AppRoute.Dashboard

        val routes = AppRoute.topLevel

        assertSame(dashboard, routes.first())
        assertEquals(
            listOf("С", "О", "З", "У", "Н"),
            routes.map(AppRoute::marker),
        )
    }
}
