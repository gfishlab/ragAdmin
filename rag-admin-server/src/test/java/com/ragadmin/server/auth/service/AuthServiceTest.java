package com.ragadmin.server.auth.service;

import com.ragadmin.server.auth.config.AuthProperties;
import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.LoginRequest;
import com.ragadmin.server.auth.dto.LoginResponse;
import com.ragadmin.server.auth.dto.RefreshTokenResponse;
import com.ragadmin.server.auth.entity.SysUserEntity;
import com.ragadmin.server.auth.mapper.AuthUserStructMapper;
import com.ragadmin.server.auth.mapper.SysRoleMapper;
import com.ragadmin.server.auth.mapper.SysUserMapper;
import com.ragadmin.server.auth.model.AuthClaims;
import com.ragadmin.server.auth.model.AuthTokenType;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthUserStructMapper authUserStructMapper;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService();
        ReflectionTestUtils.setField(authService, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(authService, "sysRoleMapper", sysRoleMapper);
        ReflectionTestUtils.setField(authService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(authService, "tokenService", tokenService);
        ReflectionTestUtils.setField(authService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(authService, "authUserStructMapper", authUserStructMapper);
        ReflectionTestUtils.setField(authService, "authProperties", new AuthProperties()
                .setAccessTokenTtlSeconds(7200)
                .setRefreshTokenTtlSeconds(604800));
    }

    @Test
    void shouldLoginAndPersistSession() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("admin");
        request.setPassword("Admin@123456");

        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("hashed-password");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        CurrentUserResponse currentUser = new CurrentUserResponse()
                .setId(1L)
                .setUsername("admin")
                .setDisplayName("系统管理员")
                .setRoles(List.of("ADMIN"));

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Admin@123456", "hashed-password")).thenReturn(true);
        when(tokenService.generateAccessToken(eq(1L), eq("admin"), any())).thenReturn("access-token");
        when(tokenService.generateRefreshToken(eq(1L), eq("admin"), any())).thenReturn("refresh-token");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(authUserStructMapper.toCurrentUserResponse(user, List.of("ADMIN"))).thenReturn(currentUser);

        LoginResponse response = authService.loginForAdminPortal(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(7200, response.getExpiresIn());
        assertEquals(604800, response.getRefreshExpiresIn());
        assertEquals("admin", response.getUser().getUsername());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations, times(2)).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        assertTrue(keyCaptor.getAllValues().get(0).startsWith("rag:auth:session:"));
        assertEquals("1", valueCaptor.getAllValues().get(0));
        assertEquals(Duration.ofSeconds(604800), ttlCaptor.getAllValues().get(0));
        assertTrue(keyCaptor.getAllValues().get(1).startsWith("rag:auth:refresh:"));
        assertEquals("refresh-token", valueCaptor.getAllValues().get(1));
        assertEquals(Duration.ofSeconds(604800), ttlCaptor.getAllValues().get(1));
    }

    @Test
    void shouldRejectRefreshWhenStoredTokenMismatch() {
        AuthClaims claims = new AuthClaims()
                .setUserId(1L)
                .setUsername("admin")
                .setSessionId("session-1")
                .setTokenType(AuthTokenType.REFRESH);

        when(tokenService.parse("bad-refresh-token")).thenReturn(claims);
        when(stringRedisTemplate.hasKey("rag:auth:session:session-1")).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rag:auth:refresh:session-1")).thenReturn("another-token");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.refresh("bad-refresh-token")
        );

        assertEquals("UNAUTHORIZED", exception.getCode());
        assertTrue(exception.getMessage().contains("Refresh Token 无效或已失效"));
        verify(sysUserMapper, never()).selectById(any());
    }

    @Test
    void shouldRefreshTokenWhenSessionAlive() {
        AuthClaims claims = new AuthClaims()
                .setUserId(1L)
                .setUsername("admin")
                .setSessionId("session-2")
                .setTokenType(AuthTokenType.REFRESH);

        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        when(tokenService.parse("refresh-token-old")).thenReturn(claims);
        when(stringRedisTemplate.hasKey("rag:auth:session:session-2")).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rag:auth:refresh:session-2")).thenReturn("refresh-token-old");
        when(sysUserMapper.selectById(1L)).thenReturn(user);
        when(tokenService.generateAccessToken(1L, "admin", "session-2")).thenReturn("access-token-new");
        when(tokenService.generateRefreshToken(1L, "admin", "session-2")).thenReturn("refresh-token-new");

        RefreshTokenResponse response = authService.refresh("refresh-token-old");

        assertEquals("access-token-new", response.getAccessToken());
        assertEquals("refresh-token-new", response.getRefreshToken());
        assertEquals(7200, response.getExpiresIn());
        assertEquals(604800, response.getRefreshExpiresIn());
        verify(valueOperations).set("rag:auth:session:session-2", "1", Duration.ofSeconds(604800));
        verify(valueOperations).set("rag:auth:refresh:session-2", "refresh-token-new", Duration.ofSeconds(604800));
    }

    @Test
    void shouldDeleteSessionKeysOnLogout() {
        AuthenticatedUser user = new AuthenticatedUser()
                .setUserId(1L)
                .setUsername("admin")
                .setSessionId("session-3");

        authService.logout(user);

        verify(stringRedisTemplate).delete(List.of(
                "rag:auth:session:session-3",
                "rag:auth:refresh:session-3"
        ));
    }

    @Test
    void shouldAllowAdminRoleToLoginAppPortal() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("admin");
        request.setPassword("Admin@123456");

        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("hashed-password");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        CurrentUserResponse currentUser = new CurrentUserResponse()
                .setId(1L)
                .setUsername("admin")
                .setDisplayName("系统管理员")
                .setRoles(List.of("ADMIN"));

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Admin@123456", "hashed-password")).thenReturn(true);
        when(tokenService.generateAccessToken(eq(1L), eq("admin"), any())).thenReturn("access-token");
        when(tokenService.generateRefreshToken(eq(1L), eq("admin"), any())).thenReturn("refresh-token");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(authUserStructMapper.toCurrentUserResponse(user, List.of("ADMIN"))).thenReturn(currentUser);

        LoginResponse response = authService.loginForAppPortal(request);

        assertEquals("admin", response.getUser().getUsername());
        assertTrue(response.getUser().getRoles().contains("ADMIN"));
    }

    @Test
    void shouldRejectAdminPortalLoginWhenUserHasOnlyAppRole() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("app-user");
        request.setPassword("App@123456");

        SysUserEntity user = new SysUserEntity();
        user.setId(2L);
        user.setUsername("app-user");
        user.setPasswordHash("hashed-password");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("App@123456", "hashed-password")).thenReturn(true);
        when(sysRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("APP_USER"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.loginForAdminPortal(request)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        assertTrue(exception.getMessage().contains("后台管理权限"));
        verify(tokenService, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void shouldRejectAppPortalLoginWhenUserHasNoAllowedRole() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("plain-user");
        request.setPassword("User@123456");

        SysUserEntity user = new SysUserEntity();
        user.setId(3L);
        user.setUsername("plain-user");
        user.setPasswordHash("hashed-password");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("User@123456", "hashed-password")).thenReturn(true);
        when(sysRoleMapper.selectRoleCodesByUserId(3L)).thenReturn(List.of("USER"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.loginForAppPortal(request)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        assertTrue(exception.getMessage().contains("问答前台权限"));
        verify(tokenService, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void shouldRejectRoleAssertionWhenUserHasNoAllowedRole() {
        when(sysRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("APP_USER"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.assertAnyRole(2L, List.of("ADMIN"), "当前账号未开通用户管理权限")
        );

        assertEquals("FORBIDDEN", exception.getCode());
        assertTrue(exception.getMessage().contains("用户管理权限"));
    }
}
