package com.lecture.spring_sns_sample_project.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  /**
   * 회원가입.
   *
   * <p>raw 입력값을 검증한 뒤 비밀번호를 인코딩하여 Entity 를 생성한다. {@code register} 와 {@link #update} 모두 동일하게 Service
   * 가 인코딩을 책임진다 (Entity 는 PasswordEncoder 에 의존하지 않음).
   */
  public User register(String email, String rawPassword, String nickname) {
    validateEmail(email);
    validateRawPassword(rawPassword);
    validateNickname(nickname);

    if (userRepository.existsByEmail(email)) {
      throw UserException.emailAlreadyExists(email);
    }

    User user = new User(email, passwordEncoder.encode(rawPassword), nickname);
    try {
      return userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
      // existsByEmail 과 save 사이 race condition 방어 — DB unique 제약으로 최종 보장
      throw UserException.emailAlreadyExists(email);
    }
  }

  public User getById(Long id) {
    return userRepository.findById(id).orElseThrow(() -> UserException.notFound(id));
  }

  public User getByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> UserException.notFoundByEmail(email));
  }

  public Page<User> getAll(Pageable pageable) {
    return userRepository.findAll(pageable);
  }

  @Transactional
  public User update(Long id, UserUpdateCommand command) {
    validateNickname(command.nickname());
    validateRawPassword(command.rawPassword());
    User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound(id));
    user.update(command.nickname(), passwordEncoder.encode(command.rawPassword()));
    return user;
  }

  @Transactional
  public void delete(Long id) {
    User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound(id));
    userRepository.delete(user);
  }

  private static void validateEmail(String email) {
    if (email == null || email.isBlank()) {
      throw UserException.invalidField("email");
    }
  }

  private static void validateRawPassword(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw UserException.invalidField("password");
    }
  }

  private static void validateNickname(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      throw UserException.invalidField("nickname");
    }
  }
}
