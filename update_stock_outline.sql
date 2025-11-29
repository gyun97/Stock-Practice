-- 삼성전자 outline 업데이트
-- 이 스크립트를 데이터베이스에서 실행하세요

USE stock;

-- 삼성전자 outline 업데이트
UPDATE stocks 
SET outline = '삼성전자는 1969년에 설립된 대한민국의 대표적인 전자제품 및 반도체 기업으로, 수원에 본사를 두고 있습니다. 주요 사업 부문은 반도체 중심의 DS, 가전/스마트폰 중심의 DX, 그리고 자회사인 삼성디스플레이로 구성됩니다. 혁신적인 기술 개발과 글로벌 시장에서의 리더십을 유지하며, 최고의 제품과 서비스를 통한 인류 사회 공헌이라는 목표를 추구합니다.' 
WHERE ticker = '005930';

-- 업데이트 확인
SELECT ticker, name, outline FROM stocks WHERE ticker = '005930';

