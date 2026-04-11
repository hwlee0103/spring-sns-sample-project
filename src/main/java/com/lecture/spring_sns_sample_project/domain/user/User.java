package com.lecture.spring_sns_sample_project.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 사용자 도메인 Entity.
 *
 * <p>비밀번호는 항상 인코딩된 값으로 생성/수정한다. 인코딩은 Service 레이어 책임이며, 이 클래스는 PasswordEncoder 등 보안 기술에 의존하지 않는다.
 */
@Entity
@Table(name = "users")
@Getter
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private String nickname;

  protected User() {}

  /**
   * @param email 검증된 이메일
   * @param encodedPassword 이미 인코딩된 비밀번호 (원문 금지)
   * @param nickname 검증된 닉네임
   */
  public User(String email, String encodedPassword, String nickname) {
    if (email == null || email.isBlank()) {
      throw UserException.invalidField("email");
    }
    if (encodedPassword == null || encodedPassword.isBlank()) {
      throw UserException.invalidField("password");
    }
    if (nickname == null || nickname.isBlank()) {
      throw UserException.invalidField("nickname");
    }
    this.email = email;
    this.password = encodedPassword;
    this.nickname = nickname;
  }

  /**
   * @param nickname 검증된 닉네임
   * @param encodedPassword 이미 인코딩된 비밀번호 (원문 금지)
   */
  public void update(String nickname, String encodedPassword) {
    if (nickname == null || nickname.isBlank()) {
      throw UserException.invalidField("nickname");
    }
    if (encodedPassword == null || encodedPassword.isBlank()) {
      throw UserException.invalidField("password");
    }
    this.nickname = nickname;
    this.password = encodedPassword;
  }
}
