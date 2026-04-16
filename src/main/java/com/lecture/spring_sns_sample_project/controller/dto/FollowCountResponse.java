package com.lecture.spring_sns_sample_project.controller.dto;

/** 팔로워/팔로이 수 응답. 도메인(FollowCount)과 동일하게 followersCount/followeesCount 로 통일. */
public record FollowCountResponse(long followersCount, long followeesCount) {}
