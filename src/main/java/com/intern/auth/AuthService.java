package com.intern.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.common.BusinessException;
import com.intern.mapper.SysUserMapper;
import com.intern.model.entity.SysUser;
import com.intern.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public LoginResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException("用户名已被使用");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setRole("VISITOR");
        user.setOnline(false);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("用户名已被使用");
        }
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
