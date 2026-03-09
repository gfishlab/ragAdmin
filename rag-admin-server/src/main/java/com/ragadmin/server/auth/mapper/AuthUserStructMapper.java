package com.ragadmin.server.auth.mapper;

import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.UserListItemResponse;
import com.ragadmin.server.auth.entity.SysUserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AuthUserStructMapper {

    @Mapping(target = "roles", source = "roles")
    CurrentUserResponse toCurrentUserResponse(SysUserEntity user, List<String> roles);

    @Mapping(target = "roles", source = "roles")
    UserListItemResponse toUserListItemResponse(SysUserEntity user, List<String> roles);
}
