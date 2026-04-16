import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomItem } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export let options = {
    stages: [
        { duration: '1m', target: 100 },
        { duration: '3m', target: 500 },
        { duration: '5m', target: 1000 },
        { duration: '2m', target: 0 }
    ],
    thresholds: {
        // 포토폴리오 조회(LOOKUP) 성능에 대해서만 엄격한 기준 적용 가능
        'http_req_duration{name:LOOKUP}': ['p(95)<1000'],
    },
};

// 회원님의 로컬 Redis에 실제로 존재하는 주식 티커 5개로 수정 (네이버, 카카오 등)
const tickers = ['010130', '024110', '034020', '035420', '035720'];

let vuToken = null;
let vuUserId = null;

export default function () {
    // [1. SEEDING] 로그인 및 매수는 각 VU별로 최초 1회만 수행
    if (!vuToken) {
        // 게스트 로그인 (SEEDING 태그)
        const loginRes = http.post('http://localhost:8888/api/v1/users/guest-login', null, {
            tags: { name: 'SEEDING' }
        });

        if (loginRes.status !== 200 && loginRes.status !== 201) return;

        vuToken = loginRes.json().data.accessToken;
        vuUserId = loginRes.json().data.userId;

        const authParams = {
            headers: { 'Authorization': `Bearer ${vuToken}`, 'Content-Type': 'application/json' },
            tags: { name: 'SEEDING' }
        };

        // 초기 주식 매수 (SEEDING 태그)
        for (let i = 0; i < 3; i++) {
            const ticker = randomItem(tickers);
            http.post(`http://localhost:8888/api/v1/orders/buying/${ticker}?quantity=10`, null, authParams);
        }

        // 데이터가 DB에 반영될 시간을 잠깐 줍니다.
        sleep(1);
    }

    // [2. LOOKUP] 측정하고자 하는 핵심 대상
    const url = `http://localhost:8888/api/v1/portfolios/users/${vuUserId}`;
    let params = {
        headers: { 'Authorization': `Bearer ${vuToken}` },
        tags: { name: 'LOOKUP' }, // 이 태그로 나중에 따로 뽑아볼 수 있습니다.
        timeout: '10s',
    };

    let res = http.get(url, params);

    check(res, {
        'is status 200': (r) => r.status === 200,
        'has stock data': (r) => r.json().data !== null,
    });

    sleep(2);
}
