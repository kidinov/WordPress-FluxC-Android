package org.wordpress.android.fluxc.network.rest.wpcom.scan

import com.android.volley.RequestQueue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nhaarman.mockitokotlin2.KArgumentCaptor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.UnitTestUtils
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.scan.ScanStateModel.Credentials
import org.wordpress.android.fluxc.model.scan.ScanStateModel.State
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError
import org.wordpress.android.fluxc.network.BaseRequest.GenericErrorType.NETWORK_ERROR
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComGsonNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder.Response
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder.Response.Success
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.scan.ScanStateResponse.Threat
import org.wordpress.android.fluxc.store.ScanStore.FetchedScanStatePayload
import org.wordpress.android.fluxc.store.ScanStore.ScanStateErrorType
import org.wordpress.android.fluxc.test

@RunWith(MockitoJUnitRunner::class)
class ScanRestClientTest {
    @Mock private lateinit var wpComGsonRequestBuilder: WPComGsonRequestBuilder
    @Mock private lateinit var dispatcher: Dispatcher
    @Mock private lateinit var requestQueue: RequestQueue
    @Mock private lateinit var accessToken: AccessToken
    @Mock private lateinit var userAgent: UserAgent
    @Mock private lateinit var site: SiteModel

    private lateinit var urlCaptor: KArgumentCaptor<String>
    private lateinit var scanRestClient: ScanRestClient
    private val siteId: Long = 12

    @Before
    fun setUp() {
        urlCaptor = argumentCaptor()
        scanRestClient = ScanRestClient(
            wpComGsonRequestBuilder,
            dispatcher,
            null,
            requestQueue,
            accessToken,
            userAgent
        )
    }

    @Test
    fun `fetch scan state builds correct request url`() = test {
        val successResponseJson =
            UnitTestUtils.getStringFromResourceFile(javaClass, JP_SCAN_DAILY_SCAN_IDLE_WITH_THREATS_JSON)
        val scanResponse = getScanStateResponseFromJsonString(successResponseJson)
        initFetchScanState(scanResponse)

        scanRestClient.fetchScanState(site)

        assertEquals(urlCaptor.firstValue, "https://public-api.wordpress.com/wpcom/v2/sites/${site.siteId}/scan/")
    }

