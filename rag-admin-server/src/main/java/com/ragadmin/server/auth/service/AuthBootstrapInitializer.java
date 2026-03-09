package com.ragadmin.server.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.auth.config.AuthProperties;
import com.ragadmin.server.auth.entity.SysRoleEntity;
import com.ragadmin.server.auth.entity.SysUserEntity;
import com.ragadmin.server.auth.entity.SysUserRoleEntity;
import com.ragadmin.server.auth.mapper.SysRoleMapper;
import com.ragadmin.server.auth.mapper.SysUserMapper;
import com.ragadmin.server.auth.mapper.SysUserRoleMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrapInitializer.class);

    @Resource
    private AuthProperties authProperties;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        SysUserEntity existingUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getUsername, authProperties.getBootstrap().getAdminUsername())
                .last("LIMIT 1"));
        if (existingUser != null) {
            return;
        }

        SysRoleEntity adminRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getRoleCode, "ADMIN")
                .last("LIMIT 1"));
        if (adminRole == null) {
            log.warn("管理员角色不存在，跳过管理员初始化");
            return;
        }

        SysUserEntity user = new SysUserEntity();
        user.setUsername(authProperties.getBootstrap().getAdminUsername());
        user.setDisplayName(authProperties.getBootstrap().getAdminDisplayName());
        user.setMobile(authProperties.getBootstrap().getAdminMobile());
        user.setPasswordHash(passwordEncoder.encode(authProperties.getBootstrap().getAdminPassword()));
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);
        sysUserMapper.insert(user);

        SysUserRoleEntity relation = new SysUserRoleEntity();
        relation.setUserId(user.getId());
        relation.setRoleId(adminRole.getId());
        sysUserRoleMapper.insert(relation);

        log.info("已初始化默认管理员账号，username={}", user.getUsername());
    }
}
