package com.lecture.spring_sns_sample_project.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lecture.spring_sns_sample_project.config.TestSessionConfig;
import com.lecture.spring_sns_sample_project.config.TokenVersionFilter;
import com.lecture.spring_sns_sample_project.domain.follow.FollowCount;
import com.lecture.spring_sns_sample_project.domain.follow.FollowCountRepository;
import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Auth 흐름 통합 테스트 — MockMvc 기반 로그인/me/tokenVersion/logout end-to-end.
 *
 * <p>전체 Spring Security 필터 체인 + RestAuthenticationFilter + TokenVersionFilter 를 거쳐 실행된다. 테스트 세션은
 * {@link TestSessionConfig} 의 in-memory 스텁으로 격리된다.
 *
 * <p>RateLimitFilter 가 127.0.0.1 단일 IP 기반으로 동작하므로 테스트 전역에서 분당 상한을 10_000 으로 조정한다.
 */
@SpringBootTest
@Import(TestSessionConfig.class)
@TestPropertySource(properties = "app.rate-limit.ip-requests-per-minute=10000")
@DisplayName("Auth 흐름 통합 테스트")
class AuthFlowIntegrationTest {

  private static final String EMAIL = "auth_test@example.com";
  private static final String PASSWORD = "password123";
  private static final String NICKNAME = "auth_test_nick";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository userRepository;
  @Autowired private FollowCountRepository followCountRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TokenVersionFilter tokenVersionFilter;

  private MockMvc mockMvc;
  private User testUser;

  @BeforeEach
  void setUp() {
    // MockMvcBuilders.webAppContextSetup + springSecurity 로 Spring Security 전체 체인 활성화
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();

    followCountRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();

    testUser = new User(EMAIL, passwordEncoder.encode(PASSWORD), NICKNAME);
    userRepository.saveAndFlush(testUser);
    followCountRepository.saveAndFlush(new FollowCount(testUser));

    // 이전 테스트의 tokenVersion 캐시가 남지 않도록 evict
    tokenVersionFilter.evict(testUser.getId());
  }

  private String loginBody(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }

  @Nested
  @DisplayName("POST /api/v1/auth/login")
  class Login {

    @Test
    @DisplayName("정상 로그인 — 200 + UserResponse JSON")
    void 정상_로그인() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(loginBody(EMAIL, PASSWORD)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(testUser.getId()))
          .andExpect(jsonPath("$.email").value(EMAIL))
          .andExpect(jsonPath("$.nickname").value(NICKNAME))
          // tokenVersion 은 응답에 포함되지 않아야 함 (#144 @JsonIgnore)
          .andExpect(jsonPath("$.tokenVersion").doesNotExist())
          .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("잘못된 비밀번호 → 401")
    void 잘못된_비밀번호() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(loginBody(EMAIL, "wrong_password")))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 email → 401 (타이밍 균일 — UserDetailsServiceImpl 더미 인코딩)")
    void 미존재_email() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(loginBody("ghost@example.com", PASSWORD)))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Content-Type 느슨 파싱 — application/json; charset=UTF-8 허용 (#104)")
    void contentType_charset_포함() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                  .content(loginBody(EMAIL, PASSWORD)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("잘못된 JSON → 401 (AuthenticationServiceException)")
    void 잘못된_json() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("not-a-json"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("세션 고정 방어 — 로그인 전후 세션 ID 가 변경됨")
    void 세션_고정_방어() throws Exception {
      // 로그인 전: 요청마다 새 세션 생성되지만 changeSessionId() 후에도 값이 있어야 함
      MvcResult result =
          mockMvc
              .perform(
                  post("/api/v1/auth/login")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(loginBody(EMAIL, PASSWORD)))
              .andExpect(status().isOk())
              .andReturn();

      HttpSession sessionAfterLogin = result.getRequest().getSession(false);
      assertThat(sessionAfterLogin).isNotNull();
      assertThat(sessionAfterLogin.getId()).isNotBlank();
      // 로그인 성공 후 SecurityContext 가 저장된 세션이 존재함을 확인
    }
  }

  @Nested
  @DisplayName("GET /api/v1/auth/me")
  class Me {

    @Test
    @DisplayName("비인증 — 401")
    void 비인증() throws Exception {
      mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인 후 세션 포함 요청 → 200 + 현재 사용자 정보")
    void 로그인_후_me() throws Exception {
      MockHttpSession session = loginAndGetSession();

      mockMvc
          .perform(get("/api/v1/auth/me").session(session))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value(EMAIL))
          .andExpect(jsonPath("$.nickname").value(NICKNAME));
    }

    @Test
    @DisplayName("세션은 있지만 user 가 DB 에서 삭제됨 → 401 + 세션 무효화")
    void 삭제된_user() throws Exception {
      MockHttpSession session = loginAndGetSession();

      // DB 에서 사용자 완전 삭제
      userRepository.deleteAllInBatch();
      followCountRepository.deleteAllInBatch();
      tokenVersionFilter.evict(testUser.getId());

      mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("tokenVersion 불일치 — 비밀번호 변경 등으로 DB 버전이 증가하면 401")
    void tokenVersion_불일치() throws Exception {
      MockHttpSession session = loginAndGetSession();

      // 첫 호출은 200 (DB tokenVersion=0 == session tokenVersion=0)
      mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());

      // DB 의 tokenVersion 강제 증가 (비밀번호 변경을 시뮬레이션)
      User user = userRepository.findById(testUser.getId()).orElseThrow();
      user.changePassword(passwordEncoder.encode("new_password"));
      userRepository.saveAndFlush(user);
      // TokenVersionFilter Caffeine 캐시를 evict 하여 다음 요청에서 DB 재조회
      tokenVersionFilter.evict(testUser.getId());

      mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("POST /api/v1/auth/logout")
  class Logout {

    @Test
    @DisplayName("로그아웃 성공 — 204")
    void 로그아웃_성공() throws Exception {
      MockHttpSession session = loginAndGetSession();

      mockMvc
          .perform(post("/api/v1/auth/logout").session(session).with(csrf()))
          .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("로그아웃 후 me → 401 (세션 무효화 확인)")
    void 로그아웃_후_me() throws Exception {
      MockHttpSession session = loginAndGetSession();

      mockMvc
          .perform(post("/api/v1/auth/logout").session(session).with(csrf()))
          .andExpect(status().isNoContent());

      // 로그아웃으로 세션 무효화됨 — 동일 세션으로 me 호출 시 401
      mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
  }

  /** 로그인 수행 후 인증된 세션 반환. */
  private MockHttpSession loginAndGetSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
    MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
    if (session == null) {
      throw new IllegalStateException("로그인 후 세션이 존재해야 합니다.");
    }
    return session;
  }
}
