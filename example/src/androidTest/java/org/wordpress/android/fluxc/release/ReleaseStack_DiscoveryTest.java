package org.wordpress.android.fluxc.release;

import org.greenrobot.eventbus.Subscribe;
import org.wordpress.android.fluxc.TestUtils;
import org.wordpress.android.fluxc.example.BuildConfig;
import org.wordpress.android.fluxc.generated.AuthenticationActionBuilder;
import org.wordpress.android.fluxc.generated.SiteActionBuilder;
import org.wordpress.android.fluxc.network.HTTPAuthManager;
import org.wordpress.android.fluxc.network.MemorizingTrustManager;
import org.wordpress.android.fluxc.network.discovery.SelfHostedEndpointFinder.DiscoveryError;
import org.wordpress.android.fluxc.store.AccountStore;
import org.wordpress.android.fluxc.store.AccountStore.OnAuthenticationChanged;
import org.wordpress.android.fluxc.store.SiteStore;
import org.wordpress.android.fluxc.store.SiteStore.OnSiteChanged;
import org.wordpress.android.fluxc.store.SiteStore.RefreshSitesXMLRPCPayload;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.UrlUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Tests with real credentials on real servers using the full release stack (no mock)
 */
public class ReleaseStack_DiscoveryTest extends ReleaseStack_Base {
    @Inject SiteStore mSiteStore;
    @Inject AccountStore mAccountStore;
    @Inject HTTPAuthManager mHTTPAuthManager;
    @Inject MemorizingTrustManager mMemorizingTrustManager;

    enum TestEvents {
        NONE,
        DISCOVERY_SUCCEEDED,
        INVALID_URL_ERROR,
        NO_SITE_ERROR,
        WORDPRESS_COM_SITE,
        ERRONEOUS_SSL_CERTIFICATE,
        HTTP_AUTH_REQUIRED,
        XMLRPC_BLOCKED,
        XMLRPC_FORBIDDEN,
        MISSING_XMLRPC_METHOD,
        SITE_CHANGED,
        SITE_REMOVED
    }

    private TestEvents mNextEvent;

