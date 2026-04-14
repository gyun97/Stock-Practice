package com.project.demo.common.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfig {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    /**
     * datasource-micrometer 같은 라이브러리가 TracingObservationHandler를 먼저 등록하면
     * Spring Boot의 @ConditionalOnMissingBean 조건이 충족되어
     * 일반 @Observed 용 DefaultTracingObservationHandler가 등록되지 않습니다.
     *
     * 이 Customizer를 통해 명시적으로 등록하여
     * @Observed 메서드에서도 Span(분산 추적)이 반드시 생성되도록 보장합니다.
     */
    @Bean
    public ObservationRegistryCustomizer<ObservationRegistry> tracingObservationHandlerCustomizer(Tracer tracer) {
        return registry -> registry.observationConfig()
                .observationHandler(new DefaultTracingObservationHandler(tracer));
    }
}
