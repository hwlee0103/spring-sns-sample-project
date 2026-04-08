package com.lecture.spring_sns_sample_project.domain.user;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  public User register(User user) {
    if (userRepository.existsByEmail(user.getEmail())) {
      throw UserException.emailAlreadyExists(user.getEmail());
    }
    return userRepository.save(user);
  }

  public User getById(Long id) {
    return userRepository.findById(id).orElseThrow(() -> UserException.notFound(id));
  }

  public List<User> getAll() {
    return userRepository.findAll();
  }

  @Transactional
  public User update(Long id, String nickname, String password) {
    User user = userRepository.findById(id).orElseThrow(() -> UserException.notFound(id));
    user.update(nickname, password);
    return user;
  }

  public void delete(Long id) {
    if (!userRepository.existsById(id)) {
      throw UserException.notFound(id);
    }
    userRepository.deleteById(id);
  }
}
