package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicOriginTlsContractTest {
    @Test
    fun publicOriginTransportKeepsPlatformTlsAndFailsClosed() {
        val root = repositoryRoot()
        val transport = File(
            root,
            "app/src/main/java/com/morimil/app/net/SafeHttpTransport.kt"
        ).readText()
        val sourcePolicy = File(
            root,
            "app/src/main/java/com/morimil/app/net/NetSourcePolicy.kt"
        ).readText()

        assertTrue(
            "The public-origin TLS exception must remain explicitly tracked",
            "PUBLIC_ORIGIN_TLS_BOUNDARY: CODEQL_ALERT_33_ISSUE_132" in transport
        )
        assertTrue(
            "The transport must connect only to the already-validated DNS set",
            ".dns(PublicOnlyDns(resolver))" in transport
        )
        assertTrue(
            "Automatic TLS redirects must remain disabled for per-hop validation",
            ".followSslRedirects(false)" in transport
        )
        assertTrue(
            "Public-origin requests must fail closed on non-HTTPS URLs",
            "parsed.protocol != \"https\"" in sourcePolicy
        )
        assertFalse(
            "The public-origin transport must not replace platform hostname verification",
            "hostnameVerifier" in transport
        )
        assertFalse(
            "The public-origin transport must not install a custom TLS trust manager",
            "sslSocketFactory" in transport || "trustManager" in transport
        )
    }

    private fun repositoryRoot(): File {
        return sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
    }
}
