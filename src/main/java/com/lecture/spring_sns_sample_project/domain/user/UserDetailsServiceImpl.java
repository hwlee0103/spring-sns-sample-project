package com.lecture.spring_sns_sample_project.domain.user;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  /** 존재하지 않는 사용자에 대한 타이밍 공격을 막기 위한 더미 인코딩 결과. 부팅 시 1회 계산하여 매 호출마다 동일한 비교 비용을 지불한다. */
  private String dummyEncodedPassword;

  @PostConstruct
  void init() {
    this.dummyEncodedPassword = passwordEncoder.encode("dummy-password-for-timing-defense");
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty()) {
      // 타이밍 공격 방어 — 존재하지 않는 사용자에도 동일한 시간(matches 비용)을 소모시켜
      // 응답 시간 차이로 계정 존재 여부가 탐지되지 않도록 한다.
      passwordEncoder.matches("dummy-password-for-timing-defense", dummyEncodedPassword);
      throw new UsernameNotFoundException("User not found: " + email);
    }
    return AuthUser.from(userOpt.get());
  }
}
