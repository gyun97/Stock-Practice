package com.project.demo.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Streams Consumer 설정.
 *
 * <pre>
 * 기능:
 *  1. Consumer 구동을 위한 비동기 스레드 풀(ThreadPoolTaskExecutor) 정의
 *  2. Redis Stream 키 및 Consumer Group 이 존재하지 않으면 초기화 (XGROUP CREATE)
 *  3. StreamMessageListenerContainer에 Consumer 연결
 * </pre>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    @org.springframework.context.annotation.Lazy
    private final RedisStreamConsumer streamConsumer;
    private final StringRedisTemplate redisTemplate;

    private static final String STREAM_KEY = RedisStreamProducer.STREAM_KEY;
    private static final String CONSUMER_GROUP = "stock-group";
    private static final String CONSUMER_NAME = "worker-1";

    /** 컨테이너 재시작 중복 방지 플래그 */
    private final AtomicBoolean restarting = new AtomicBoolean(false);

    /**
     * Consumer 처리를 위한 스레드 풀 정의.
     * Netty 수신 스레드와 병목을 분리하기 위해 별도의 스레드 풀을 사용합니다.
     */
    @Bean
    public ThreadPoolTaskExecutor streamTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("StreamConsumer-");
        executor.initialize();
        return executor;
    }

    /**
     * Producer(XADD) 처리를 위한 전용 스레드 풀.
     * Netty EventLoop를 블로킹하지 않기 위해 별도로 분리합니다.
     */
    @Bean
    public ThreadPoolTaskExecutor producerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);      // 4 -> 8 상향
        executor.setMaxPoolSize(16);     // 8 -> 16 상향
        executor.setQueueCapacity(2000); // 200 -> 2000 상향 (데이터 유실 방지)
        executor.setThreadNamePrefix("StreamProducer-");
        executor.initialize();
        return executor;
    }

    /**
     * StreamMessageListenerContainer 설정 및 리스너 등록
     */
    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ThreadPoolTaskExecutor streamTaskExecutor) {

        // 1. 스트림과 컨슈머 그룹 초기화
        initStreamAndGroup();

        // 2. 컨테이너 옵션 설정
        //    - pollTimeout 200ms: Redis timeout(3s)과 충분한 간격을 두어 NPE 방지
        //    - errorHandler: NullPointerException(records=null) 발생 시 컨테이너 재시작
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container = buildContainer(
                connectionFactory, streamTaskExecutor);

        // 3. 컨테이너 반환 (Spring Lifecycle에 의해 자동으로 start()됨)
        log.info("Redis Stream Consumer Container 설정 완료");

        return container;
    }

    /**
     * 컨테이너 인스턴스를 생성하고 리스너를 등록합니다.
     * errorHandler에서 재시작 시 동일 팩토리 메서드를 재사용합니다.
     */
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> buildContainer(
            RedisConnectionFactory connectionFactory,
            ThreadPoolTaskExecutor streamTaskExecutor) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .<String, MapRecord<String, String, String>>builder()
                        // Redis timeout(3s)보다 훨씬 작게 유지. XREAD 결과가 null로 오는 원인을 줄입니다.
                        .pollTimeout(Duration.ofMillis(200))
                        .executor(streamTaskExecutor)
                        .errorHandler(t -> {
                            log.error("=== REDIS STREAM CONSUMER CRITICAL ERROR ===");
                            log.error("에러 메시지: {}", t.getMessage());
                            log.error("에러 원인 클래스: {}", t.getClass().getName());

                            // NullPointerException(records=null)은 Spring Data Redis 의
                            // StreamPollTask 가 Redis 응답 timeout 후 null 결과를 역직렬화하려다
                            // 발생합니다. 컨테이너 자체가 죽어 수신이 멈추므로 재시작합니다.
                            if (t instanceof NullPointerException) {
                                log.warn("StreamPollTask NPE 감지 — 컨테이너 재시작 시도");
                                restartContainer(connectionFactory, streamTaskExecutor);
                            }
                        })
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                streamConsumer);

        return container;
    }

    /**
     * 컨테이너를 비동기로 중지 후 재시작합니다.
     * AtomicBoolean 으로 중복 재시작을 방지합니다.
     */
    private void restartContainer(
            RedisConnectionFactory connectionFactory,
            ThreadPoolTaskExecutor streamTaskExecutor) {

        if (!restarting.compareAndSet(false, true)) {
            log.warn("컨테이너 재시작이 이미 진행 중입니다.");
            return;
        }

        Thread restartThread = new Thread(() -> {
            try {
                Thread.sleep(2000); // 2초 대기 후 재시작 (Redis 과부하 일시 완화)
                log.info("Redis Stream Consumer Container 재시작 중...");
                initStreamAndGroup(); // 그룹 재확인
                StreamMessageListenerContainer<String, MapRecord<String, String, String>> newContainer =
                        buildContainer(connectionFactory, streamTaskExecutor);
                newContainer.start(); // 재시작 시에는 수동으로 start() 호출 필요 (이미 컨텍스트가 뜬 상태이므로)
                log.info("Redis Stream Consumer Container 재시작 완료");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("컨테이너 재시작 스레드 인터럽트");
            } catch (Exception e) {
                log.error("컨테이너 재시작 실패: {}", e.getMessage(), e);
            } finally {
                restarting.set(false);
            }
        }, "StreamContainer-Restart");
        restartThread.setDaemon(true);
        restartThread.start();
    }

    /**
     * 애플리케이션 기동 시 스트림 및 Consumer Group 초기화
     */
    private void initStreamAndGroup() {
        try {
            boolean hasKey = Boolean.TRUE.equals(redisTemplate.hasKey(STREAM_KEY));
            if (!hasKey) {
                // 스트림 키가 없으면 그룹 생성 전 XADD로 키를 먼저 만들어야 함 (Redis 5+ 기준 XGROUP CREATE MKSTREAM)
                // MKSTREAM 옵션을 지원하기 위해 opsForStream().createGroup 사용
                redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
                log.info("Redis Stream 및 Consumer Group 생성 완료: {}", CONSUMER_GROUP);
            } else {
                // 키는 있는데 그룹이 있는지 확인
                boolean groupExists = false;
                var groups = redisTemplate.opsForStream().groups(STREAM_KEY);
                for (var info : groups) {
                    if (CONSUMER_GROUP.equals(info.groupName())) {
                        groupExists = true;
                        break;
                    }
                }
                if (!groupExists) {
                    redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
                    log.info("기존 Stream에 Consumer Group 생성 완료: {}", CONSUMER_GROUP);
                } else {
                    log.info("Consumer Group 이미 존재함: {}", CONSUMER_GROUP);
                }
            }
        } catch (Exception e) {
            log.warn("Redis Stream/Group 초기화 중 예외 발생 (이미 존재할 가능성 높음): {}", e.getMessage());
        }
    }
}
