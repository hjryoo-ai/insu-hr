package com.portfolio.insuhr.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 실행 모듈 1: REST API 서버.
 *
 * <p>엔티티·리포지토리는 이 클래스가 있는 {@code ...api} 가 아니라 insuhr-domain 모듈에 있다. 컴포넌트 스캔과 달리 엔티티/리포지토리 스캔은 부트 앱
 * 클래스의 패키지를 기준으로 잡히므로, 세 범위를 모두 {@code com.portfolio.insuhr} 로 명시한다.
 */
@SpringBootApplication(scanBasePackages = "com.portfolio.insuhr")
@EntityScan("com.portfolio.insuhr")
@EnableJpaRepositories("com.portfolio.insuhr")
public class InsuhrApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(InsuhrApiApplication.class, args);
  }
}
