package com.portfolio.insuhr.relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 실행 모듈 3: Outbox 릴레이 (설계서 9.2).
 *
 * <p>READY 이벤트를 EVENT_ID 순으로 집어 구독 시스템에 전송한다. Phase 0에서는 골격만. 폴러/웹훅 서명/재시도는 Phase 6에서 구현한다.
 */
@SpringBootApplication(scanBasePackages = "com.portfolio.insuhr")
@EntityScan("com.portfolio.insuhr")
@EnableJpaRepositories("com.portfolio.insuhr")
public class InsuhrRelayApplication {

  public static void main(String[] args) {
    SpringApplication.run(InsuhrRelayApplication.class, args);
  }
}
