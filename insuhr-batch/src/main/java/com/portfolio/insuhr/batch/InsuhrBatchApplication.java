package com.portfolio.insuhr.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 실행 모듈 2: 배치 서버 (설계서 8장).
 *
 * <p>실행: {@code java -jar insuhr-batch.jar --spring.batch.job.name={잡명} [targetDate=yyyy-MM-dd]} —
 * 잡 선택은 옵션 인자, {@code targetDate}는 비옵션 잡 파라미터다(설계서 3.0/8 v2.0).
 */
@SpringBootApplication(scanBasePackages = "com.portfolio.insuhr")
@EntityScan("com.portfolio.insuhr")
@EnableJpaRepositories("com.portfolio.insuhr")
public class InsuhrBatchApplication {

  public static void main(String[] args) {
    // 배치는 잡 종료와 함께 프로세스가 끝나야 한다. 종료코드를 그대로 셸에 전파해
    // 스케줄러가 실패를 감지할 수 있게 한다.
    System.exit(SpringApplication.exit(SpringApplication.run(InsuhrBatchApplication.class, args)));
  }
}
