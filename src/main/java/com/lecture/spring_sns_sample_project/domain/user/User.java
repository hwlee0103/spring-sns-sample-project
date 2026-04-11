package com.lecture.spring_sns_sample_project.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.springframework.security.crypto.password.PasswordEncoder;

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

  public User(String email, String password, String nickname) {
    this.email = email;
    this.password = password;
    this.nickname = nickname;
  }

  public void encodePassword(PasswordEncoder passwordEncoder) {
    this.password = passwordEncoder.encode(this.password);
  }

  public void update(String nickname, String password) {
    if (nickname == null || nickname.isBlank()) {
      throw UserException.invalidField("nickname");
    }
    if (password == null || password.isBlank()) {
      throw UserException.invalidField("password");
    }
    this.nickname = nickname;
    this.password = password;
  }
}
