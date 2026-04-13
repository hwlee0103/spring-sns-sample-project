package com.lecture.spring_sns_sample_project.domain.user.security;

import com.lecture.spring_sns_sample_project.domain.user.User;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security {@code Principal} 로 사용되는 커스텀 UserDetails.
 *
 * <p>{@link User} 의 id/email/nickname/tokenVersion 을 동시에 보관하여, 권한 체크 및 세션 유효성 검증 시 추가 DB 조회를 최소화한다.
 * {@code AuthenticationSuccessHandler} 가 DB 재조회 없이 응답을 구성할 수 있도록 nickname 도 포함한다.
 *
 * <p>Redis 세션 저장소에 직렬화되므로 {@link Serializable} 을 구현한다.
 */
@Getter
public class AuthUser implements UserDetails, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private final Long id;
  private final String email;
  private final String nickname;
  private final String password;
  private final int tokenVersion;

  public AuthUser(Long id, String email, String nickname, String password, int tokenVersion) {
    this.id = id;
    this.email = email;
    this.nickname = nickname;
    this.password = password;
    this.tokenVersion = tokenVersion;
  }

  public static AuthUser from(User user) {
    return new AuthUser(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getPassword(),
        user.getTokenVersion());
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
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
