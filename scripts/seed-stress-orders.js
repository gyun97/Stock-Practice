const http = require('http');

const TARGET_ORDERS = 100000; // 10만 건
const CONCURRENCY = 1000; // 동시성 극대화

const agent = new http.Agent({
  keepAlive: true,
  maxSockets: CONCURRENCY,
  keepAliveMsecs: 1000
});

// InitStockSubscribe.FIXED_TICKERS 리스트 (40개 종목)
const TICKERS = [
  "005930", "000660", "373220", "207940", "005380", "068270", "000270", "005935", "005490", "105560",
  "028260", "055550", "035420", "000810", "012330", "066570", "051910", "006400", "086790", "032830",
  "010130", "329180", "035720", "015760", "003550", "034730", "011200", "018260", "009150", "034020",
  "010140", "024110", "096770", "042660", "012450", "316140", "001450", "267250", "033780", "000100"
];

const TARGET_PRICE = 100000; // 기준 가격

async function requestAPI(path, method = 'GET', body = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: 8888,
      path: path,
      method: method,
      agent: agent, // 에이전트 적용
      headers: {
        'Content-Type': 'application/json',
        ...headers
      }
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { resolve(JSON.parse(data)); } catch (e) { resolve(data); }
        } else {
          let errorMsg = `Status: ${res.statusCode}`;
          try {
            const parsed = JSON.parse(data);
            if (parsed.message) errorMsg += ` - ${parsed.message}`;
          } catch (e) { }
          reject(new Error(errorMsg));
        }
      });
    });

    req.on('error', (e) => reject(e));
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

async function loginAsGuest() {
  console.log('게스트 유저를 생성/로그인 합니다...');
  try {
    const res = await requestAPI('/api/v1/users/guest-login', 'POST');
    console.log(`로그인 성공! UserId: ${res.data.userId}`);
    return res.data.accessToken;
  } catch (error) {
    console.error('로그인 실패:', error.message);
    process.exit(1);
  }
}

let successCount = 0;
let failCount = 0;

async function seedOrder(userToken) {
  const quantity = 1;
  const ticker = TICKERS[Math.floor(Math.random() * TICKERS.length)];
  const path = `/api/v1/orders/reserve-buying/${ticker}?quantity=${quantity}&targetPrice=${TARGET_PRICE}`;

  try {
    await requestAPI(path, 'POST', null, { 'Authorization': userToken });
    successCount++;
  } catch (error) {
    failCount++;
    if (failCount % 100 === 1) {
      console.error(`[에러] ${error.message}`);
    }
  }

  if (successCount + failCount < TARGET_ORDERS) {
    await seedOrder(userToken);
  }
}

async function main() {
  const token = await loginAsGuest();

  console.log(`\n================================`);
  console.log(`총 ${TICKERS.length}개 종목에 대해 ${TARGET_ORDERS}개의 예약 주문 시딩을 시작합니다.`);
  console.log(`목표 가격: ${TARGET_PRICE}원`);
  console.log(`================================\n`);

  const startTime = Date.now();
  const workers = Array(CONCURRENCY).fill(0).map(() => seedOrder(token));

  const logInterval = setInterval(() => {
    console.log(`[진행 상황] 성공: ${successCount}, 실패: ${failCount} / 총 목표: ${TARGET_ORDERS}`);
    if (successCount + failCount >= TARGET_ORDERS) {
      clearInterval(logInterval);
      const elapsed = (Date.now() - startTime) / 1000;
      console.log(`\n데이터 시딩 완료! 소요 시간: ${elapsed.toFixed(2)}s`);
      process.exit(0);
    }
  }, 1000);

  await Promise.all(workers);
}

main();
