package com.lecture.spring_sns_sample_project.domain.user;

import com.lecture.spring_sns_sample_project.domain.follow.FollowCount;
import com.lecture.spring_sns_sample_project.domain.follow.FollowCountRepository;
import com.lecture.spring_sns_sample_project.domain.follow.FollowRepository;
import com.lecture.spring_sns_sample_project.domain.post.PostRepository;
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
  private final FollowRepository followRepository;
  private final FollowCountRepository followCountRepository;
  private final PostRepository postRepository;
  private final PasswordEncoder passwordEncoder;

  /**
   * 회원가입.
   *
   * <p>raw 입력값을 검증한 뒤 비밀번호를 인코딩하여 Entity 를 생성한다. Service 가 인코딩을 책임진다 (Entity 는 PasswordEncoder 에
   * 의존하지 않음).
   *
   * <p>회원 생성과 함께 {@link FollowCount} 초기행(0, 0)을 동시에 생성하여, 프로필 조회 시 null 처리 없이 카운트를 반환할 수 있도록 한다.
   */
  @Transactional
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
      userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
      if (userRepository.existsByEmail(email)) {
        throw UserException.emailAlreadyExists(email);
      }
      throw UserException.nicknameAlreadyExists(nickname);
    }

    // FollowCount 초기행 생성 — 팔로워/팔로이 수 0으로 시작
    followCountRepository.save(new FollowCount(user));

    return user;
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
    if (!user.getNickname().equals(nickname) && userRepository.existsByNickname(nickname)) {
      throw UserException.nicknameAlreadyExists(nickname);
    }
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
    if (currentRawPassword.equals(newRawPassword)) {
      throw UserException.samePassword();
    }
    user.changePassword(passwordEncoder.encode(newRawPassword));
    return user;
  }

  /**
   * 회원 탈퇴 — 관련 레코드 cascade 정리.
   *
   * <p>FK 제약이 없으므로 애플리케이션에서 직접 관련 행을 삭제한다. 순서: follow → follow_count → post → user.
   *
   * <p>팔로우 관계는 양방향(follower/following) 모두 제거하여 고아 레코드를 남기지 않는다.
   */
  @Transactional
  public void delete(Long id) {
    User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound(id));
    // Cascade cleanup — FK 제약이 없으므로 수동 정리 필수
    followRepository.deleteAllByUser(user);
    followCountRepository.deleteByUserId(id);
    postRepository.deleteAllByAuthor(user);
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