    @Test
    fun `fetch scan state dispatches response on success`() = test {
        val successResponseJson =
            UnitTestUtils.getStringFromResourceFile(javaClass, JP_SCAN_DAILY_SCAN_IDLE_WITH_THREATS_JSON)
        val scanResponse = getScanStateResponseFromJsonString(successResponseJson)
        initFetchScanState(scanResponse)

        val payload = scanRestClient.fetchScanState(site)

        with(payload) {
            assertEquals(site, this@ScanRestClientTest.site)
            assertNull(error)
            assertNotNull(scanStateModel)
            requireNotNull(scanStateModel).apply {
                assertEquals(state, State.fromValue(requireNotNull(scanResponse?.state)))
                assertEquals(hasCloud, requireNotNull(scanResponse?.hasCloud))
                assertNull(reason)
                assertNotNull(credentials)
                assertNotNull(threats)
                mostRecentStatus?.apply {
                    assertEquals(progress, scanResponse?.mostRecentStatus?.progress)
                    assertEquals(startDate, scanResponse?.mostRecentStatus?.startDate)
                    assertEquals(duration, scanResponse?.mostRecentStatus?.duration)
                    assertEquals(error, scanResponse?.mostRecentStatus?.error)
                    assertEquals(isInitial, scanResponse?.mostRecentStatus?.isInitial)
                }
                currentStatus?.apply {
                    assertEquals(progress, scanResponse?.mostRecentStatus?.progress)
                    assertEquals(startDate, scanResponse?.mostRecentStatus?.startDate)
                    assertEquals(isInitial, scanResponse?.mostRecentStatus?.isInitial)
                }
                credentials?.forEachIndexed { index, creds ->
                    creds.apply {
                        assertEquals(type, scanResponse?.credentials?.get(index)?.type)
                        assertEquals(role, scanResponse?.credentials?.get(index)?.role)
                        assertEquals(host, scanResponse?.credentials?.get(index)?.host)
                        assertEquals(port, scanResponse?.credentials?.get(index)?.port)
                        assertEquals(user, scanResponse?.credentials?.get(index)?.user)
                        assertEquals(path, scanResponse?.credentials?.get(index)?.path)
                        assertEquals(stillValid, scanResponse?.credentials?.get(index)?.stillValid)
                    }
                }
                threats?.forEachIndexed { index, threat ->
                    threat.apply {
                        assertEquals(id, scanResponse?.threats?.get(index)?.id)
                        assertEquals(signature, scanResponse?.threats?.get(index)?.signature)
                        assertEquals(description, scanResponse?.threats?.get(index)?.description)
                        assertEquals(status, scanResponse?.threats?.get(index)?.status)
                        assertEquals(firstDetected, scanResponse?.threats?.get(index)?.firstDetected)
                        assertEquals(fixedOn, scanResponse?.threats?.get(index)?.fixedOn)
                        fixable?.apply {
                            assertEquals(fixer, scanResponse?.threats?.get(index)?.fixable?.fixer)
                            assertEquals(target, scanResponse?.threats?.get(index)?.fixable?.target)
                        }
                        extension?.apply {
                            assertEquals(type, scanResponse?.threats?.get(index)?.extension?.type)
                            assertEquals(slug, scanResponse?.threats?.get(index)?.extension?.slug)
                            assertEquals(name, scanResponse?.threats?.get(index)?.extension?.name)
                            assertEquals(version, scanResponse?.threats?.get(index)?.extension?.version)
                            assertEquals(isPremium, scanResponse?.threats?.get(index)?.extension?.isPremium)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `fetch scan state dispatches most recent status for idle state`() = test {
        val successResponseJson = UnitTestUtils.getStringFromResourceFile(javaClass, JP_COMPLETE_SCAN_IDLE_JSON)
        val scanResponse = getScanStateResponseFromJsonString(successResponseJson)
        initFetchScanState(scanResponse)

        val payload = scanRestClient.fetchScanState(site)

        with(payload) {
            assertNotNull(scanStateModel?.mostRecentStatus)
            assertEquals(scanStateModel?.state, State.IDLE)
        }
    }

    @Test
    fun `fetch scan state dispatches empty creds when server creds not setup for site with scan capability`() = test {
        val successResponseJson = UnitTestUtils.getStringFromResourceFile(
            javaClass,
            JP_SCAN_DAILY_SCAN_IDLE_WITH_THREAT_WITHOUT_SERVER_CREDS_JSON
        )
        val scanResponse = getScanStateResponseFromJsonString(successResponseJson)
        initFetchScanState(scanResponse)

        val payload = scanRestClient.fetchScanState(site)

        with(payload) {
            assertEquals(scanStateModel?.credentials, emptyList<Credentials>())
            assertEquals(scanStateModel?.state, State.IDLE)
        }
    }

    @Test
    fun `fetch scan state dispatches empty threats if no threats found for site with scan capability`() = test {
        val successResponseJson = UnitTestUtils.getStringFromResourceFile(javaClass, JP_COMPLETE_SCAN_IDLE_JSON)

        val scanResponse = getScanStateResponseFromJsonString(successResponseJson)
        initFetchScanState(scanResponse)

        val payload = scanRestClient.fetchScanState(site)

        with(payload) {
            assertEquals(scanStateModel?.threats, emptyList<Threat>())
            assertEquals(scanStateModel?.state, State.IDLE)
        }
    }

    @Test
    fun `fetch scan state dispatches current progress status for scanning state`() = test {
        val successResponseJson = UnitTestUtils.getStringFromResourceFile(javaClass, JP_COMPLETE_SCAN_SCANNING_JSON)
        val scanResponse = getScanStateResponseFromJsonString(successResponseJson)
        initFetchScanState(scanResponse)

        val payload = scanRestClient.fetchScanState(site)

        with(payload) {
            assertNotNull(scanStateModel?.currentStatus)
            assertEquals(scanStateModel?.state, State.SCANNING)
        }
    }

    @Test
    fun `fetch scan state dispatches reason, null threats and creds for scan unavailable state`() = test {
        val successResponseJson = UnitTestUtils.getStringFromResourceFile(
            javaClass,
            JP_BACKUP_DAILY_SCAN_UNAVAILABLE_JSON
        )
        val scanResponse = getScanStateResponseFromJsonString(successResponseJson)
        initFetchScanState(scanResponse)

        val payload = scanRestClient.fetchScanState(site)

        with(payload) {
            assertNull(scanStateModel?.credentials)
            assertNull(scanStateModel?.threats)
            assertNotNull(scanStateModel?.reason)
            assertEquals(scanStateModel?.state, State.UNAVAILABLE)
        }
    }

    @Test
    fun `fetch scan state dispatches generic error on failure`() = test {
        initFetchScanState(error = WPComGsonNetworkError(BaseNetworkError(NETWORK_ERROR)))

        val payload = scanRestClient.fetchScanState(site)

        assertEmittedScanStatusError(payload, ScanStateErrorType.GENERIC_ERROR)
    }

    @Test
    fun `fetch scan state dispatches error on wrong state`() = test {
        val successResponseJson = UnitTestUtils.getStringFromResourceFile(javaClass, JP_COMPLETE_SCAN_IDLE_JSON)
        val scanResponse = getScanStateResponseFromJsonString(successResponseJson)

        initFetchScanState(scanResponse?.copy(state = "wrong"))

        val payload = scanRestClient.fetchScanState(site)

        assertEmittedScanStatusError(payload, ScanStateErrorType.INVALID_RESPONSE)
    }

    private fun assertEmittedScanStatusError(payload: FetchedScanStatePayload, errorType: ScanStateErrorType) {
        with(payload) {
            assertEquals(site, this@ScanRestClientTest.site)
            assertTrue(isError)
            assertEquals(errorType, error.type)
        }
    }

    private fun getScanStateResponseFromJsonString(json: String): ScanStateResponse? {
        val responseType = object : TypeToken<ScanStateResponse>() {}.type
        return Gson().fromJson(json, responseType) as? ScanStateResponse
    }

    private suspend fun initFetchScanState(
        data: ScanStateResponse? = null,
        error: WPComGsonNetworkError? = null
    ): Response<ScanStateResponse> {
        val nonNullData = data ?: mock()
        val response = if (error != null) Response.Error(error) else Success(nonNullData)
        whenever(
            wpComGsonRequestBuilder.syncGetRequest(
                eq(scanRestClient),
                urlCaptor.capture(),
                eq(mapOf()),
                eq(ScanStateResponse::class.java),
                eq(false),
                any(),
                eq(false)
            )
        ).thenReturn(response)
        whenever(site.siteId).thenReturn(siteId)
        return response
    }

    companion object {
        private const val JP_COMPLETE_SCAN_IDLE_JSON = "wp/jetpack/scan/jp-complete-scan-idle.json"
        private const val JP_COMPLETE_SCAN_SCANNING_JSON = "wp/jetpack/scan/jp-complete-scan-scanning.json"
        private const val JP_BACKUP_DAILY_SCAN_UNAVAILABLE_JSON =
            "wp/jetpack/scan/jp-backup-daily-scan-unavailable.json"
        private const val JP_SCAN_DAILY_SCAN_IDLE_WITH_THREATS_JSON =
            "wp/jetpack/scan/jp-scan-daily-scan-idle-with-threat.json"
        private const val JP_SCAN_DAILY_SCAN_IDLE_WITH_THREAT_WITHOUT_SERVER_CREDS_JSON =
            "wp/jetpack/scan/jp-scan-daily-scan-idle-with-threat-without-server-creds.json"
    }
}
