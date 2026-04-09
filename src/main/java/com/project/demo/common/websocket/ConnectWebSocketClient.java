package com.project.demo.common.websocket;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/*
 * @Deprecated
 * Netty 기반의 KisNettyWebSocketClient 와 RedisStreamProducer, RedisStreamConsumer 로
 * 수신부와 처리부가 완전히 분리된 비동기(EDA) 구조로 전환되었습니다.
 * 이 클래스는 과거 레퍼런스 목적으로 남겨둡니다.
 */
@Slf4j
@Deprecated
// @Component 주석 처리하여 Spring Bean으로 등록되지 않게 함
public class ConnectWebSocketClient extends WebSocketClient {

    public ConnectWebSocketClient() throws Exception {
        super(new URI("ws://localhost"));
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
    }

    @Override
    public void onMessage(String message) {
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Exception ex) {
    }
}