package com.lecture.spring_sns_sample_project;

import com.lecture.spring_sns_sample_project.config.TestSessionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestSessionConfig.class)
class SpringSnsSampleProjectApplicationTests {

  @Test
  void contextLoads() {}
}
