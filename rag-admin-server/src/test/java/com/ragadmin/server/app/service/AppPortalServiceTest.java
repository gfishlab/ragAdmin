package com.ragadmin.server.app.service;

import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.LoginRequest;
import com.ragadmin.server.auth.dto.LoginResponse;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.infra.search.WebSearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppPortalServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private WebSearchProvider webSearchProvider;

    private AppPortalService appPortalService;

    @BeforeEach
    void setUp() {
        appPortalService = new AppPortalService();
        ReflectionTestUtils.setField(appPortalService, "authService", authService);
    }

    @Test
    void shouldExposeWebSearchAvailabilityAsFalseWhenNoRealProviderConfigured() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("app-user");
        request.setPassword("App@123456");
        CurrentUserResponse currentUser = new CurrentUserResponse()
                .setId(2L)
                .setUsername("app-user");
        LoginResponse loginResponse = new LoginResponse()
                .setAccessToken("access-token")
                .setRefreshToken("refresh-token")
                .setUser(currentUser);

        when(authService.loginForAppPortal(request)).thenReturn(loginResponse);

        LoginResponse response = appPortalService.login(request);

        assertFalse(response.getUser().isWebSearchAvailable());
    }

    @Test
    void shouldExposeWebSearchAvailabilityAsTrueWhenProviderIsAvailable() {
        CurrentUserResponse currentUser = new CurrentUserResponse()
                .setId(3L)
                .setUsername("app-user");

        when(authService.getCurrentUserForAppPortal(3L)).thenReturn(currentUser);
        when(webSearchProvider.isAvailable()).thenReturn(true);
        ReflectionTestUtils.setField(appPortalService, "webSearchProvider", webSearchProvider);

        CurrentUserResponse response = appPortalService.getCurrentUser(3L);

        assertTrue(response.isWebSearchAvailable());
    }
}
