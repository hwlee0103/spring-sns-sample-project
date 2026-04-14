package com.lecture.spring_sns_sample_project.domain.user.security;

import com.lecture.spring_sns_sample_project.domain.user.Role;
import com.lecture.spring_sns_sample_project.domain.user.User;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security {@code Principal} 로 사용되는 커스텀 UserDetails.
 *
 * <p>{@link User} 의 id/email/nickname/role/tokenVersion 을 동시에 보관하여, 권한 체크 및 세션 유효성 검증 시 추가 DB 조회를
 * 최소화한다.
 *
 * <p>Redis 세션 저장소에 직렬화되므로 {@link Serializable} 을 구현한다. <b>비밀번호 해시는 저장하지 않는다</b> — 인증은 {@code
 * DaoAuthenticationProvider} 에서 완료되며, 이후 세션에 비밀번호를 보관할 이유가 없다. Redis 접근 시 해시 유출을 원천 차단한다.
 */
@Getter
public class AuthUser implements UserDetails, Serializable {

  @Serial private static final long serialVersionUID = 2L;

  private final Long id;
  private final String email;
  private final String nickname;
  private final Role role;
  private final int tokenVersion;

  public AuthUser(Long id, String email, String nickname, Role role, int tokenVersion) {
    this.id = id;
    this.email = email;
    this.nickname = nickname;
    this.role = role;
    this.tokenVersion = tokenVersion;
  }

  public static AuthUser from(User user) {
    return new AuthUser(
        user.getId(), user.getEmail(), user.getNickname(), user.getRole(), user.getTokenVersion());
  }

  /** 비밀번호 해시를 Redis 세션에 직렬화하지 않기 위해 항상 null 반환. */
  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    if (role == Role.ADMIN) {
      return List.of(
          new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
