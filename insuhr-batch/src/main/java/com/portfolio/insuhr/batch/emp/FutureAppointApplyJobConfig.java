package com.portfolio.insuhr.batch.emp;

import com.portfolio.insuhr.batch.support.BatchTargetDate;
import com.portfolio.insuhr.batch.support.LoggingSkipListener;
import com.portfolio.insuhr.domain.emp.AppointmentApplyService;
import com.portfolio.insuhr.domain.emp.Emp;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.IteratorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code futureAppointApplyJob} — 발령일이 도래한 확정 발령을 스냅샷에 반영 + Outbox 발행 (설계서 8, 5.5, 시나리오 6b).
 *
 * <p><b>반영 규칙을 배치에 재구현하지 않는다</b> — 대상 추출도 반영도 도메인 {@link AppointmentApplyService}를 감싸기만 한다. 재구현하면
 * 온라인 확정 경로와 배치 경로가 갈라진다(설계서 13.2).
 *
 * <ul>
 *   <li>Reader: {@code findEmpIdsDueOn(targetDate)}(단일 출처) — 발령일이 도래한 CONFIRMED 발령의 EMP_ID. 일자
 *       도래분이라 작다.
 *   <li>Processor: {@code recalculateAsOf(empId, targetDate)}(멱등 — 재계산). 스냅샷이 실제로 바뀐 경우에만 {@code
 *       emp.appointment.applied}를 발행한다 — 온라인 {@code EmployeeService}와 같은 모양이라 재실행해도 이벤트가 중복되지 않는다
 *       ({@link Emp#applySnapshot}가 변화 없을 때 false).
 * </ul>
 */
@Configuration
public class FutureAppointApplyJobConfig {

  private static final int CHUNK = 100;

  @Bean
  public Job futureAppointApplyJob(JobRepository jobRepository, Step futureAppointApplyStep) {
    return new JobBuilder("futureAppointApplyJob", jobRepository)
        .start(futureAppointApplyStep)
        .build();
  }

  @Bean
  public Step futureAppointApplyStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      IteratorItemReader<Long> dueEmpReader,
      ItemProcessor<Long, Long> appointApplyProcessor) {
    return new StepBuilder("futureAppointApplyStep", jobRepository)
        .<Long, Long>chunk(CHUNK)
        .reader(dueEmpReader)
        .processor(appointApplyProcessor)
        .writer(noOpWriter()) // 반영·발행은 프로세서가 끝낸다
        .transactionManager(transactionManager)
        .faultTolerant()
        .skip(Exception.class)
        .skipLimit(10_000)
        .skipListener(new LoggingSkipListener<Long, Long>())
        .build();
  }

  @Bean
  @StepScope
  public IteratorItemReader<Long> dueEmpReader(
      AppointmentApplyService appointmentApplyService,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    return new IteratorItemReader<>(appointmentApplyService.findEmpIdsDueOn(asOf));
  }

  @Bean
  @StepScope
  public ItemProcessor<Long, Long> appointApplyProcessor(
      AppointmentApplyService appointmentApplyService,
      EmpRepository empRepository,
      IntegrationRecorder integrationRecorder,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    return empId -> {
      boolean changed = appointmentApplyService.recalculateAsOf(empId, asOf);
      if (changed) {
        // 온라인 EmployeeService.publishAppointmentApplied와 같은 이벤트 모양(민감정보 없이 업무키+스냅샷).
        Emp emp = empRepository.findById(empId).orElseThrow();
        integrationRecorder.record(
            IntegrationEvent.updated(
                "emp.appointment.applied", "EMP", emp.getId(), emp.getEmpNo(), emp.toSnapshot()));
      }
      return empId;
    };
  }

  private static ItemWriter<Long> noOpWriter() {
    return chunk -> {};
  }
}
