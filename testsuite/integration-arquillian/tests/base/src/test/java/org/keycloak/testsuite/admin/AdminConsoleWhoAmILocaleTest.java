package org.keycloak.testsuite.admin;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.Response;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.Constants;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.testsuite.AbstractKeycloakTest;
import org.keycloak.testsuite.auth.page.AuthRealm;
import org.keycloak.testsuite.broker.util.SimpleHttpDefault;
import org.keycloak.testsuite.updaters.ClientAttributeUpdater;
import org.keycloak.testsuite.util.AdminClientUtil;
import org.keycloak.testsuite.util.RealmBuilder;
import org.keycloak.testsuite.util.UserBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.keycloak.models.Constants.ADMIN_CLI_CLIENT_ID;

public class AdminConsoleWhoAmILocaleTest extends AbstractKeycloakTest {

    private static final String REALM_I18N_OFF = "realm-i18n-off";
    private static final String REALM_I18N_ON = "realm-i18n-on";
    private static final String USER_WITHOUT_LOCALE = "user-without-locale";
    private static final String USER_WITH_LOCALE = "user-with-locale";
    private static final String USER_NO_ACCESS = "user-no-access";
    private static final String PASSWORD = "password";
    private static final String DEFAULT_LOCALE = "en";
    private static final String REALM_LOCALE = "no";
    private static final String USER_LOCALE = "de";
    private static final String EXTRA_LOCALE = "zh-CN";

    private CloseableHttpClient client;

    @Before
    public void createHttpClient() throws Exception {
        client = HttpClientBuilder.create().build();

        getCleanup().addCleanup(ClientAttributeUpdater.forClient(adminClient, "master", ADMIN_CLI_CLIENT_ID).setAttribute(Constants.SECURITY_ADMIN_CONSOLE_ATTR, "true").update());
        getCleanup().addCleanup(ClientAttributeUpdater.forClient(adminClient, REALM_I18N_OFF, ADMIN_CLI_CLIENT_ID).setAttribute(Constants.SECURITY_ADMIN_CONSOLE_ATTR, "true").update());
        getCleanup().addCleanup(ClientAttributeUpdater.forClient(adminClient, REALM_I18N_ON, ADMIN_CLI_CLIENT_ID).setAttribute(Constants.SECURITY_ADMIN_CONSOLE_ATTR, "true").update());
    }

