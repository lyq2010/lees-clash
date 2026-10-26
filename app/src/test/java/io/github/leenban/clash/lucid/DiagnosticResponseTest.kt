package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.DiagnosticJson
import org.junit.Assert.*
import org.junit.Test

class DiagnosticResponseTest {
    @Test
    fun typedArrayResponsesAndErrorObjectsAreHandledSeparately() {
        assertEquals("[\"Manual\"]", DiagnosticJson.checked("[\"Manual\"]"))
        assertEquals("[]", DiagnosticJson.checked("[]"))
        assertEquals("{\"total\":0}", DiagnosticJson.checked("{\"total\":0}"))
        assertThrows(IllegalStateException::class.java) {
            DiagnosticJson.checked("{\"error\":\"No profile\"}")
        }
    }
}
