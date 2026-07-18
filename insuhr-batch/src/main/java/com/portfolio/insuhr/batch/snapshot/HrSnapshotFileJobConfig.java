package com.portfolio.insuhr.batch.snapshot;

import com.portfolio.insuhr.batch.support.BatchTargetDate;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileFooterCallback;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code hrSnapshotFileJob} — 설계사 전수 스냅샷 CSV + SHA-256 체크섬 파일 생성 (설계서 8, 9.5).
 *
 * <p>두 스텝: ① 청크 스텝이 {@code JdbcCursorItemReader}로 전 설계사를 스트리밍해 {@code
 * INSUHR_AGENT_FULL_{yyyyMMdd}.csv}(헤더 + 행 + 트레일러 {@code #COUNT=n})를 쓴다 ② 태스클릿이 그 파일의 SHA-256을 계산해
 * {@code .sha256} 체크섬 파일을 쓴다. 두 스텝은 {@code targetDate}(파일명)와 출력 디렉터리에서 같은 경로를 도출한다.
 *
 * <p>페이로드에 민감정보를 싣지 않는다(9.3 원칙 연장) — 업무키/코드 컬럼만. {@code targetDate}는 스냅샷 파일명에만 쓰이고 조회는 현재 전수
 * 상태다(state-carried).
 */
@Configuration
public class HrSnapshotFileJobConfig {

  private static final int CHUNK = 500;
  private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final String HEADER = "AGENT_CD,STATUS_CD,ORG_ID,RECRUIT_ELIG_YN";

  /** 스냅샷 한 행 — 엔티티가 아니라 전용 DTO(설계서 4.3 조회 전용 record). */
  public record AgentSnapshotRow(
      String agentCd, String statusCd, long orgId, String recruitEligYn) {}

  @Bean
  public Job hrSnapshotFileJob(
      JobRepository jobRepository, Step hrSnapshotWriteStep, Step hrSnapshotChecksumStep) {
    return new JobBuilder("hrSnapshotFileJob", jobRepository)
        .start(hrSnapshotWriteStep)
        .next(hrSnapshotChecksumStep)
        .build();
  }

  @Bean
  public Step hrSnapshotWriteStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      JdbcCursorItemReader<AgentSnapshotRow> agentSnapshotReader,
      FlatFileItemWriter<AgentSnapshotRow> agentSnapshotWriter) {
    return new StepBuilder("hrSnapshotWriteStep", jobRepository)
        .<AgentSnapshotRow, AgentSnapshotRow>chunk(CHUNK)
        .reader(agentSnapshotReader)
        .writer(agentSnapshotWriter)
        .transactionManager(transactionManager)
        .build();
  }

  @Bean
  public Step hrSnapshotChecksumStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      Tasklet agentSnapshotChecksumTasklet) {
    return new StepBuilder("hrSnapshotChecksumStep", jobRepository)
        .tasklet(agentSnapshotChecksumTasklet, transactionManager)
        .build();
  }

  @Bean
  @StepScope
  public JdbcCursorItemReader<AgentSnapshotRow> agentSnapshotReader(DataSource dataSource) {
    return new JdbcCursorItemReaderBuilder<AgentSnapshotRow>()
        .name("agentSnapshotReader")
        .dataSource(dataSource)
        .sql(
            "SELECT AGENT_CD, AGENT_STATUS_CD, ORG_ID, RECRUIT_ELIG_YN FROM TB_AGENT ORDER BY AGENT_ID")
        .rowMapper(
            (rs, rowNum) ->
                new AgentSnapshotRow(
                    rs.getString("AGENT_CD"),
                    rs.getString("AGENT_STATUS_CD"),
                    rs.getLong("ORG_ID"),
                    rs.getString("RECRUIT_ELIG_YN")))
        .build();
  }

  @Bean
  @StepScope
  public FlatFileItemWriter<AgentSnapshotRow> agentSnapshotWriter(
      @Value("${insuhr.batch.snapshot-dir:build/snapshot}") String snapshotDir,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    Path csv = agentFullCsvPath(snapshotDir, BatchTargetDate.resolve(stepExecution, clock));
    ensureDir(csv.getParent());

    DelimitedLineAggregator<AgentSnapshotRow> aggregator = new DelimitedLineAggregator<>();
    aggregator.setDelimiter(",");
    aggregator.setFieldExtractor(
        row -> new Object[] {row.agentCd(), row.statusCd(), row.orgId(), row.recruitEligYn()});

    // 트레일러 #COUNT=n. writeFooter는 마지막 행 뒤(스트림 close)에 불려 이 시점 writeCount가 확정이다(9.5).
    return new FlatFileItemWriterBuilder<AgentSnapshotRow>()
        .name("agentSnapshotWriter")
        .resource(new FileSystemResource(csv))
        .encoding(StandardCharsets.UTF_8.name())
        .shouldDeleteIfExists(true)
        .lineAggregator(aggregator)
        .headerCallback(w -> w.write(HEADER))
        .footerCallback(countFooter(stepExecution))
        .build();
  }

  @Bean
  @StepScope
  public Tasklet agentSnapshotChecksumTasklet(
      @Value("${insuhr.batch.snapshot-dir:build/snapshot}") String snapshotDir,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    Path csv = agentFullCsvPath(snapshotDir, BatchTargetDate.resolve(stepExecution, clock));
    return (contribution, chunkContext) -> {
      byte[] bytes = Files.readAllBytes(csv);
      String hex = sha256Hex(bytes);
      Path checksum = csv.resolveSibling(csv.getFileName() + ".sha256");
      // sha256sum 호환 포맷("<hex>  <파일명>") — 수신측이 표준 도구로 검증 가능(9.5).
      Files.writeString(
          checksum,
          hex + "  " + csv.getFileName() + System.lineSeparator(),
          StandardCharsets.UTF_8);
      return RepeatStatus.FINISHED;
    };
  }

  private static FlatFileFooterCallback countFooter(StepExecution stepExecution) {
    return new FlatFileFooterCallback() {
      @Override
      public void writeFooter(Writer writer) throws IOException {
        writer.write("#COUNT=" + stepExecution.getWriteCount());
      }
    };
  }

  static Path agentFullCsvPath(String snapshotDir, LocalDate targetDate) {
    return Path.of(snapshotDir, "INSUHR_AGENT_FULL_" + targetDate.format(FILE_DATE) + ".csv");
  }

  private static void ensureDir(Path dir) {
    try {
      if (dir != null) {
        Files.createDirectories(dir);
      }
    } catch (IOException e) {
      throw new IllegalStateException("스냅샷 디렉터리 생성 실패: " + dir, e);
    }
  }

  static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 미지원", e); // JDK 표준 — 발생 불가
    }
  }
}
