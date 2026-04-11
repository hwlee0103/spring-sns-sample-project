package com.lecture.spring_sns_sample_project.domain.user.security;

import com.lecture.spring_sns_sample_project.domain.user.User;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security `Principal` 로 사용되는 커스텀 UserDetails. {@link User} 의 id/email 을 동시에 보관하여, 권한 체크 시
 * 추가 DB 조회 없이 작성자 식별이 가능하다.
 */
@Getter
public class AuthUser implements UserDetails {

  private final Long id;
  private final String email;
  private final String password;

  public AuthUser(Long id, String email, String password) {
    this.id = id;
    this.email = email;
    this.password = password;
  }

  public static AuthUser from(User user) {
    return new AuthUser(user.getId(), user.getEmail(), user.getPassword());
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
