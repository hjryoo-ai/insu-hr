package com.portfolio.insuhr.batch.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code hrSnapshotFileJob} — 스냅샷 CSV(헤더+행+트레일러) + SHA-256 체크섬 파일 (설계서 9.5, 13.2 완료 기준).
 *
 * <p>완료 기준의 핵심은 체크섬 검증이다 — 태스클릿이 쓴 {@code .sha256}의 해시가 CSV 바이트의 실제 SHA-256과 일치하는지 본다.
 */
class HrSnapshotFileJobIntegrationTest extends AbstractBatchIntegrationTest {

  // @TempDir + static @DynamicPropertySource의 초기화 순서 함정을 피해 직접 임시 디렉터리를 만든다.
  private static final Path SNAPSHOT_DIR = createTempDir();

  @Autowired Job hrSnapshotFileJob;

  @DynamicPropertySource
  static void snapshotDir(DynamicPropertyRegistry registry) {
    registry.add("insuhr.batch.snapshot-dir", SNAPSHOT_DIR::toString);
  }

  @Test
  @DisplayName("전 설계사 스냅샷 CSV에 헤더·트레일러가 있고, .sha256 해시가 파일 내용과 일치한다")
  void writesCsvWithTrailerAndMatchingChecksum() throws Exception {
    long orgId = seedOrg();
    LocalDate far = LocalDate.of(2030, 1, 1);
    seedActiveEligibleLifeAgent(seedPerson(), orgId, far);
    seedActiveEligibleLifeAgent(seedPerson(), orgId, far);
    LocalDate targetDate = LocalDate.of(2026, 7, 18);

    JobExecution exec = runJob(hrSnapshotFileJob, targetDate);
    assertThat(isCompleted(exec)).isTrue();

    Path csv = SNAPSHOT_DIR.resolve("INSUHR_AGENT_FULL_20260718.csv");
    Path checksum = SNAPSHOT_DIR.resolve("INSUHR_AGENT_FULL_20260718.csv.sha256");
    assertThat(csv).exists();
    assertThat(checksum).exists();

    List<String> lines = Files.readAllLines(csv);
    assertThat(lines.get(0)).isEqualTo("AGENT_CD,STATUS_CD,ORG_ID,RECRUIT_ELIG_YN");
    assertThat(lines).as("트레일러 #COUNT=2").anyMatch(l -> l.equals("#COUNT=2"));

    String expectedHex =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(csv)));
    assertThat(Files.readString(checksum))
        .as("체크섬 파일의 해시가 CSV 실제 바이트의 SHA-256과 일치")
        .startsWith(expectedHex);
  }

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("insuhr-snapshot");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
