// 예약 주문 체결 테스트용 스크립트

const WebSocket = require('ws');

// 8081 포트에서 가짜 KIS 웹소켓 서버 실행
const wss = new WebSocket.Server({ port: 8081, host: '0.0.0.0' });
console.log('Stress Trigger Mock Server가 8081 포트에서 시작되었습니다.');
console.log('애플리케이션이 연결되면 90,000원(삼성전자) 가격 정보를 전송하여 체결을 유도합니다.');

wss.on('connection', function connection(ws) {
  console.log('애플리케이션(클라이언트)이 연결되었습니다!');

  // 연결 후 2초 뒤에 트리거 전송 (시스템 초기화 대기)
  setTimeout(() => {
    const ticker = '005930';
    const time = '120000';
    const price = 90000; // 예약가 100,000원보다 낮게 설정하여 체결 유도

    const fakeData = `0|H0STCNT0|001|${ticker}^${time}^${price}^0^100^0.5^0^0^0^0^0^0^0^1000000`;

    console.log(`[트리거] ${ticker}의 가격 정보를 전송합니다 (현재가: ${price})`);
    ws.send(fakeData);

    console.log('트리거 전송 완료. 10초 후 서버를 종료합니다.');
    setTimeout(() => {
      console.log('서버 종료.');
      process.exit(0);
    }, 10000);
  }, 2000);

  ws.on('message', function incoming(message) {
    console.log('수신:', message.toString());
  });
});
