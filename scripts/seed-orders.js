const http = require('http');

// ==========================================
// 2단계: 예약 주문 데이터 펌핑 (Data Seeding)
// 실행 방법: node scripts/seed-orders.js
// ==========================================

const TARGET_ORDERS = 10000;
const CONCURRENCY = 50;

const TICKERS = ['005930', '000660', '035420', '035720', '010130'];
let currentOrders = 0;
let successCount = 0;
let failCount = 0;

// 유저 토큰 보관
let userToken = '';
let userId = '';

// API 요청 헬퍼
function requestAPI(_path, method = 'POST', body = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: 8888,
      path: _path,
      method: method,
      headers: {
        'Content-Type': 'application/json',
        ...headers
      }
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { resolve(JSON.parse(data)); } catch (e) { resolve(data); }
        } else {
          let errorMsg = `Status: ${res.statusCode}`;
          try {
            const parsed = JSON.parse(data);
            if (parsed.message) errorMsg += ` - ${parsed.message}`;
          } catch (e) {}
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
    // 포트와 경로는 애플리케이션에 맞게 수정하세요. 
    const res = await requestAPI('/api/v1/users/guest-login', 'POST');
    userToken = res.data.accessToken;
    userId = res.data.userId;
    console.log(`로그인 성공! UserId: ${userId}`);
    
    // 예약 주문을 넣으려면 지갑에 돈이 있어야 하므로 주식을 조금 사서 포트폴리오를 활성화하거나 돈을 충전해야 할 수 있습니다.
    // 시스템에 따라 돈이 있어야 예약이 가능하므로 여기서 별도 API를 호출해야 할 수도 있습니다.
  } catch (error) {
    console.error('로그인 실패 - 서버가 켜져 있는지 확인하세요:', error.message);
    process.exit(1);
  }
}

async function createReservation() {
  if (currentOrders >= TARGET_ORDERS) return;
  currentOrders++;

  const ticker = TICKERS[Math.floor(Math.random() * TICKERS.length)];
  const quantity = Math.floor(Math.random() * 5) + 1;
  const targetPrice = Math.floor(Math.random() * 80000 + 40000); // 40,000 ~ 120,000원 사이
  const orderType = 'buy'; // 대량 펌핑을 위해 매수 유형으로 고정 (매도는 주식 보유 필요)
  
  const orderPrefix = orderType === 'buy' ? 'reserve-buying' : 'reserve-selling';
  const path = `/api/v1/orders/${orderPrefix}/${ticker}?quantity=${quantity}&targetPrice=${targetPrice}`;

  try {
    // userToken에 이미 'Bearer '가 포함되어 있으므로 그대로 사용
    await requestAPI(path, 'POST', null, { 'Authorization': userToken });
    successCount++;
  } catch (error) {
    failCount++;
    // 시스템 부하를 줄이기 위해 에러가 많을 때만 로그 출력
    if (failCount % 100 === 1) {
      console.error(`[에러 발생] ${path} -> ${error.message}`);
    }
  }

  // 재귀적으로 동시성 유지
  createReservation();
}

async function main() {
  await loginAsGuest();
  console.log(`\n================================`);
  console.log(`총 ${TARGET_ORDERS}개의 예약 주문 펌핑을 시작합니다.`);
  console.log(`동시 요청 수(Concurrency): ${CONCURRENCY}`);
  console.log(`================================\n`);

  // CONCURRENCY 개수만큼 동시에 작업 시작
  for (let i = 0; i < CONCURRENCY; i++) {
    createReservation();
  }

  // 1초 단위로 로그 출력
  const logInterval = setInterval(() => {
    console.log(`[진행 상황] 성공: ${successCount}, 실패: ${failCount} / 총 목표: ${TARGET_ORDERS}`);
    if (successCount + failCount >= TARGET_ORDERS) {
      clearInterval(logInterval);
      console.log('데이터 펌핑이 성공적으로 완료되었습니다!');
      process.exit(0);
    }
  }, 1000);
}

main();
