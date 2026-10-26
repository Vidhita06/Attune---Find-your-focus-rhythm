package com.attune.prediction

import com.attune.prediction.classifier.LinkAnalyzer
import com.attune.prediction.classifier.LinkSafety
import org.junit.Assert.*
import org.junit.Test

/**
 * Link analysis is evaluated on its own, separately from message importance. The corpus below is hand-written (not a measured sample of
 * real traffic), so the tables it supports show that the rules behave as designed, not how often they are right in the wild.
 */
class LinkAnalyzerTest {
    private fun level(t: String) = LinkAnalyzer.assess(t).safety
    private fun signals(t: String) = LinkAnalyzer.assess(t).signals

    @Test fun extractsUrlsWithAndWithoutScheme() {
        assertEquals(listOf("example.com"), LinkAnalyzer.hosts("see https://www.example.com/a?b=1 now"))
        assertEquals(listOf("example.org"), LinkAnalyzer.hosts("go to example.org/page"))
        assertEquals(listOf("bit.ly"), LinkAnalyzer.hosts("(bit.ly/3xYz)."))
        assertEquals(listOf("a.com", "b.co.in"), LinkAnalyzer.hosts("read a.com and then http://b.co.in/x, thanks"))
        assertEquals(listOf("site.dev"), LinkAnalyzer.hosts("deploy at site.dev:8080/x"))
        assertEquals(listOf("zomato.com"), LinkAnalyzer.hosts("Order at:zomato.com!"))
    }

    @Test fun ordinaryTextIsNotALink() {
        for (t in listOf("meet at 5 p.m. tomorrow", "e.g. the rent", "version 3.5 is out", "report.txt attached", "mail me at rahul@gmail.com", "ok...", "Rs. 4,200 debited", "see you at 10.30"))
            assertEquals(t, LinkSafety.NONE, level(t))
    }

    @Test fun malformedLinksDoNotCrashAndAreNotTrusted() {
        for (t in listOf("http://", "https:///x", "http://.com", "http://a..b.com", "www.", "http://-bad-.com", "http://999.1.1.1/x", "ftp://", "https://" + "a".repeat(70) + ".com"))
            assertEquals(t, LinkSafety.NONE, level(t))
    }

    @Test fun multipleLinksReportTheWorstOne() {
        val a = LinkAnalyzer.assess("docs.google.com/d/1 and http://hdfc-secure-verify.top/kyc")
        assertEquals(LinkSafety.HIGH_RISK, a.safety); assertEquals(2, a.findings.size)
        assertEquals(LinkSafety.UNKNOWN, a.findings.first { it.host == "docs.google.com" }.safety)
    }

    @Test fun shortenerIsLabelledAsHidingTheDestinationNotAsMalicious() {
        val a = LinkAnalyzer.assess("details: https://bit.ly/3abc")
        assertEquals(LinkSafety.CAUTION, a.safety); assertEquals(listOf("shortener"), a.signals)
        assertTrue(LinkAnalyzer.describe("shortener").contains("not necessarily harmful"))
        assertTrue(a.limitations.any { it.contains("hides its real destination") })
        assertNotEquals(LinkSafety.HIGH_RISK, a.safety)
    }

    @Test fun unknownLinkIsNeverSafe() {
        for (t in listOf("https://example.org/x", "see wikipedia.org", "https://news.ycombinator.com")) {
            val a = LinkAnalyzer.assess(t)
            assertEquals(t, LinkSafety.UNKNOWN, a.safety)
            assertTrue(a.limitations.any { it.contains("Nothing was opened") })
        }
        assertEquals("Unknown", LinkAnalyzer.label(LinkSafety.UNKNOWN))
        assertEquals(LinkSafety.values().toList(), listOf(LinkSafety.NONE, LinkSafety.UNKNOWN, LinkSafety.CAUTION, LinkSafety.HIGH_RISK)) // no "safe" level exists
    }

    @Test fun httpsIsNotProofOfSafetyAndHttpAloneIsNotAlarming() {
        assertEquals(LinkSafety.HIGH_RISK, level("https://hdfcbank.com.verify-now.top/login"))
        val http = LinkAnalyzer.assess("http://example.org/page")
        assertEquals(LinkSafety.UNKNOWN, http.safety); assertTrue("insecure_http" in http.notes)
    }