    private RefreshSitesXMLRPCPayload mPayload;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReleaseStackAppComponent.inject(this);
        // Register
        init();
        // Reset expected test event
        mNextEvent = TestEvents.NONE;
    }

    public void testNoUrlFetchSites() throws InterruptedException {
        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = "";
        mPayload.username = BuildConfig.TEST_WPORG_USERNAME_SH_SIMPLE;
        mPayload.password = BuildConfig.TEST_WPORG_PASSWORD_SH_SIMPLE;

        mNextEvent = TestEvents.INVALID_URL_ERROR;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onChanged event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public void testInvalidUrlFetchSites() throws InterruptedException {
        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = "notaurl&*@";
        mPayload.username = BuildConfig.TEST_WPORG_USERNAME_SH_SIMPLE;
        mPayload.password = BuildConfig.TEST_WPORG_PASSWORD_SH_SIMPLE;

        mNextEvent = TestEvents.NO_SITE_ERROR;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onChanged event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public void testNonWordPressFetchSites() throws InterruptedException {
        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = "example.com";
        mPayload.username = BuildConfig.TEST_WPORG_USERNAME_SH_SIMPLE;
        mPayload.password = BuildConfig.TEST_WPORG_PASSWORD_SH_SIMPLE;

        mNextEvent = TestEvents.NO_SITE_ERROR;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onChanged event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public void testWPComUrlFetchSites() throws InterruptedException {
        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = "mysite.wordpress.com";
        mPayload.username = BuildConfig.TEST_WPCOM_USERNAME_TEST1;
        mPayload.password = BuildConfig.TEST_WPCOM_PASSWORD_TEST1;

        mNextEvent = TestEvents.WORDPRESS_COM_SITE;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onChanged event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public void testXMLRPCSimpleFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_SIMPLE,
                BuildConfig.TEST_WPORG_USERNAME_SH_SIMPLE,
                BuildConfig.TEST_WPORG_PASSWORD_SH_SIMPLE);
    }

    public void testXMLRPCSimpleHTTPSFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_VALID_SSL,
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_SSL,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_SSL);
    }

    public void testXMLRPCHTTPToHTTPRedirectFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTP_TO_HTTP_REDIRECT,
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTP_TO_HTTP_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTP_TO_HTTP_REDIRECT);
    }

    public void testXMLRPCHTTPToHTTPSRedirectFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTP_TO_HTTPS_REDIRECT,
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTP_TO_HTTPS_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTP_TO_HTTPS_REDIRECT);
    }

    public void testXMLRPCHTTPSToHTTPSRedirectFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTPS_TO_HTTPS_REDIRECT,
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTPS_TO_HTTPS_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTPS_TO_HTTPS_REDIRECT);
    }

    public void testXMLRPCHTTPSToHTTPRedirectFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTPS_TO_HTTP_REDIRECT,
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTPS_TO_HTTP_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTPS_TO_HTTP_REDIRECT);
    }

    public void testXMLRPCHTTPToHTTPSSameDomainRedirectFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_VALID_SSL_REDIRECT,
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_SSL_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_SSL_REDIRECT);
    }

    public void testXMLRPCSelfSignedSSLFetchSites() throws InterruptedException {
        checkSelfHostedSelfSignedSSLFetchForSite(BuildConfig.TEST_WPORG_URL_SH_SELFSIGNED_SSL,
                BuildConfig.TEST_WPORG_USERNAME_SH_SELFSIGNED_SSL,
                BuildConfig.TEST_WPORG_PASSWORD_SH_SELFSIGNED_SSL);
    }

    public void testXMLRPCHTTPAuthFetchSites() throws InterruptedException {
        checkSelfHostedHTTPAuthFetchForSite(BuildConfig.TEST_WPORG_URL_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_USERNAME_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_PASSWORD_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_HTTPAUTH_USERNAME_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_HTTPAUTH_PASSWORD_SH_HTTPAUTH);
    }

    public void testXMLRPCHTTPToHTTPSRedirectWithEndpointSameDomainFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_VALID_SSL_REDIRECT_ENDPOINT,
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_SSL_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_SSL_REDIRECT);
    }

    // No protocol in URL tests

    public void testXMLRPCSimpleNoProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(UrlUtils.removeScheme(BuildConfig.TEST_WPORG_URL_SH_SIMPLE),
                BuildConfig.TEST_WPORG_USERNAME_SH_SIMPLE,
                BuildConfig.TEST_WPORG_PASSWORD_SH_SIMPLE);
    }

    public void testXMLRPCSimpleHTTPSNoProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(UrlUtils.removeScheme(BuildConfig.TEST_WPORG_URL_SH_VALID_SSL),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_SSL,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_SSL);
    }

    public void testXMLRPCHTTPToHTTPNoProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(
                UrlUtils.removeScheme(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTP_TO_HTTP_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTP_TO_HTTP_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTP_TO_HTTP_REDIRECT);
    }

    public void testXMLRPCHTTPToHTTPSNoProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(
                UrlUtils.removeScheme(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTP_TO_HTTPS_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTP_TO_HTTPS_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTP_TO_HTTPS_REDIRECT);
    }

    public void testXMLRPCHTTPSToHTTPSNoProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(
                UrlUtils.removeScheme(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTPS_TO_HTTPS_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTPS_TO_HTTPS_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTPS_TO_HTTPS_REDIRECT);
    }

    public void testXMLRPCHTTPSToHTTPNoProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(
                UrlUtils.removeScheme(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTPS_TO_HTTP_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTPS_TO_HTTP_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTPS_TO_HTTP_REDIRECT);
    }

    public void testXMLRPCHTTPToHTTPSSameDomainNoProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(UrlUtils.removeScheme(BuildConfig.TEST_WPORG_URL_SH_VALID_SSL_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_SSL_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_SSL_REDIRECT);
    }

    public void testXMLRPCSelfSignedSSLNoProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSelfSignedSSLFetchForSite(UrlUtils.removeScheme(BuildConfig.TEST_WPORG_URL_SH_SELFSIGNED_SSL),
                BuildConfig.TEST_WPORG_USERNAME_SH_SELFSIGNED_SSL,
                BuildConfig.TEST_WPORG_PASSWORD_SH_SELFSIGNED_SSL);
    }

    public void testXMLRPCHTTPAuthNoProtocolFetchSites() throws InterruptedException {
        checkSelfHostedHTTPAuthFetchForSite(UrlUtils.removeScheme(BuildConfig.TEST_WPORG_URL_SH_HTTPAUTH),
                BuildConfig.TEST_WPORG_USERNAME_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_PASSWORD_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_HTTPAUTH_USERNAME_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_HTTPAUTH_PASSWORD_SH_HTTPAUTH);
    }

    // Bad protocol tests

    public void testXMLRPCSimpleBadProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(addBadProtocolToUrl(BuildConfig.TEST_WPORG_URL_SH_SIMPLE),
                BuildConfig.TEST_WPORG_USERNAME_SH_SIMPLE,
                BuildConfig.TEST_WPORG_PASSWORD_SH_SIMPLE);
    }

    public void testXMLRPCSimpleHTTPSBadProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(addBadProtocolToUrl(BuildConfig.TEST_WPORG_URL_SH_VALID_SSL),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_SSL,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_SSL);
    }

    public void testXMLRPCHTTPToHTTPBadProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(
                addBadProtocolToUrl(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTP_TO_HTTP_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTP_TO_HTTP_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTP_TO_HTTP_REDIRECT);
    }

    public void testXMLRPCHTTPToHTTPSBadProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(
                addBadProtocolToUrl(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTP_TO_HTTPS_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTP_TO_HTTPS_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTP_TO_HTTPS_REDIRECT);
    }

    public void testXMLRPCHTTPSToHTTPSBadProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(
                addBadProtocolToUrl(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTPS_TO_HTTPS_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTPS_TO_HTTPS_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTPS_TO_HTTPS_REDIRECT);
    }

    public void testXMLRPCHTTPSToHTTPBadProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(
                addBadProtocolToUrl(BuildConfig.TEST_WPORG_URL_SH_VALID_HTTPS_TO_HTTP_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_HTTPS_TO_HTTP_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_HTTPS_TO_HTTP_REDIRECT);
    }

    public void testXMLRPCHTTPToHTTPSSameDomainBadProtocolRedirectFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(addBadProtocolToUrl(BuildConfig.TEST_WPORG_URL_SH_VALID_SSL_REDIRECT),
                BuildConfig.TEST_WPORG_USERNAME_SH_VALID_SSL_REDIRECT,
                BuildConfig.TEST_WPORG_PASSWORD_SH_VALID_SSL_REDIRECT);
    }

    public void testXMLRPCSelfSignedSSLBadProtocolFetchSites() throws InterruptedException {
        checkSelfHostedSelfSignedSSLFetchForSite(addBadProtocolToUrl(BuildConfig.TEST_WPORG_URL_SH_SELFSIGNED_SSL),
                BuildConfig.TEST_WPORG_USERNAME_SH_SELFSIGNED_SSL,
                BuildConfig.TEST_WPORG_PASSWORD_SH_SELFSIGNED_SSL);
    }

    public void testXMLRPCHTTPAuthBadProtocolFetchSites() throws InterruptedException {
        checkSelfHostedHTTPAuthFetchForSite(addBadProtocolToUrl(BuildConfig.TEST_WPORG_URL_SH_HTTPAUTH),
                BuildConfig.TEST_WPORG_USERNAME_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_PASSWORD_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_HTTPAUTH_USERNAME_SH_HTTPAUTH,
                BuildConfig.TEST_WPORG_HTTPAUTH_PASSWORD_SH_HTTPAUTH);
    }

    public void testXMLRPCRsdFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_RSD,
                BuildConfig.TEST_WPORG_USERNAME_SH_RSD,
                BuildConfig.TEST_WPORG_PASSWORD_SH_RSD);
    }

    public void testXMLRPCNoRsdFetchSites() throws InterruptedException {
        checkSelfHostedSimpleFetchForSite(BuildConfig.TEST_WPORG_URL_SH_NO_RSD,
                BuildConfig.TEST_WPORG_USERNAME_SH_NO_RSD,
                BuildConfig.TEST_WPORG_PASSWORD_SH_NO_RSD);
    }

    public void testXMLRPCBlockedDiscovery() throws InterruptedException {
        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = BuildConfig.TEST_WPORG_URL_SH_BLOCKED;
        mPayload.username = BuildConfig.TEST_WPORG_USERNAME_SH_BLOCKED;
        mPayload.password = BuildConfig.TEST_WPORG_PASSWORD_SH_BLOCKED;

        mNextEvent = TestEvents.XMLRPC_BLOCKED;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onChanged event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public void testXMLRPCForbiddenDiscovery() throws InterruptedException {
        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = BuildConfig.TEST_WPORG_URL_SH_FORBIDDEN;
        mPayload.username = BuildConfig.TEST_WPORG_USERNAME_SH_FORBIDDEN;
        mPayload.password = BuildConfig.TEST_WPORG_PASSWORD_SH_FORBIDDEN;

        mNextEvent = TestEvents.XMLRPC_FORBIDDEN;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onChanged event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    public void testXMLRPCMissingMethodDiscovery() throws InterruptedException {
        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = BuildConfig.TEST_WPORG_URL_SH_MISSING_METHODS;
        mPayload.username = BuildConfig.TEST_WPORG_USERNAME_SH_MISSING_METHODS;
        mPayload.password = BuildConfig.TEST_WPORG_PASSWORD_SH_MISSING_METHODS;

        mNextEvent = TestEvents.MISSING_XMLRPC_METHOD;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onChanged event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void checkSelfHostedSimpleFetchForSite(String url, String username, String password)
            throws InterruptedException {
        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = url;
        mPayload.username = username;
        mPayload.password = password;

        mNextEvent = TestEvents.DISCOVERY_SUCCEEDED;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onChanged event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        fetchSites();
    }

    private void checkSelfHostedSelfSignedSSLFetchForSite(String url, String username, String password)
            throws InterruptedException {
        mMemorizingTrustManager.clearLocalTrustStore();

        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = url;
        mPayload.username = username;
        mPayload.password = password;

        mNextEvent = TestEvents.ERRONEOUS_SSL_CERTIFICATE;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onAuthenticationChanged error event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // Add an exception for the last certificate
        mMemorizingTrustManager.storeLastFailure();

        // Retry endpoint discovery, and attempt to fetch sites
        mNextEvent = TestEvents.DISCOVERY_SUCCEEDED;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onAuthenticationChanged error event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        fetchSites();

        mMemorizingTrustManager.clearLocalTrustStore();
    }

    private void checkSelfHostedHTTPAuthFetchForSite(String url, String username, String password, String authUsername,
                                                     String authPassword) throws InterruptedException {
        mPayload = new RefreshSitesXMLRPCPayload();
        mPayload.url = url;
        mPayload.username = username;
        mPayload.password = password;

        mNextEvent = TestEvents.HTTP_AUTH_REQUIRED;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onAuthenticationChanged error event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // Set known HTTP Auth credentials
        mHTTPAuthManager.addHTTPAuthCredentials(authUsername, authPassword, mPayload.url, null);

        // Retry endpoint discovery, and attempt to fetch sites
        mNextEvent = TestEvents.DISCOVERY_SUCCEEDED;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(AuthenticationActionBuilder.newDiscoverEndpointAction(mPayload));

        // Wait for a network response / onChanged event
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        fetchSites();
    }

    private void fetchSites() throws InterruptedException {
        mNextEvent = TestEvents.SITE_CHANGED;
        mCountDownLatch = new CountDownLatch(1);

        mDispatcher.dispatch(SiteActionBuilder.newFetchSitesXmlRpcAction(mPayload));

        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private static String addBadProtocolToUrl(String url) {
        return "hppt://" + UrlUtils.removeScheme(url);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onSiteChanged(OnSiteChanged event) {
        AppLog.i(T.TESTS, "site count " + mSiteStore.getSitesCount());
        if (event.isError()) {
            throw new AssertionError("Unexpected error occurred with type " + event.error.type);
        }
        assertTrue(mSiteStore.hasSite());
        assertTrue(mSiteStore.hasSelfHostedSite());
        assertEquals(TestEvents.SITE_CHANGED, mNextEvent);
        mCountDownLatch.countDown();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onSiteRemoved(SiteStore.OnSiteRemoved event) {
        AppLog.i(T.TESTS, "site count " + mSiteStore.getSitesCount());
        if (event.isError()) {
            throw new AssertionError("Unexpected error occurred with type " + event.error.type);
        }
        assertFalse(mSiteStore.hasSite());
        assertFalse(mSiteStore.hasSelfHostedSite());
        assertEquals(TestEvents.SITE_REMOVED, mNextEvent);
        mCountDownLatch.countDown();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onAuthenticationChanged(OnAuthenticationChanged event) {
        throw new AssertionError("OnAuthenticationChanged called - that's not supposed to happen for discovery");
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onDiscoveryResponse(AccountStore.OnDiscoveryResponse event) {
        if (event.isError()) {
            // ERROR :(
            AppLog.i(T.API, "Discovery error: " + event.error);
            if (event.error == DiscoveryError.INVALID_URL) {
                assertEquals(TestEvents.INVALID_URL_ERROR, mNextEvent);
            } else if (event.error == DiscoveryError.NO_SITE_ERROR) {
                assertEquals(TestEvents.NO_SITE_ERROR, mNextEvent);
            } else if (event.error == DiscoveryError.WORDPRESS_COM_SITE) {
                assertEquals(TestEvents.WORDPRESS_COM_SITE, mNextEvent);
            } else if (event.error == DiscoveryError.HTTP_AUTH_REQUIRED) {
                assertEquals(TestEvents.HTTP_AUTH_REQUIRED, mNextEvent);
            } else if (event.error == DiscoveryError.ERRONEOUS_SSL_CERTIFICATE) {
                assertEquals(TestEvents.ERRONEOUS_SSL_CERTIFICATE, mNextEvent);
            } else if (event.error == DiscoveryError.XMLRPC_BLOCKED) {
                assertEquals(TestEvents.XMLRPC_BLOCKED, mNextEvent);
            } else if (event.error == DiscoveryError.XMLRPC_FORBIDDEN) {
                assertEquals(TestEvents.XMLRPC_FORBIDDEN, mNextEvent);
            } else if (event.error == DiscoveryError.MISSING_XMLRPC_METHOD) {
                assertEquals(TestEvents.MISSING_XMLRPC_METHOD, mNextEvent);
            } else {
                throw new AssertionError("Didn't get the correct error, expected: " + mNextEvent + ", and got: "
                        + event.error);
            }
            mPayload.url = event.failedEndpoint;
        } else {
            // SUCCESS :)
            AppLog.i(T.API, "Discovery succeeded, endpoint: " + event.xmlRpcEndpoint);
            assertEquals(TestEvents.DISCOVERY_SUCCEEDED, mNextEvent);
            mPayload.url = event.xmlRpcEndpoint;
            mCountDownLatch.countDown();
        }
        mCountDownLatch.countDown();
    }
}
