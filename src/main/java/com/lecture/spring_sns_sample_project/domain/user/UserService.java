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
   * <p>raw 입력값을 검증한 뒤 비밀번호를 인코딩하여 Entity 를 생성한다. Service 가 인코딩을 책임진다 (Entity 는 PasswordEncoder 에
   * 의존하지 않음).
   */
  public User register(String email, String rawPassword, String nickname) {
    validateEmail(email);
    validateRawPassword(rawPassword);
    validateNickname(nickname);

    if (userRepository.existsByEmail(email)) {
      throw UserException.emailAlreadyExists(email);
    }
    if (userRepository.existsByNickname(nickname)) {
      throw UserException.nicknameAlreadyExists(nickname);
    }

    User user = new User(email, passwordEncoder.encode(rawPassword), nickname);
    try {
      return userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
      // exists 체크와 save 사이 race condition 방어 — DB unique 제약으로 최종 보장
      // email 또는 nickname 둘 중 어느 쪽인지 재확인
      if (userRepository.existsByEmail(email)) {
        throw UserException.emailAlreadyExists(email);
      }
      throw UserException.nicknameAlreadyExists(nickname);
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

  public User getByNickname(String nickname) {
    return userRepository
        .findByNickname(nickname)
        .orElseThrow(() -> UserException.notFoundByNickname(nickname));
  }

  public Page<User> getAll(Pageable pageable) {
    return userRepository.findAll(pageable);
  }

  /** 프로필 수정 — nickname 만 변경. 비밀번호는 전용 {@link #changePassword} 엔드포인트에서만 변경 가능. */
  @Transactional
  public User update(Long id, String nickname) {
    validateNickname(nickname);
    User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound(id));
    user.updateNickname(nickname);
    return user;
  }

  /**
   * 비밀번호 변경 — 현재 비밀번호 재확인 후 새 비밀번호로 교체.
   *
   * <p>{@code User.changePassword()} 가 tokenVersion 을 자동 증가시켜 다른 디바이스의 세션을 무효화한다.
   */
  @Transactional
  public User changePassword(Long id, String currentRawPassword, String newRawPassword) {
    validateRawPassword(currentRawPassword);
    validateRawPassword(newRawPassword);
    User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound(id));
    if (!passwordEncoder.matches(currentRawPassword, user.getPassword())) {
      throw UserException.invalidCredentials();
    }
    user.changePassword(passwordEncoder.encode(newRawPassword));
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
