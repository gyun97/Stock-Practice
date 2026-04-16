## 💡 기획 배경

주식 투자를 연습하고 싶지만 주식을 할 자본금이 부족한 사람들을 위한 실시간 주식 데이터를 활용한 모의 투자 플랫폼입니다. 한국투자증권(KIS) API를 연동하여 실제 주식 시세를 기반으로 매수/매도, 포트폴리오 관리, 랭킹 시스템 등을 제공합니다.



<br>
<br>


## 📋 목차

- [프로젝트 개요](#프로젝트-개요)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [ERD](#ERD)
- [아키텍처 구조도](#아키텍처)
- [API 문서](#api-문서)
- [데이터 흐름도](#data-flow)
- [주요 기능](#주요-기능)



<br>
<br>

## 🎯 프로젝트 개요

이 프로젝트는 **Spring Boot**와 **React**를 기반으로 한 실시간 주식 모의 투자 플랫폼입니다. 한국투자증권(KIS) Open API를 활용하여 실제 주식 시세를 실시간으로 받아오고, 사용자가 모의 투자를 통해 주식 거래를 체험할 수 있습니다.

### 주요 특징

- 🔐 **JWT 기반 인증 시스템** (Access Token + Refresh Token)
- 🔄 **실시간 주가 데이터** (WebSocket + STOMP)
- 📊 **인터랙티브 차트** (Lightweight Charts)
- 💼 **포트폴리오 관리** 및 **랭킹 시스템**
- ⚡ **즉시 주문** 및 **예약 주문** 기능
- 🌐 **OAuth2 소셜 로그인** (카카오, 네이버)
- 🧪 **단위, 통합 테스트** (Mocktio, Testcontainers)



<br>
<br>

## 🛠 기술 스택

### Backend
![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=Spring&logoColor=white)
![Spring](https://img.shields.io/badge/springsecurity-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![Spring](https://img.shields.io/badge/springwebflux-6DB33F?style=for-the-badge&logo=springwebflux&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![Redis](https://img.shields.io/badge/redis-%23DD0031.svg?style=for-the-badge&logo=redis&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-000000?style=for-the-badge&logo=websocket&logoColor=white)
![OAuth 2.0](https://img.shields.io/badge/OAuth%202.0-3C8DBC?style=for-the-badge&logo=oauth&logoColor=white)
![STOMP](https://img.shields.io/badge/STOMP-7952B3?style=for-the-badge&logo=stomp&logoColor=white)
![MySQL](https://img.shields.io/badge/mysql-4479A1.svg?style=for-the-badge&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![GitHub](https://img.shields.io/badge/github-%23121011.svg?style=for-the-badge&logo=github&logoColor=white)


- **Java 17** 
- **Spring Boot 3.5.6**
  - Spring Security
  - Spring Data JPA
  - Spring WebSocket (STOMP)
  - Spring WebFlux (WebClient)
- **MySQL 8.0+** (Flyway 마이그레이션)
- **Redis Sentinel** (실시간 데이터 캐싱 및 정렬)
- **JWT** (io.jsonwebtoken)
- **OAuth2 Client** (카카오, 네이버)
- **Docker**

### Frontend

![React](https://img.shields.io/badge/React-18.3.1-61DAFB?style=for-the-badge&logo=react&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5%2B-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-4%2B-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![React Router](https://img.shields.io/badge/React%20Router-CA4245?style=for-the-badge&logo=reactrouter&logoColor=white)
![STOMP.js](https://img.shields.io/badge/STOMP.js-WebSocket%20Messaging-0A76B1?style=for-the-badge)
![Lightweight%20Charts](https://img.shields.io/badge/Lightweight%20Charts-TradingView-131722?style=for-the-badge&logo=tradingview&logoColor=white)


- **React 18.3.1**
- **TypeScript**
- **Vite**
- **Lightweight Charts** (TradingView 차트 라이브러리)
- **STOMP.js** (WebSocket 통신)
- **React Router DOM**

### Testing
![JUnit5](https://img.shields.io/badge/JUnit5-FB4F14?style=for-the-badge&logo=JUnit5&logoColor=white)
![Mockito](https://img.shields.io/badge/Mockito-4.x-009639?style=for-the-badge&logo=mockito&logoColor=white)
![MockMvc](https://img.shields.io/badge/MockMvc-Spring%20Test-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Testcontainers](https://img.shields.io/badge/Testcontainers-MySQL%20%7C%20Redis-0DB7ED?style=for-the-badge&logo=testcontainers&logoColor=white)


- **JUnit 5**
- **Mockito**
- **Testcontainers** (MySQL, Redis)
- **MockMvc**



<br>
<br>

## 📁 프로젝트 구조

```
stock/
├── src/main/java/com/project/demo/
│   ├── common/                    # 공통 모듈
│   │   ├── config/                # 설정 클래스
│   │   ├── exception/             # 예외 처리
│   │   ├── jwt/                   # JWT 유틸리티
│   │   ├── kis/                   # KIS API 연동
│   │   ├── oauth2/                # OAuth2 설정
│   │   ├── redis/                 # Redis 설정
│   │   ├── response/              # 공통 응답 형식
│   │   ├── util/                  # 유틸리티
│   │   └── websocket/             # WebSocket 설정
│   ├── domain/                    # 도메인별 모듈
│   │   ├── execution/             # 체결 내역
│   │   ├── order/                 # 주문
│   │   ├── portfolio/             # 포트폴리오
│   │   ├── stock/                 # 주식 정보
│   │   ├── user/                  # 사용자
│   │   └── userstock/             # 보유 주식
│   └── StockApplication.java      # 메인 애플리케이션
├── src/main/resources/
│   ├── application.yml            # 설정 파일
│   └── db/migration/mysql/        # Flyway 마이그레이션
├── src/test/                      # 테스트 코드
│   ├── java/.../integration/      # 통합 테스트
│   └── java/.../domain/           # 단위 테스트
└── frontend/                      # React 프론트엔드
    ├── src/
    │   ├── pages/                 # 페이지 컴포넌트
    │   ├── components/            # 공통 컴포넌트
    │   └── lib/                   # 유틸리티
    └── package.json
```



<br>
<br>



## ERD

<img width="1154" height="852" alt="image" src="https://github.com/user-attachments/assets/989e45c5-d96f-4592-992c-a83fddcf72c9" />



<br>
<br>



## 아키텍처 구조도

<img width="808" height="774" alt="image" src="https://github.com/user-attachments/assets/02990d1b-b262-42fe-ae2e-30836232fa76" />





<br>
<br>


## 📡 API 문서

### WebSocket 엔드포인트

| 경로 | 설명 |
|------|------|
| `/ws` | WebSocket 연결 엔드포인트 (SockJS 지원) |
| `/topic/stocks` | 실시간 주식 시세 구독 |
| `/topic/portfolio/{userId}` | 포트폴리오 업데이트 알림 |
| `/topic/user-stocks/{userId}` | 보유 주식 업데이트 알림 |
| `/topic/order-notification/{userId}` | 주문 체결 알림 |

<br>

### 사용자 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/users/sign-up` | 회원가입 |
| POST | `/api/v1/users/login` | 로그인 |
| POST | `/api/v1/users/logout` | 로그아웃 |
| POST | `/api/v1/users/reissue` | Access Token 재발급 |
| GET | `/api/v1/users/{userId}` | 사용자 정보 조회 |
| PATCH | `/api/v1/users/{userId}` | 사용자 정보 수정 |
| PATCH | `/api/v1/users/password` | 비밀번호 변경 |
| DELETE | `/api/v1/users/{userId}` | 회원 탈퇴 |

<br>

### 주식 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/stocks` | 전체 주식 목록 조회 |
| GET | `/api/v1/stocks/{ticker}/outline` | 기업 개요 조회 |
| GET | `/api/v1/stocks/{ticker}/period` | 기간별 차트 데이터 |
| GET | `/api/v1/stocks/{ticker}/period/range` | 기간별 차트 데이터 (범위 지정) |

<br>

### 주문 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/orders/buying/{ticker}` | 즉시 매수 |
| POST | `/api/v1/orders/selling/{ticker}` | 즉시 매도 |
| POST | `/api/v1/orders/reserve-buying/{ticker}` | 예약 매수 |
| POST | `/api/v1/orders/reserve-selling/{ticker}` | 예약 매도 |
| DELETE | `/api/v1/orders/{orderId}` | 예약 주문 취소 |
| GET | `/api/v1/orders` | 전체 주문 내역 |
| GET | `/api/v1/orders/normal` | 일반 주문 내역 |
| GET | `/api/v1/orders/reservation` | 예약 주문 내역 |

<br>

### 포트폴리오 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/portfolios/users/{userId}` | 포트폴리오 조회 |
| GET | `/api/v1/portfolios/ranking` | 랭킹 조회 |




<br>
<br>


## 데이터 흐름도

```mermaid
sequenceDiagram
    participant User as 사용자
    participant Frontend as React Frontend
    participant Controller as Controller
    participant Service as Service
    participant DB as MySQL
    participant Redis as Redis
    participant KIS as KIS API/WebSocket

    User->>Frontend: 로그인 요청
    Frontend->>Controller: POST /api/v1/users/signup
    Controller->>Service: signUp()
    Service->>DB: 사용자 저장
    Service->>Redis: Refresh Token 저장
    Service-->>Controller: JWT 토큰 반환
    Controller-->>Frontend: LoginResponse
    Frontend-->>User: 로그인 완료

    Note over KIS,Redis: 실시간 주가 업데이트
    KIS->>Redis: 주가 데이터 저장
    KIS->>Service: 주가 업데이트 이벤트
    Service->>Service: 예약 주문 체결 확인
    Service->>DB: 주문 체결 저장
    Service->>Redis: 포트폴리오 업데이트
    Service->>Frontend: WebSocket 알림
    Frontend->>User: 실시간 업데이트 표시
```


<br>
<br>


## ✨ 주요 기능

### 1. 사용자 인증 및 관리

#### 1.1 회원가입 / 로그인 (JWT 토큰 기반 인증)
- 이메일 기반 회원가입
  
![Image](https://github.com/user-attachments/assets/bd1182aa-cf2f-4d3b-a5b7-9f7a6fe1d285)

<br>
  
- 소셜 로그인 (카카오, 네이버)
  
![Image](https://github.com/user-attachments/assets/5d29adba-e95d-44e1-bfc3-2ea83f21cfe8)

<br>

![Image](https://github.com/user-attachments/assets/49e54c57-7c84-4715-9d1a-f309332f91f4)

<br>

#### 1.2 토큰 재발급
- Refresh Token을 이용한 Access Token 자동 갱신
- Access Token (60분) + Refresh Token (14일)
- 쿠키 기반 Refresh Token 관리

<br>

#### 1.3 사용자 정보 관리
- 개인정보 조회/수정
- 비밀번호 변경
- 회원 탈퇴
  
![Image](https://github.com/user-attachments/assets/0cf89e06-f69f-4117-ba38-62c9df9212a5)



<br>
<br>



### 2. 실시간 주식 데이터

#### 2.1 주식 정보 조회
- 전체 주식 목록 조회 (거래량, 가격, 급상승, 급하락 순 정렬)
- 실시간 현재가, 등락률, 거래량 조회

#### 2.2 실시간 주가 업데이트
- 한국투자증권 WebSocket을 통한 실시간 주가 수신
- Redis에 주가 데이터 저장 및 정렬
- STOMP를 통한 클라이언트 실시간 전송

![Image](https://github.com/user-attachments/assets/75842303-e386-426f-bf6e-83e3b6885a32)



<br>
<br>


### 3. 차트 및 시각화

#### 3.1 캔들스틱 차트
- Trading View의 Light-Weight 캔들 차트 라이브러리 이용
- 일/주/월/년 단위 선택 캔들 차트
- 무한 드래그를 통한 과거 데이터 및 차트 확인
- 툴팁으로 상세 정보 표시
- 각 종목별 기업 개요 정보 표시

![Image](https://github.com/user-attachments/assets/230bfd99-5672-44d1-bf95-cefd17b32f18)



<br>
<br>

### 4. 주문 시스템

#### 4.1 즉시 주문
- 즉시 매수: 현재가로 즉시 체결
- 즉시 매도: 보유 주식을 현재가로 즉시 매도

![Image](https://github.com/user-attachments/assets/990897d9-8779-4265-b8cb-e5d244979dd7)

<br>


#### 4.2 예약 주문
- 예약 매수: 목표가 이하로 하락 시 자동으로 매수 체결
- 예약 매도: 목표가 이상으로 상승 시 자동으로 매도 체결
- 예약 주문 체결시 웹 브라우저 알림 전송

![Image](https://github.com/user-attachments/assets/04ab899e-407a-4a02-838a-5d5c5496c901)


<br>

#### 4.3 주문 내역 조회
- 전체 주문 내역 조회
- 일반 주문(즉시 주문) 내역 조회
- 예약 주문 내역 조회
- 예약 주문 취소 기능

![Image](https://github.com/user-attachments/assets/b8c5b479-d0fb-43a4-b843-855b9ae1d6f6)




### 4. 포트폴리오 관리

#### 4.1 포트폴리오 조회
- 현금 잔액(자본금 1000만원)
- 보유 주식의 실시간 총액
- 총 자산(현금 + 보유 주식)
- 보유 종목 수
- 실시간 자산, 수익률 갱신

![Image](https://github.com/user-attachments/assets/ebf146f7-c9ab-4323-8be0-f17e473d1aa7)



<br>
<br>

## 🧪 테스트(라인 커버리지 72%)

### 단위 테스트
- Service 계층 단위 테스트 (Mockito)
- Controller 계층 테스트 (MockMvc)

### 통합 테스트
- Testcontainers를 이용한 MySQL, Redis 통합 테스트
- 실제 데이터베이스 환경에서 API 테스트





