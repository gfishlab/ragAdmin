package com.ragadmin.server.auth.controller;

import com.ragadmin.server.auth.dto.AssignUserRolesRequest;
import com.ragadmin.server.auth.dto.CreateUserRequest;
import com.ragadmin.server.auth.dto.UpdateUserRequest;
import com.ragadmin.server.auth.dto.UserListItemResponse;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.auth.service.UserAdminService;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private AuthService authService;

    @GetMapping
    public ApiResponse<PageResponse<UserListItemResponse>> list(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        assertUserManagementAccess(request);
        return ApiResponse.success(userAdminService.list(keyword, status, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<UserListItemResponse> create(
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody CreateUserRequest request
    ) {
        assertUserManagementAccess(httpServletRequest);
        return ApiResponse.success(userAdminService.create(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserListItemResponse> update(
            HttpServletRequest httpServletRequest,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        assertUserManagementAccess(httpServletRequest);
        return ApiResponse.success(userAdminService.update(userId, request));
    }

    @PutMapping("/{userId}/roles")
    public ApiResponse<Void> assignRoles(
            HttpServletRequest httpServletRequest,
            @PathVariable Long userId,
            @Valid @RequestBody AssignUserRolesRequest request
    ) {
        assertUserManagementAccess(httpServletRequest);
        userAdminService.assignRoles(userId, request.getRoleCodes());
        return ApiResponse.success(null);
    }

    private void assertUserManagementAccess(HttpServletRequest request) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) request.getAttribute(AuthService.REQUEST_ATTRIBUTE);
        // 用户管理属于高敏感治理能力，本轮先只对系统管理员开放。
        authService.assertAnyRole(authenticatedUser.getUserId(), List.of("ADMIN"), "当前账号未开通用户管理权限");
    }
}
