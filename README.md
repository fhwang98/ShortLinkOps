# ShortLinkOps

> MyBatis SQL, Redis 캐시, Docker Compose, AWS EC2 배포를 포함한 Spring Boot 기반 URL 단축 서비스

## 1. 프로젝트 개요

**ShortLinkOps**는 긴 URL을 짧은 URL로 변환하고, 단축 URL 접속 시 원본 URL로 리다이렉트하는 Spring Boot 기반 URL 단축 서비스입니다.

단순 CRUD 구현에 그치지 않고, URL 리다이렉트 요청의 특성을 고려하여 Redis 캐시를 적용하고, MyBatis를 사용해 SQL을 직접 작성합니다. 또한 Docker Compose 기반으로 애플리케이션, MySQL, Redis, Nginx를 분리 실행하며, AWS EC2 환경에 배포 가능한 구조로 구성합니다.

## 2. 프로젝트 목표

- Spring Boot 기반 백엔드 API 구현
- MyBatis를 활용한 SQL 직접 작성
- MySQL 기반 URL 데이터 저장
- Redis 캐시를 통한 리다이렉트 조회 최적화
- Thymeleaf 기반 최소 화면 제공
- Docker Compose 기반 실행 환경 구성
- AWS EC2 단일 인스턴스 배포
- GitHub Actions를 활용한 빌드 자동화
- 운영 환경을 고려한 포트 노출 및 설정 분리

## 3. 기술 스택

### Backend

- Java 17
- Spring Boot 3.5.14
- Spring Web
- MyBatis
- Spring Validation
- Spring Boot Actuator
- Thymeleaf

### Database

- MySQL 8.x

### Cache

- Redis 7.x

### Infra / Deploy

- AWS EC2
- Docker
- Docker Compose
- Nginx

### CI/CD

- GitHub Actions
- Docker Hub

## 4. 주요 기능

### URL 단축

- 긴 URL을 입력받아 고유한 shortCode를 생성합니다.
- 생성된 shortCode를 기반으로 단축 URL을 제공합니다.

### URL 리다이렉트

- `/s/{shortCode}` 요청 시 원본 URL로 리다이렉트합니다.
- Redis 캐시를 먼저 조회하고, 캐시 미스 시 MySQL에서 조회합니다.

### 클릭 수 집계

- 단축 URL 접속 시 클릭 수를 증가시킵니다.
- 클릭 수 증가는 단일 UPDATE 쿼리로 처리합니다.

### URL 상세 조회

- 원본 URL, 단축 URL, 클릭 수, 생성일, 만료일을 조회할 수 있습니다.

### 만료 처리

- 만료 시간이 지난 단축 URL은 리다이렉트하지 않습니다.
- 만료된 URL 요청 시 `410 Gone` 응답을 반환합니다.

### 기본 화면 제공

- Thymeleaf를 사용하여 URL 생성 화면, 결과 화면, 상세 화면을 제공합니다.

## 5. 아키텍처

```text
User
  |
  v
EC2 Public IP or Domain
  |
  v
Nginx Container :80
  |
  v
Spring Boot Container :8080
  |
  +--> MySQL Container :3306
  |
  +--> Redis Container :6379
```

외부에는 Nginx의 80 포트만 노출합니다. Spring Boot, MySQL, Redis는 Docker 내부 네트워크에서만 접근하도록 구성합니다.

## 6. URL 설계

### 화면 URL

| Method | Path | Description |
|---|---|---|
| GET | `/` | URL 생성 화면 |
| POST | `/links` | URL 생성 form 처리 |
| GET | `/links/{shortCode}` | 단축 URL 상세 화면 |
| GET | `/s/{shortCode}` | 원본 URL로 리다이렉트 |

### REST API

| Method | Path | Description |
|---|---|---|
| POST | `/api/links` | 단축 URL 생성 |
| GET | `/api/links/{shortCode}` | 단축 URL 상세 조회 |
| GET | `/api/links/{shortCode}/stats` | 클릭 수 조회 |
| GET | `/actuator/health` | 애플리케이션 상태 확인 |

