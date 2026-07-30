package de.mymiggi.tankblick.navapp

import de.mymiggi.tankblick.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationFeedbackTest {

    /** The map opening is the feedback; a toast on top would be noise. */
    @Test
    fun `says nothing when a navigation app opened`() {
        assertNull(navigationFeedbackRes(launched = true))
    }

    /** A tap that silently does nothing reads as a broken app. */
    @Test
    fun `explains it when no navigation app could be opened`() {
        assertEquals(R.string.navigate_no_app, navigationFeedbackRes(launched = false))
    }
}
