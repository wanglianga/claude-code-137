package com.community.medrecycle.service;

import com.community.medrecycle.auth.AuthUser;
import com.community.medrecycle.auth.TokenStore;
import com.community.medrecycle.common.BizException;
import com.community.medrecycle.domain.PointsAccount;
import com.community.medrecycle.domain.User;
import com.community.medrecycle.dto.AuthDtos;
import com.community.medrecycle.repo.PointsAccountRepository;
import com.community.medrecycle.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PointsAccountRepository pointsAccountRepository;
    private final TokenStore tokenStore;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public AuthDtos.UserView register(AuthDtos.RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BizException("用户名已存在");
        }
        User u = new User();
        u.setUsername(req.username());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setName(req.name());
        u.setPhone(req.phone());
        u.setRole(User.Role.RESIDENT);
        userRepository.save(u);

        PointsAccount account = new PointsAccount();
        account.setUserId(u.getId());
        account.setBalance(0);
        pointsAccountRepository.save(account);
        return AuthDtos.UserView.of(u);
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest req) {
        User u = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new BizException("用户名或密码错误"));
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        AuthUser authUser = new AuthUser(u.getId(), u.getUsername(), u.getName(), u.getRole(),
                u.getCommunityPointId());
        String token = tokenStore.issue(authUser);
        return new AuthDtos.LoginResponse(token, AuthDtos.UserView.of(u));
    }

    public String encode(String raw) {
        return passwordEncoder.encode(raw);
    }
}
