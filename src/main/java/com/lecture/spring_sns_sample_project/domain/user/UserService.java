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

  public User register(User user) {
    if (userRepository.existsByEmail(user.getEmail())) {
      throw UserException.emailAlreadyExists(user.getEmail());
    }
    user.encodePassword(passwordEncoder);
    try {
      return userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
      throw UserException.emailAlreadyExists(user.getEmail());
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
  public User update(Long id, String nickname, String password) {
    if (password == null || password.isBlank()) {
      throw UserException.invalidField("password");
    }
    User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound(id));
    user.update(nickname, passwordEncoder.encode(password));
    return user;
  }

  public void delete(Long id) {
    if (!userRepository.existsById(id)) {
      throw UserException.notFound(id);
    }
    userRepository.deleteById(id);
  }
}
