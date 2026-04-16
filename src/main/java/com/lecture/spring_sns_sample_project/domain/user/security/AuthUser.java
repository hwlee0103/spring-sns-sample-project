package com.lecture.spring_sns_sample_project.domain.user.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

  @Serial private static final long serialVersionUID = 3L;

  private final Long id;
  private final String email;
  private final String nickname;
  private final Role role;

  /** 내부 세션 검증용. 직렬화는 허용(Redis), JSON 응답에는 노출 금지. */
  @JsonIgnore private final int tokenVersion;

  /**
   * 비밀번호 해시 — 인증 시점({@code DaoAuthenticationProvider})에서만 사용. Redis 직렬화 시 {@code transient} 로 제외되어
   * 세션에 저장되지 않는다.
   */
  private final transient String password;

  public AuthUser(
      Long id, String email, String nickname, Role role, int tokenVersion, String password) {
    this.id = id;
    this.email = email;
    this.nickname = nickname;
    this.role = role;
    this.tokenVersion = tokenVersion;
    this.password = password;
  }

  /** 인증용 — password hash 포함. {@code UserDetailsServiceImpl.loadUserByUsername()} 에서 사용. */
  public static AuthUser from(User user) {
    return new AuthUser(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getRole(),
        user.getTokenVersion(),
        user.getPassword());
  }

  /** 세션 갱신용 — password hash 미포함. {@code refreshSecurityContext()} 에서 사용. */
  public static AuthUser withoutPassword(User user) {
    return new AuthUser(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getRole(),
        user.getTokenVersion(),
        null);
  }

  /**
   * 인증 시점에는 실제 해시 반환, Redis 역직렬화 후에는 {@code transient} 로 null. 인증 완료 후 세션에서 복원된 {@code AuthUser} 는
   * password 가 null 이므로 Redis 유출 시에도 해시가 노출되지 않는다.
   */
  @Override
  public String getPassword() {
    return password;
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
