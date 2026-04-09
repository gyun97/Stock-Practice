const WebSocket = require('ws');

// 8081 포트에서 가짜 KIS 웹소켓 서버 실행
const wss = new WebSocket.Server({ port: 8081, host: '0.0.0.0' });
console.log('Mock KIS WebSocket 서버가 8081 포트에서 시작되었습니다.');

// 임의의 주식 티커 목록
const tickers = ['005930', '000660', '035420', '035720', '010130'];

wss.on('connection', function connection(ws) {
  console.log('스프링 부트(클라이언트)가 연결되었습니다!');

  ws.on('message', function incoming(message) {
    const msgString = message.toString();
    console.log('클라이언트로부터 구독 요청 수신:', msgString);

    // 앱이 연결되자마자 PINGPONG이나 구독 요청을 보냅니다.
    // 우리는 그냥 무시하고 주가 데이터를 미친듯이 쏘기 시작합니다.
  });

  let totalSent = 0;

  // 5분(300,000ms) 후에 테스트 자동 종료 설정
  const testDuration = 300000;
  setTimeout(() => {
    console.log('=== 5분간의 부하 테스트가 완료되어 서버를 종료합니다. ===');
    clearInterval(interval);
    clearInterval(logInterval);
    ws.close();
    process.exit(0);
  }, testDuration);

  // 초당 1000개의 주가 데이터를 쏘는 로직
  const interval = setInterval(() => {
    // 초당 1000건을 위해 한 번에 10개씩 발송 (100번/초 * 10 = 1000 TPS)
    for (let i = 0; i < 10; i++) {
      // 랜덤 종목, 변동 가격 생성
      const ticker = tickers[Math.floor(Math.random() * tickers.length)];
      const time = new Date().toISOString().split('T')[1].replace(/[:.Z]/g, '').substring(0, 6);
      const price = Math.floor(Math.random() * 100000) + 1000;

      // KIS 데이터 포맷: 암호화여부|...|...|티커^시간^현재가^...^전일대비^등락율^...^누적거래량
      // ConnectWebSocketClient.java의 parse 로직에 맞춤 (최소 14개 필드 필요)
      const fakeData = `0|H0STCNT0|001|${ticker}^${time}^${price}^0^100^0.5^0^0^0^0^0^0^0^1000000`;

      if (ws.readyState === WebSocket.OPEN) {
        ws.send(fakeData);
        totalSent++;
      }
    }
  }, 10);

  // 진행 상태 출력 (1초마다)
  const logInterval = setInterval(() => {
    console.log(`현재까지 쏜 가짜 주가 데이터 개수: ${totalSent}`);
  }, 1000);

  ws.on('close', () => {
    console.log('클라이언트 연결 종료');
    clearInterval(interval);
    clearInterval(logInterval);
  });
});