    @After
    public void closeHttpClient() {
        try {
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        RealmBuilder realm = RealmBuilder.create()
            .name(REALM_I18N_OFF)
            .internationalizationEnabled(false);
        realm.user(UserBuilder.create()
            .username(USER_WITHOUT_LOCALE)
            .password(PASSWORD)
            .role(Constants.REALM_MANAGEMENT_CLIENT_ID, AdminRoles.REALM_ADMIN));
        realm.user(UserBuilder.create()
            .username(USER_WITH_LOCALE)
            .password(PASSWORD)
            .addAttribute("locale", USER_LOCALE)
            .role(Constants.REALM_MANAGEMENT_CLIENT_ID, AdminRoles.REALM_ADMIN));
        testRealms.add(realm.build());
        
        realm = RealmBuilder.create()
            .name(REALM_I18N_ON)
            .internationalizationEnabled(true)
            .supportedLocales(new HashSet<>(Arrays.asList(REALM_LOCALE, USER_LOCALE, EXTRA_LOCALE)))
            .defaultLocale(REALM_LOCALE);
        realm.user(UserBuilder.create()
            .username(USER_WITHOUT_LOCALE)
            .password(PASSWORD)
            .role(Constants.REALM_MANAGEMENT_CLIENT_ID, AdminRoles.REALM_ADMIN));
        realm.user(UserBuilder.create()
            .username(USER_WITH_LOCALE)
            .password(PASSWORD)
            .addAttribute("locale", USER_LOCALE)
            .role(Constants.REALM_MANAGEMENT_CLIENT_ID, AdminRoles.REALM_ADMIN));
        realm.user(UserBuilder.create()
            .username(USER_NO_ACCESS)
            .password(PASSWORD)
            .addAttribute("locale", USER_LOCALE));
        testRealms.add(realm.build());
    }

    private org.keycloak.testsuite.util.oauth.AccessTokenResponse accessToken(String realmName, String username, String password) throws Exception {
        return oauth.realm(realmName).client(ADMIN_CLI_CLIENT_ID).doPasswordGrantRequest(username, password);
    }

    private String whoAmiUrl(String realmName) {
        return whoAmiUrl(realmName, null);
    }

    private String whoAmiUrl(String realmName, String currentRealm) {
        StringBuilder sb = new StringBuilder()
                .append(suiteContext.getAuthServerInfo().getContextRoot().toString())
                .append("/auth/admin/")
                .append(realmName)
                .append("/console/whoami");
        if (currentRealm != null) {
             sb.append("?currentRealm=").append(currentRealm);
        }
        return sb.toString();
    }

    private void checkRealmAccess(String realm, JsonNode whoAmI) {
        Assert.assertNotNull(whoAmI.get("realm_access"));
        Assert.assertNotNull(whoAmI.get("realm_access").get(realm));
        Assert.assertTrue(whoAmI.get("realm_access").get(realm).isArray());
        Assert.assertTrue(whoAmI.get("realm_access").get(realm).size() > 0);
    }

    @Test
    public void testLocaleRealmI18nDisabledUserWithoutLocale() throws Exception {
        org.keycloak.testsuite.util.oauth.AccessTokenResponse response = oauth.realm(REALM_I18N_OFF).client(ADMIN_CLI_CLIENT_ID).doPasswordGrantRequest(USER_WITHOUT_LOCALE, PASSWORD);
        JsonNode whoAmI = SimpleHttpDefault
            .doGet(whoAmiUrl(REALM_I18N_OFF), client)
            .header("Accept", "application/json")
            .auth(response.getAccessToken())
            .asJson();
        Assert.assertEquals(REALM_I18N_OFF, whoAmI.get("realm").asText());
        Assert.assertEquals(DEFAULT_LOCALE, whoAmI.get("locale").asText());
        checkRealmAccess(REALM_I18N_OFF, whoAmI);
        oauth.doLogout(response.getRefreshToken());
    }

    @Test
    public void testLocaleRealmI18nDisabledUserWithLocale() throws Exception {
        org.keycloak.testsuite.util.oauth.AccessTokenResponse response = accessToken(REALM_I18N_OFF, USER_WITH_LOCALE, PASSWORD);
        JsonNode whoAmI = SimpleHttpDefault
            .doGet(whoAmiUrl(REALM_I18N_OFF), client)
            .header("Accept", "application/json")
            .auth(response.getAccessToken())
            .asJson();
        Assert.assertEquals(REALM_I18N_OFF, whoAmI.get("realm").asText());
        Assert.assertEquals(DEFAULT_LOCALE, whoAmI.get("locale").asText());
        checkRealmAccess(REALM_I18N_OFF, whoAmI);
        oauth.doLogout(response.getRefreshToken());
    }

    @Test
    public void testLocaleRealmI18nEnabledUserWithoutLocale() throws Exception {
        org.keycloak.testsuite.util.oauth.AccessTokenResponse response = accessToken(REALM_I18N_ON, USER_WITHOUT_LOCALE, PASSWORD);
        JsonNode whoAmI = SimpleHttpDefault
            .doGet(whoAmiUrl(REALM_I18N_ON), client)
            .header("Accept", "application/json")
            .auth(response.getAccessToken())
            .asJson();
        Assert.assertEquals(REALM_I18N_ON, whoAmI.get("realm").asText());
        Assert.assertEquals(REALM_LOCALE, whoAmI.get("locale").asText());
        checkRealmAccess(REALM_I18N_ON, whoAmI);
        oauth.doLogout(response.getRefreshToken());
    }

    @Test
    public void testLocaleRealmI18nEnabledUserWithLocale() throws Exception {
        org.keycloak.testsuite.util.oauth.AccessTokenResponse response = accessToken(REALM_I18N_ON, USER_WITH_LOCALE, PASSWORD);
        JsonNode whoAmI = SimpleHttpDefault
            .doGet(whoAmiUrl(REALM_I18N_ON), client)
            .header("Accept", "application/json")
            .auth(response.getAccessToken())
            .asJson();
        Assert.assertEquals(REALM_I18N_ON, whoAmI.get("realm").asText());
        Assert.assertEquals(USER_LOCALE, whoAmI.get("locale").asText());
        checkRealmAccess(REALM_I18N_ON, whoAmI);
        oauth.doLogout(response.getRefreshToken());
    }

    @Test
    public void testLocaleRealmI18nEnabledAcceptLanguageHeader() throws Exception {
        org.keycloak.testsuite.util.oauth.AccessTokenResponse response = accessToken(REALM_I18N_ON, USER_WITHOUT_LOCALE, PASSWORD);
        JsonNode whoAmI = SimpleHttpDefault
            .doGet(whoAmiUrl(REALM_I18N_ON), client)
            .header("Accept", "application/json")
            .auth(response.getAccessToken())
            .header("Accept-Language", EXTRA_LOCALE)
            .asJson();
        Assert.assertEquals(REALM_I18N_ON, whoAmI.get("realm").asText());
        Assert.assertEquals(EXTRA_LOCALE, whoAmI.get("locale").asText());
        checkRealmAccess(REALM_I18N_ON, whoAmI);
        oauth.doLogout(response.getRefreshToken());
    }

    @Test
    public void testLocaleRealmI18nEnabledKeycloakLocaleCookie() throws Exception {
        org.keycloak.testsuite.util.oauth.AccessTokenResponse response = accessToken(REALM_I18N_ON, USER_WITHOUT_LOCALE, PASSWORD);
        JsonNode whoAmI = SimpleHttpDefault
            .doGet(whoAmiUrl(REALM_I18N_ON), client)
            .header("Accept", "application/json")
            .auth(response.getAccessToken())
            .header("Cookie", "KEYCLOAK_LOCALE=" + EXTRA_LOCALE)
            .asJson();
        Assert.assertEquals(REALM_I18N_ON, whoAmI.get("realm").asText());
        Assert.assertEquals(EXTRA_LOCALE, whoAmI.get("locale").asText());
        checkRealmAccess(REALM_I18N_ON, whoAmI);
        oauth.doLogout(response.getRefreshToken());
    }

    @Test
    public void testMasterRealm() throws Exception {
        org.keycloak.testsuite.util.oauth.AccessTokenResponse response = accessToken(AuthRealm.MASTER, AuthRealm.ADMIN, AuthRealm.ADMIN);
        JsonNode whoAmI = SimpleHttpDefault
            .doGet(whoAmiUrl(AuthRealm.MASTER), client)
            .header("Accept", "application/json")
            .auth(response.getAccessToken())
            .asJson();
        Assert.assertEquals(AuthRealm.MASTER, whoAmI.get("realm").asText());
        Assert.assertEquals(DEFAULT_LOCALE, whoAmI.get("locale").asText());
        checkRealmAccess(AuthRealm.MASTER, whoAmI);
        oauth.doLogout(response.getRefreshToken());
    }

    @Test
    public void testMasterRealmCurrentRealm() throws Exception {
        org.keycloak.testsuite.util.oauth.AccessTokenResponse response = accessToken(AuthRealm.MASTER, AuthRealm.ADMIN, AuthRealm.ADMIN);
        JsonNode whoAmI = SimpleHttpDefault
            .doGet(whoAmiUrl(AuthRealm.MASTER, REALM_I18N_ON), client)
            .header("Accept", "application/json")
            .auth(response.getAccessToken())
            .asJson();
        Assert.assertEquals(AuthRealm.MASTER, whoAmI.get("realm").asText());
        Assert.assertEquals(DEFAULT_LOCALE, whoAmI.get("locale").asText());
        checkRealmAccess(REALM_I18N_ON, whoAmI);
        oauth.doLogout(response.getRefreshToken());
    }

    @Test
    public void testLocaleRealmNoToken() throws Exception {
        try (SimpleHttp.Response response = SimpleHttpDefault
                .doGet(whoAmiUrl(REALM_I18N_ON), client)
                .header("Accept", "application/json")
                .asResponse()) {
            Assert.assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        }
    }

    @Test
    public void testLocaleRealmUserNoAccess() throws Exception {
        org.keycloak.testsuite.util.oauth.AccessTokenResponse response = accessToken(REALM_I18N_ON, USER_NO_ACCESS, PASSWORD);
        try (SimpleHttp.Response res = SimpleHttpDefault
                .doGet(whoAmiUrl(REALM_I18N_ON), client)
                .header("Accept", "application/json")
                .auth(response.getAccessToken())
                .asResponse()) {
            Assert.assertEquals(Response.Status.FORBIDDEN.getStatusCode(), res.getStatus());
        }
        oauth.doLogout(response.getRefreshToken());
    }

    @Test
    public void testLocaleRealmTokenForOtherClient() throws Exception {
        try (var c = ClientAttributeUpdater.forClient(adminClient, REALM_I18N_ON, ADMIN_CLI_CLIENT_ID).setAttribute(Constants.SECURITY_ADMIN_CONSOLE_ATTR, null).update();
          Keycloak adminCliClient = AdminClientUtil.createAdminClient(true, REALM_I18N_ON,
                USER_WITH_LOCALE, PASSWORD, Constants.ADMIN_CLI_CLIENT_ID, null)) {
            AccessTokenResponse accessToken = adminCliClient.tokenManager().getAccessToken();
            Assert.assertNotNull(accessToken);
            String token = accessToken.getToken();
            try (SimpleHttp.Response response = SimpleHttpDefault
                    .doGet(whoAmiUrl(REALM_I18N_ON), client)
                    .header("Accept", "application/json")
                    .auth(token)
                    .asResponse()) {
                Assert.assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
            }
        }
    }

    @Test
    public void testLocaleRealmTokenForOtherClientButAllowed() throws Exception {
        try (ClientAttributeUpdater updater = ClientAttributeUpdater.forClient(adminClient, REALM_I18N_ON, Constants.ADMIN_CLI_CLIENT_ID)
                .setAttribute(Constants.SECURITY_ADMIN_CONSOLE_ATTR, Boolean.TRUE.toString())
                .update();
                Keycloak adminCliClient = AdminClientUtil.createAdminClient(true, REALM_I18N_ON,
                USER_WITH_LOCALE, PASSWORD, Constants.ADMIN_CLI_CLIENT_ID, null)) {
            AccessTokenResponse accessToken = adminCliClient.tokenManager().getAccessToken();
            Assert.assertNotNull(accessToken);
            String token = accessToken.getToken();
            JsonNode whoAmI = SimpleHttpDefault
                    .doGet(whoAmiUrl(REALM_I18N_ON), client)
                    .header("Accept", "application/json")
                    .auth(token)
                    .asJson();
            Assert.assertEquals(REALM_I18N_ON, whoAmI.get("realm").asText());
            Assert.assertEquals(USER_LOCALE, whoAmI.get("locale").asText());
            checkRealmAccess(REALM_I18N_ON, whoAmI);
        }
    }
}
