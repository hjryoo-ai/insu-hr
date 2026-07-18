package com.portfolio.insuhr.relay;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/** 릴레이 배선 — 웹훅 전송용 {@link RestClient}와 스케줄링 (설계서 9.2). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RelayProperties.class)
public class RelayConfig {

  /** 웹훅 전송 전용 RestClient. 타임아웃을 짧게 둬 죽은 엔드포인트가 폴러를 붙잡지 않게 한다 — 실패는 재시도 큐로 넘긴다. */
  @Bean
  public RestClient relayRestClient(RelayProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
    return RestClient.builder().requestFactory(factory).build();
  }
}
