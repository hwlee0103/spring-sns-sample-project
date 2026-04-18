package com.lecture.spring_sns_sample_project.config;

import com.lecture.spring_sns_sample_project.domain.follow.FollowService;
import com.lecture.spring_sns_sample_project.domain.post.Post;
import com.lecture.spring_sns_sample_project.domain.post.PostRepository;
import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * dev 프로필 전용 샘플 데이터 로더.
 *
 * <p>애플리케이션 기동 시 사용자 10명 + 게시글 10개 + 팔로우 관계 16개를 자동 생성한다. {@link UserService#register} 를 거쳐 비밀번호
 * 인코딩 + FollowCount 초기행 생성이 정상 수행된다.
 *
 * <p>이미 데이터가 존재하면(재시작 시) 스킵한다.
 */
@Profile("dev")
@Component
@RequiredArgsConstructor
@Slf4j
public class SampleDataLoader implements ApplicationRunner {

  private static final String DEFAULT_PASSWORD = "password123";

  private final UserService userService;
  private final FollowService followService;
  private final PostRepository postRepository;

  @Override
  public void run(ApplicationArguments args) {
    try {
      userService.getByEmail("alice@example.com");
      log.info("[SampleDataLoader] 샘플 데이터 이미 존재 — 스킵");
      return;
    } catch (Exception ignored) {
      // 데이터 없음 → 생성 진행
    }

    log.info("[SampleDataLoader] 샘플 데이터 생성 시작");

    // 1. 사용자 10명
    Map<String, User> users = new LinkedHashMap<>();
    List.of("alice", "bob", "carol", "dave", "eve", "frank", "grace", "henry", "iris", "jack")
        .forEach(
            nick -> {
              User user = userService.register(nick + "@example.com", DEFAULT_PASSWORD, nick);
              users.put(nick, user);
              log.info("  사용자 생성: {} (id={})", nick, user.getId());
            });

    // 2. 게시글 10개
    Map<String, String> posts =
        Map.of(
            "alice", "오늘 날씨가 정말 좋네요!",
            "bob", "맛집 추천 받습니다!",
            "carol", "독서 모임 멤버를 모집합니다.",
            "dave", "커피 한 잔의 여유",
            "eve", "오늘의 코딩 일기",
            "frank", "새 앨범 나왔는데 추천!",
            "grace", "여행 계획 세우는 중",
            "henry", "오늘 배운 것: Spring Security");
    posts.forEach(
        (nick, content) -> {
          postRepository.save(new Post(users.get(nick), content));
        });
    // alice, bob 추가 게시글
    postRepository.save(new Post(users.get("alice"), "새로운 프로젝트를 시작했어요."));
    postRepository.save(new Post(users.get("bob"), "주말에 등산 다녀왔어요."));
    log.info("  게시글 {}개 생성", posts.size() + 2);

    // 3. 팔로우 관계 16개
    List<String[]> followPairs =
        List.of(
            new String[] {"alice", "bob"},
            new String[] {"alice", "carol"},
            new String[] {"alice", "dave"},
            new String[] {"bob", "alice"},
            new String[] {"bob", "carol"},
            new String[] {"carol", "alice"},
            new String[] {"carol", "bob"},
            new String[] {"dave", "alice"},
            new String[] {"dave", "eve"},
            new String[] {"eve", "alice"},
            new String[] {"eve", "frank"},
            new String[] {"frank", "grace"},
            new String[] {"grace", "henry"},
            new String[] {"henry", "iris"},
            new String[] {"iris", "jack"},
            new String[] {"jack", "alice"});
    followPairs.forEach(
        pair -> followService.follow(users.get(pair[0]).getId(), users.get(pair[1]).getId()));
    log.info("  팔로우 관계 {}개 생성", followPairs.size());

    log.info("[SampleDataLoader] 샘플 데이터 생성 완료");
    log.info(
        "  alice: followers={}, followees={}",
        followService.getFollowCount(users.get("alice").getId()).followersCount(),
        followService.getFollowCount(users.get("alice").getId()).followeesCount());
  }
}
