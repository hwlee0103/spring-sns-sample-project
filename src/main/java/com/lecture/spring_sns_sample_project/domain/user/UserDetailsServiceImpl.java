package com.lecture.spring_sns_sample_project.domain.user;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return userRepository
        .findByEmail(email)
        .map(
            user ->
                User.withUsername(user.getEmail())
                    .password(user.getPassword())
                    .authorities(List.of())
                    .build())
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
  }
}