## 7. DB 설계

### links

```sql
CREATE TABLE links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_url VARCHAR(2048) NOT NULL,
    short_code VARCHAR(20) NOT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_links_short_code (short_code)
);
```

`short_code`는 단축 URL 리다이렉트 요청의 핵심 조회 조건이므로 UNIQUE INDEX를 적용합니다.

자세한 SQL 설계는 [docs/database.md](./docs/database.md)를 참고하세요.

## 8. Redis 캐시 전략

```text
shortlink:{shortCode} -> originalUrl
```

리다이렉트 요청 시 Redis를 먼저 조회하고, 캐시 미스가 발생하면 MySQL에서 조회한 뒤 Redis에 저장합니다.

자세한 캐시 전략은 [docs/redis-cache.md](./docs/redis-cache.md)를 참고하세요.

## 9. 로컬 실행 방법

### 1. Repository clone

```bash
git clone https://github.com/{github-username}/shortlinkops.git
cd shortlinkops
```

### 2. MySQL, Redis 실행

```bash
docker compose up -d mysql redis
```

### 3. Spring Boot 실행

```bash
./gradlew bootRun
```

### 4. 접속 확인

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

자세한 실행 방법은 [docs/local-run.md](./docs/local-run.md)를 참고하세요.

## 10. AWS EC2 배포

AWS 비용을 최소화하기 위해 RDS와 ElastiCache를 사용하지 않고, 단일 EC2 인스턴스에서 Docker Compose로 실행합니다.

```text
EC2
  ├─ nginx
  ├─ spring boot app
  ├─ mysql
  └─ redis
```

자세한 배포 방법은 [docs/deployment.md](./docs/deployment.md)를 참고하세요.

## 11. GitHub Actions

GitHub Actions를 사용하여 다음 작업을 자동화합니다.

- Gradle test
- Gradle build
- Docker image build
- Docker Hub push

자세한 CI/CD 구성은 [docs/github-actions.md](./docs/github-actions.md)를 참고하세요.

## 12. 개발 규칙

본 프로젝트는 Git Flow 기반 브랜치 전략과 커밋 컨벤션을 사용합니다.

자세한 내용은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 참고하세요.

## 13. 트러블슈팅

프로젝트 진행 중 발생한 문제와 해결 과정은 [docs/troubleshooting.md](./docs/troubleshooting.md)에 기록합니다.

## 14. 인증/인가 기능 제외 이유

본 프로젝트의 1차 목표는 URL 단축 서비스의 핵심 기능, MyBatis 기반 SQL 처리, Redis 캐시, Docker Compose 기반 실행 환경, AWS EC2 배포를 완성하는 것입니다.

인증/인가 기능은 프로젝트 범위를 과도하게 확장할 수 있으므로 1차 버전에서는 제외했습니다. 추후 Spring Security를 도입하여 회원가입, 로그인, 사용자별 링크 관리 기능을 확장할 예정입니다.

## 15. 개선 예정 사항

- HTTPS 적용
- 도메인 연결
- Spring Security 기반 회원가입/로그인
- 사용자별 링크 관리
- 커스텀 shortCode 지정 기능
- 클릭 로그 테이블 추가
- 간단한 Rate Limit 적용
- GitHub Actions 기반 EC2 자동 배포
- Prometheus/Grafana 모니터링 연동
- RDS, ElastiCache 기반 관리형 인프라 전환

## 16. 프로젝트 핵심 포인트

- Spring Boot 기반 백엔드 API 구현 능력
- MyBatis를 활용한 SQL 직접 작성 능력
- Redis 캐시 적용 경험
- Docker Compose 기반 실행 환경 구성
- AWS EC2 배포 경험
- 비용을 고려한 인프라 선택
- 운영 환경을 고려한 포트 노출 및 설정 분리
