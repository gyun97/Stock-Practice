const http = require('http');

const TARGET_ORDERS = 100000; // 1만 건 
const CONCURRENCY = 100; // 시딩 속도를 위해 좀 더 높임
const TICKER = '005930';
const TARGET_PRICE = 100000; // 가격 10만으로 통일

async function requestAPI(path, method = 'GET', body = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: 8888,
      path: path,
      method: method,
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
  const path = `/api/v1/orders/reserve-buying/${TICKER}?quantity=${quantity}&targetPrice=${TARGET_PRICE}`;

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
  console.log(`종목 ${TICKER}에 대해 ${TARGET_ORDERS}개의 예약 주문 시딩을 시작합니다.`);
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
