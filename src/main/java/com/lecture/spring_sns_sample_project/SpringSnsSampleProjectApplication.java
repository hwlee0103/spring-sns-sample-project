package com.lecture.spring_sns_sample_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRetry
public class SpringSnsSampleProjectApplication {

  public static void main(String[] args) {
    SpringApplication.run(SpringSnsSampleProjectApplication.class, args);
  }
}