    @Test fun rawIpAddressIsCautionAndWithLoginPageOverHttpHighRisk() {
        assertEquals(LinkSafety.CAUTION, level("http://192.168.4.21/files"))
        assertTrue("ip_address" in signals("http://192.168.4.21/files"))
        assertEquals(LinkSafety.HIGH_RISK, level("http://203.0.113.9/secure/login"))
    }

    @Test fun lookalikeAndMisleadingDomainsAreFlagged() {
        assertEquals(LinkSafety.HIGH_RISK, level("update KYC at http://hdfc-secure-verify.top/kyc/1"))
        assertTrue("brand_lookalike" in signals("http://hdfc-secure-verify.top/kyc/1"))
        assertEquals(LinkSafety.HIGH_RISK, level("https://paypal.com.account-check.net/signin"))
        assertTrue("misleading_subdomain" in signals("https://paypal.com.account-check.net/signin"))
        assertTrue("brand_lookalike" in signals("https://amaz0n-offers.live/win"))
        assertEquals(LinkSafety.HIGH_RISK, level("https://hdfcbank.com@evil-site.top/login"))
        assertTrue("userinfo" in signals("https://hdfcbank.com@evil-site.top/login"))
    }

    @Test fun realBrandDomainsAndUnrelatedWordsAreNotFlagged() {
        for (t in listOf("https://www.hdfcbank.com/personal", "https://netbanking.hdfcbank.com/login", "amazon.in/dp/B0X", "https://www.sbi.co.in/web/personal", "https://accounts.google.com/signin",
            "https://pineapple.com/recipes", "https://applebees.com/menu", "https://www.flipkart.com/cart"))
            assertTrue(t, level(t) <= LinkSafety.UNKNOWN)
    }

    @Test fun unusualEndingsAndLongUrlsAloneAreNotDangerous() {
        assertEquals(LinkSafety.UNKNOWN, level("https://my-portfolio.xyz"))
        assertTrue("risky_tld" in LinkAnalyzer.assess("https://my-portfolio.xyz").notes)
        assertEquals(LinkSafety.UNKNOWN, level("https://example.org/" + "a/".repeat(80) + "?q=" + "x".repeat(200)))
        assertEquals(LinkSafety.UNKNOWN, level("https://cdn.some-long-subdomain.example.co.uk/file"))
    }

    @Test fun internationalisedAndPunycodeHostsAreHandled() {
        val a = LinkAnalyzer.assess("visit https://xn--pypal-4ve.com/login now")
        assertEquals(listOf("xn--pypal-4ve.com"), a.hosts); assertTrue("punycode" in a.signals); assertTrue(a.safety >= LinkSafety.CAUTION)
        val u = LinkAnalyzer.assess("https://bücher.example/shop")
        assertEquals(listOf("xn--bcher-kva.example"), u.hosts); assertTrue("punycode" in u.signals)
        assertTrue(LinkAnalyzer.assess("https://раypal.com/login").signals.contains("punycode")) // Cyrillic p/a inside "paypal"
    }

    @Test fun redirectParameterIsCautionNotHighRisk() {
        val a = LinkAnalyzer.assess("https://example.org/out?url=https://other.example/x")
        assertEquals(LinkSafety.CAUTION, a.safety); assertTrue("redirect_param" in a.signals)
    }

    @Test fun analysisNeverTouchesTheNetwork() {
        // The analyzer only imports java.net.IDN (a pure string codec). Make sure nothing network-capable is on its classpath surface.
        val src = java.io.File("src/main/java/com/attune/prediction/classifier/LinkAnalyzer.kt").readText()
        for (banned in listOf("URL(", "openConnection", "InetAddress", "Socket", "HttpURLConnection", "okhttp", "getByName", "URI.create")) assertFalse("uses $banned", banned in src.replace("val URL", ""))
        // and a hostile-looking input resolves instantly without a lookup (a DNS call would hang/fail offline)
        val t0 = System.nanoTime(); repeat(500) { LinkAnalyzer.assess("http://does-not-exist.invalid/x https://bit.ly/a 203.0.113.9/login") }
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 3000)
    }
}
