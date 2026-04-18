package com.lecture.spring_sns_sample_project.domain.user.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 컨트롤러 메서드 파라미터에서 현재 인증된 사용자({@link AuthUser})를 주입받는 커스텀 어노테이션.
 *
 * <p>{@code @AuthenticationPrincipal AuthUser authUser} 를 매번 작성하는 대신 {@code @CurrentUser AuthUser
 * authUser} 로 간결하게 사용한다.
 *
 * <p>내부적으로 Spring Security 의 {@link AuthenticationPrincipal} 을 메타 어노테이션으로 위임한다.
 *
 * <pre>
 * // Before
 * public ResponseEntity&lt;?&gt; create(@AuthenticationPrincipal AuthUser authUser) { ... }
 *
 * // After
 * public ResponseEntity&lt;?&gt; create(@CurrentUser AuthUser authUser) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUser {}
