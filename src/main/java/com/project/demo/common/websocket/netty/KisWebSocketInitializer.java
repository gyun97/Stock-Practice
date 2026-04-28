package com.project.demo.common.websocket.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Netty 채널 파이프라인 구성.
 *
 * <pre>
 * 파이프라인 구조:
 *  [SslHandler]            - wss:// 대응 (ws:// 는 생략)
 *  [IdleStateHandler]      - 60초 read idle → 연결 끊김 감지 트리거
 *  [HttpClientCodec]       - HTTP 요청/응답 인/디코딩
 *  [HttpObjectAggregator]  - HTTP 청크 조합 (최대 64KB)
 *  [WebSocketClientProtocolHandler] - WebSocket 핸드쉐이크 자동 처리
 *  [HandshakeLatchHandler] - 핸드쉐이크 완료 시 CountDownLatch 신호 (일회성)
 *  [KisWebSocketHandler]   - 비즈니스 로직 (메시지 수신 → Redis Streams 발행)
 * </pre>
 */
public class KisWebSocketInitializer extends ChannelInitializer<SocketChannel> {

    private final URI uri;
    private final KisWebSocketHandler kisWebSocketHandler;
    private final SslContext sslContext; // wss:// 일 경우만 non-null
    private final CountDownLatch handshakeLatch;

    public KisWebSocketInitializer(URI uri, KisWebSocketHandler kisWebSocketHandler,
                                   SslContext sslContext, CountDownLatch handshakeLatch) {
        this.uri = uri;
        this.kisWebSocketHandler = kisWebSocketHandler;
        this.sslContext = sslContext;
        this.handshakeLatch = handshakeLatch;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // wss:// 지원
        if (sslContext != null) {
            pipeline.addLast("ssl", sslContext.newHandler(ch.alloc(), uri.getHost(), getPort()));
        }

        // 60초간 읽기 이벤트 없으면 Stale Connection으로 판단
        pipeline.addLast("idleState", new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));

        // HTTP 코덱 (WebSocket 핸드쉐이크는 HTTP 업그레이드 방식)
        pipeline.addLast("httpCodec", new HttpClientCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(65536));

        // WebSocket 핸드쉐이크 자동 처리
        pipeline.addLast("wsProtocol", new WebSocketClientProtocolHandler(
                io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory.newHandshaker(
                        uri,
                        WebSocketVersion.V13,
                        null,
                        false,
                        new io.netty.handler.codec.http.DefaultHttpHeaders()
                )
        ));

        // 핸드쉐이크 완료 감지 → CountDownLatch countdown (일회성 핸들러)
        // WebSocketClientProtocolHandler는 핸드쉐이크 완료 시
        // ClientHandshakeStateEvent.HANDSHAKE_COMPLETE 이벤트를 userEventTriggered로 전달함
        pipeline.addLast("handshakeLatch", new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof WebSocketClientProtocolHandler.ClientHandshakeStateEvent stateEvent
                        && stateEvent == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                    handshakeLatch.countDown(); // doConnect()의 latch.await() 해제
                    ctx.pipeline().remove(this); // 일회성이므로 파이프라인에서 제거
                }
                super.userEventTriggered(ctx, evt);
            }
        });

        // 비즈니스 핸들러 (수신 메시지 → Redis Streams)
        pipeline.addLast("kisHandler", kisWebSocketHandler);
    }

    private int getPort() {
        int port = uri.getPort();
        if (port == -1) {
            return "wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return port;
    }
}
