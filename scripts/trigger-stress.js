// 예약 주문 체결 테스트용 스크립트

const WebSocket = require('ws');

// 8081 포트에서 가짜 KIS 웹소켓 서버 실행
const wss = new WebSocket.Server({ port: 8081, host: '0.0.0.0' });
console.log('Stress Trigger Mock Server가 8081 포트에서 시작되었습니다.');
console.log('애플리케이션이 연결되면 모든 종목에 90,000원 가격 정보를 전송하여 체결을 유도합니다.');

wss.on('connection', function connection(ws) {
  console.log('애플리케이션(클라이언트)이 연결되었습니다!');

  const TICKERS = [
    "005930", "000660", "373220", "207940", "005380", "068270", "000270", "005935", "005490", "105560",
    "028260", "055550", "035420", "000810", "012330", "066570", "051910", "006400", "086790", "032830",
    "010130", "329180", "035720", "015760", "003550", "034730", "011200", "004020", "009150", "034020",
    "010140", "024110", "096770", "042660", "012450", "316140", "001450", "267250", "033780", "000100"
  ];

  // 모든 종목(40개)에 대해 순차적으로 1번씩만 전송
  const sendAllTickers = async () => {
    for (const ticker of TICKERS) {
      const time = '120000';
      const price = 90000;
      const fakeData = `0|H0STCNT0|001|${ticker}^${time}^${price}^0^100^0.5^0^0^0^0^0^0^0^1000000`;

      console.log(`[트리거] ${ticker} 데이터 전송 중...`);
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(fakeData);
      }
      // 애플리케이션이 안정적으로 처리할 수 있도록 50ms 간격 유지
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    console.log('모든 40개 종목 데이터 전송 완료. 서버를 종료합니다.');
    ws.close();
    process.exit(0);
  };

  sendAllTickers();

  ws.on('message', function incoming(message) {
    console.log('수신:', message.toString());
  });
});
