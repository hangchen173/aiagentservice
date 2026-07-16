package com.intern.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.mapper.SysUserMapper;
import com.intern.model.entity.SysUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StaffAccountBootstrap implements ApplicationRunner {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Account admin;
    private final Account agent;

    public StaffAccountBootstrap(
            SysUserMapper userMapper,
            PasswordEncoder passwordEncoder,
            @Value("${nexusmind.bootstrap.admin.username:}") String adminUsername,
            @Value("${nexusmind.bootstrap.admin.password:}") String adminPassword,
            @Value("${nexusmind.bootstrap.admin.display-name:系统管理员}") String adminDisplayName,
            @Value("${nexusmind.bootstrap.agent.username:}") String agentUsername,
            @Value("${nexusmind.bootstrap.agent.password:}") String agentPassword,
            @Value("${nexusmind.bootstrap.agent.display-name:客服坐席}") String agentDisplayName) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.admin = new Account(adminUsername, adminPassword, adminDisplayName, "ADMIN");
        this.agent = new Account(agentUsername, agentPassword, agentDisplayName, "AGENT");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        upsert(requireConfigured(admin));
        upsert(requireConfigured(agent));
    }

    private Account requireConfigured(Account account) {
        if (account.username().isBlank() || account.password().length() < 12) {
            throw new IllegalStateException(account.role() + " 预置账号未配置，密码至少需要 12 个字符");
        }
        return account;
    }

    private void upsert(Account account) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, account.username()));
        if (user == null) {
            user = new SysUser();
            user.setUsername(account.username());
        }
        user.setPassword(passwordEncoder.encode(account.password()));
        user.setDisplayName(account.displayName());
        user.setRole(account.role());
        user.setOnline(false);
        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
    }

    private record Account(String username, String password, String displayName, String role) {
    }
}
