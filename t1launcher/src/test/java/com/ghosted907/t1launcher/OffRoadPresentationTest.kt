package com.ghosted907.t1launcher
import org.junit.Assert.*
import org.junit.Test
class OffRoadPresentationTest {
    @Test fun psiConversionAndInvalidData() {
        assertEquals(34.80905712, OffRoadPresentation.psi(2.4)!!, .00001)
        assertNull(OffRoadPresentation.psi(null))
        assertNull(OffRoadPresentation.psi(Double.NaN))
        assertNull(OffRoadPresentation.psi(0.0))
        assertNull(OffRoadPresentation.psi(-1.0))
    }
    @Test fun compassDirectionsWrap() {
        assertEquals("SE", OffRoadPresentation.compassLabel(125.0))
        assertEquals("N", OffRoadPresentation.compassLabel(360.0))
        assertEquals("N", OffRoadPresentation.compassLabel(0.0))
        assertEquals("NW", OffRoadPresentation.compassLabel(-45.0))
        assertEquals("البوصلة", OffRoadPresentation.compassLabel(null))
    }
}
