import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TankblickApiKeyTest {

    @Test
    fun `no key at all stays empty`() {
        assertEquals("", TankblickApiKey.resolve(""))
        assertEquals("", TankblickApiKey.resolve("   "))
    }

    @Test
    fun `a plain uuid is taken as is`() {
        assertEquals(
            "00000000-0000-0000-0000-000000000002",
            TankblickApiKey.resolve("00000000-0000-0000-0000-000000000002"),
        )
    }

    @Test
    fun `surrounding whitespace and case do not matter`() {
        assertEquals(
            "1e6334c3-1b98-4e2c-884f-2d2730b40cd6",
            TankblickApiKey.resolve("  1E6334C3-1B98-4E2C-884F-2D2730B40CD6\n"),
        )
    }

    @Test
    fun `a reversed base64 key is decoded`() {
        assertEquals(
            "1e6334c3-1b98-4e2c-884f-2d2730b40cd6",
            TankblickApiKey.resolve("2Q2YwQjYwMzNyQmMtYGN4gTLjJTZ00CO5IWMtMzY0MzM2UWM"),
        )
    }

    @Test
    fun `the form spritpreise uses decodes too`() {
        assertEquals(
            "98210e80-7505-3843-358e-0190b44866db",
            TankblickApiKey.resolve("iRmN2gDN0IGM5EDMtUGO1MTLzQDOz0SNwUzNtADOlBTMygTO"),
        )
    }

    @Test
    fun `base64 that is not reversed is rejected`() {
        // Forgetting to reverse would otherwise silently compile in nothing.
        assertFailsWith<IllegalArgumentException> {
            TankblickApiKey.resolve("MWU2MzM0YzMtMWI5OC00ZTJjLTg4NGYtMmQyNzMwYjQwY2Q2")
        }
    }

    @Test
    fun `a truncated uuid is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TankblickApiKey.resolve("1e6334c3-1b98-4e2c-884f")
        }
    }

    @Test
    fun `something that is not base64 at all is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TankblickApiKey.resolve("not a key")
        }
    }

    @Test
    fun `base64 of something that is not a uuid is rejected`() {
        // "hello world" reversed-base64: decodes cleanly, still not a key.
        assertFailsWith<IllegalArgumentException> {
            TankblickApiKey.resolve("=QGby92dg8GbsVGa")
        }
    }

    @Test
    fun `the failure message does not leak the key`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            TankblickApiKey.resolve("1e6334c3-1b98-4e2c-884f")
        }
        // Build logs get pasted into issues.
        assertEquals(false, thrown.message!!.contains("1e6334c3"))
    }
}
