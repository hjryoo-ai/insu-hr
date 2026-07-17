package com.portfolio.insuhr.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 실행 모듈 2: 배치 서버 (설계서 8장).
 *
 * <p>실행: {@code java -jar insuhr-batch.jar --job.name={잡명} [--targetDate=yyyy-MM-dd]}
 *
 * <p>Phase 0에서는 골격만. 잡 10종은 Phase 7에서 구현한다.
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
