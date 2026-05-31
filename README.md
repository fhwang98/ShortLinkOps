# ShortLinkOps

> MyBatis SQL, Redis 캐시, Docker Compose, AWS EC2 배포를 포함한 Spring Boot 기반 URL 단축 서비스

## 1. 프로젝트 개요

**ShortLinkOps**는 긴 URL을 짧은 URL로 변환하고, 단축 URL 접속 시 원본 URL로 리다이렉트하는 Spring Boot 기반 URL 단축 서비스입니다.

단순 CRUD 구현에 그치지 않고, URL 리다이렉트 요청의 특성을 고려하여 Redis 캐시를 적용하고, MyBatis를 사용해 SQL을 직접 작성했습니다. 또한 Docker Compose 기반으로 애플리케이션, MySQL, Redis, Nginx를 분리 실행하며, AWS EC2 환경에 배포 가능한 구조로 구성했습니다.

## 2. 프로젝트 목표

이 프로젝트의 목표는 다음과 같습니다.

* Spring Boot 기반 백엔드 API 구현
* MyBatis를 활용한 SQL 직접 작성
* MySQL 기반 URL 데이터 저장
* Redis 캐시를 통한 리다이렉트 조회 최적화
* Thymeleaf 기반 최소 화면 제공
* Docker Compose 기반 실행 환경 구성
* AWS EC2 단일 인스턴스 배포
* GitHub Actions를 활용한 빌드 자동화
* 운영 환경을 고려한 포트 노출 및 설정 분리

## 3. 기술 스택

### Backend

* Java 17
* Spring Boot 3.5.14
* Spring Web
* MyBatis
* Spring Validation
* Spring Boot Actuator
* Thymeleaf

### Database

* MySQL 8.x

### Cache

* Redis 7.x

### Infra / Deploy

* AWS EC2
* Docker
* Docker Compose
* Nginx

### CI/CD

* GitHub Actions
* Docker Hub

## 4. 주요 기능

### URL 단축

* 긴 URL을 입력받아 고유한 shortCode를 생성합니다.
* 생성된 shortCode를 기반으로 단축 URL을 제공합니다.

### URL 리다이렉트

* `/s/{shortCode}` 요청 시 원본 URL로 리다이렉트합니다.
* Redis 캐시를 먼저 조회하고, 캐시 미스 시 MySQL에서 조회합니다.

### 클릭 수 집계

* 단축 URL 접속 시 클릭 수를 증가시킵니다.
* 클릭 수 증가는 단일 UPDATE 쿼리로 처리합니다.

### URL 상세 조회

* 원본 URL, 단축 URL, 클릭 수, 생성일, 만료일을 조회할 수 있습니다.

### 만료 처리

* 만료 시간이 지난 단축 URL은 리다이렉트하지 않습니다.
* 만료된 URL 요청 시 `410 Gone` 응답을 반환합니다.

### 기본 화면 제공

* Thymeleaf를 사용하여 URL 생성 화면, 결과 화면, 상세 화면을 제공합니다.

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

## 6. 화면 URL

| Method | Path                 | Description    |
| ------ | -------------------- | -------------- |
| GET    | `/`                  | URL 생성 화면      |
| POST   | `/links`             | URL 생성 form 처리 |
| GET    | `/links/{shortCode}` | 단축 URL 상세 화면   |
| GET    | `/s/{shortCode}`     | 원본 URL로 리다이렉트  |

## 7. REST API

| Method | Path                           | Description  |
| ------ | ------------------------------ | ------------ |
| POST   | `/api/links`                   | 단축 URL 생성    |
| GET    | `/api/links/{shortCode}`       | 단축 URL 상세 조회 |
| GET    | `/api/links/{shortCode}/stats` | 클릭 수 조회      |
| GET    | `/actuator/health`             | 애플리케이션 상태 확인 |

## 8. DB 설계

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

### 인덱스 설계

`short_code`는 단축 URL 리다이렉트 요청의 핵심 조회 조건입니다.

```sql
SELECT
    id,
    original_url,
    short_code,
    click_count,
    expires_at,
    created_at,
    updated_at
FROM links
WHERE short_code = #{shortCode};
```

따라서 `short_code` 컬럼에는 UNIQUE INDEX를 적용했습니다.

## 9. Redis 캐시 전략

### Redis Key

```text
shortlink:{shortCode} -> originalUrl
```

예시:

```text
shortlink:aB12xY -> https://example.com/some/long/path
```

### 리다이렉트 처리 흐름

```text
GET /s/{shortCode}
  |
  v
Redis 조회
  |
  +-- Cache Hit
  |      |
  |      +--> MySQL click_count 증가
  |      +--> Redirect
  |
  +-- Cache Miss
         |
         +--> MySQL short_code 조회
         +--> 만료 여부 확인
         +--> Redis 저장
         +--> MySQL click_count 증가
         +--> Redirect
```

클릭 수 증가는 Java에서 값을 조회한 뒤 증가시키지 않고, 다음과 같은 단일 UPDATE 쿼리로 처리합니다.

```sql
UPDATE links
SET click_count = click_count + 1
WHERE short_code = #{shortCode};
```

## 10. 프로젝트 구조

```text
src/main/java/com/fhwang/shortlinkops
  ├─ ShortlinkopsApplication.java
  ├─ controller
  │   ├─ LinkApiController.java
  │   └─ LinkViewController.java
  ├─ service
  │   └─ LinkService.java
  ├─ mapper
  │   └─ LinkMapper.java
  ├─ domain
  │   └─ Link.java
  ├─ dto
  │   ├─ CreateLinkRequest.java
  │   ├─ CreateLinkResponse.java
  │   ├─ LinkResponse.java
  │   └─ LinkStatsResponse.java
  ├─ exception
  │   ├─ GlobalExceptionHandler.java
  │   ├─ LinkNotFoundException.java
  │   └─ ExpiredLinkException.java
  └─ util
      └─ ShortCodeGenerator.java
```

```text
src/main/resources
  ├─ mapper
  │   └─ LinkMapper.xml
  ├─ templates
  │   ├─ index.html
  │   ├─ result.html
  │   ├─ detail.html
  │   └─ error
  │       ├─ 404.html
  │       └─ 410.html
  ├─ static
  │   └─ css
  │       └─ style.css
  ├─ schema.sql
  └─ application.yml
```

## 11. 로컬 실행 방법

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

## 12. Docker Compose 실행

전체 컨테이너를 Docker Compose로 실행합니다.

```bash
docker compose up -d
```

실행 상태 확인:

```bash
docker compose ps
```

로그 확인:

```bash
docker compose logs -f app
```

중지:

```bash
docker compose down
```

## 13. AWS EC2 배포 구조

AWS 비용을 최소화하기 위해 RDS와 ElastiCache를 사용하지 않고, 단일 EC2 인스턴스에서 Docker Compose로 실행합니다.

```text
EC2
  ├─ nginx
  ├─ spring boot app
  ├─ mysql
  └─ redis
```

### Security Group

| Port | Source    | Description     |
| ---- | --------- | --------------- |
| 22   | My IP     | SSH             |
| 80   | 0.0.0.0/0 | HTTP            |
| 443  | 0.0.0.0/0 | HTTPS, optional |

다음 포트는 외부에 노출하지 않습니다.

| Port | Description |
| ---- | ----------- |
| 8080 | Spring Boot |
| 3306 | MySQL       |
| 6379 | Redis       |

## 14. GitHub Actions

GitHub Actions를 사용하여 다음 작업을 자동화합니다.

* Gradle test
* Gradle build
* Docker image build
* Docker Hub push

초기 단계에서는 `test`와 `build`를 우선 구성하고, 이후 Docker image publish 단계를 추가합니다.

## 15. Git Flow 전략

본 프로젝트는 Git Flow 기반 브랜치 전략을 사용합니다.

### Branch 종류

| Branch      | Description  |
| ----------- | ------------ |
| `main`      | 배포 가능한 안정 버전 |
| `dev`       | 개발 통합 브랜치    |
| `feature/*` | 기능 개발 브랜치    |
| `fix/*`     | 버그 수정 브랜치    |
| `hotfix/*`  | 운영 긴급 수정 브랜치 |
| `release/*` | 배포 준비 브랜치    |

### Branch 규칙

```text
main
dev
feature/{issue-number}-{feature-name}
fix/{issue-number}-{fix-name}
hotfix/{issue-number}-{hotfix-name}
release/v{major}.{minor}.{patch}
```

예시:

```text
feature/1-create-link-api
feature/2-redis-cache
fix/5-expired-link-response
hotfix/10-redirect-error
release/v1.0.0
```

### 기본 개발 흐름

```text
1. dev 브랜치에서 feature 브랜치 생성
2. feature 브랜치에서 기능 개발
3. Pull Request 생성
4. 코드 리뷰 및 CI 통과 확인
5. dev 브랜치로 merge
6. 배포 준비 시 release 브랜치 생성
7. 검증 완료 후 main merge
8. main에서 tag 생성
```

## 16. Commit Convention

커밋 메시지는 다음 형식을 따릅니다.

```text
type: subject
```

예시:

```text
feat: add link creation api
fix: handle expired short link
docs: update deployment guide
```

### Commit Type

| Type       | Description                       |
| ---------- | --------------------------------- |
| `feat`     | 새로운 기능 추가                         |
| `fix`      | 버그 수정                             |
| `docs`     | 문서 수정                             |
| `style`    | 코드 포맷팅, 세미콜론 누락 등 기능 변경 없는 수정     |
| `refactor` | 리팩토링                              |
| `test`     | 테스트 코드 추가 또는 수정                   |
| `chore`    | 빌드, 설정, 패키지 관리 등 기타 작업            |
| `ci`       | CI/CD 설정 변경                       |
| `infra`    | Docker, Nginx, AWS 등 인프라 관련 변경    |
| `db`       | schema.sql, SQL Mapper 등 DB 관련 변경 |

### Commit Message 예시

```text
feat: add short link creation api
feat: add thymeleaf link creation page
feat: apply redis cache to redirect flow
fix: return 410 when short link is expired
db: add links table schema
infra: add docker compose configuration
ci: add gradle build workflow
docs: add redis cache strategy
```

## 17. Pull Request 규칙

Pull Request는 다음 기준을 만족해야 합니다.

* 하나의 PR은 하나의 목적만 가진다.
* 기능 구현과 리팩토링을 한 PR에 섞지 않는다.
* PR 제목은 작업 내용을 명확히 작성한다.
* 관련 Issue가 있다면 PR 본문에 연결한다.
* CI가 실패한 PR은 merge하지 않는다.

### PR 제목 예시

```text
[Feat] 단축 URL 생성 API 구현
[Feat] Redis 캐시 기반 리다이렉트 처리 구현
[Fix] 만료된 단축 URL 응답 처리 수정
[Docs] README 배포 문서 추가
```

### PR Template

```markdown
## 작업 내용

- 

## 변경 사항

- 

## 테스트 방법

- 

## 체크리스트

- [ ] 로컬에서 정상 동작 확인
- [ ] 테스트 통과
- [ ] 불필요한 로그 제거
- [ ] 문서 업데이트
```

## 18. Issue 규칙

Issue는 작업 단위로 생성합니다.

### Issue 제목 예시

```text
[Feature] 단축 URL 생성 API 구현
[Feature] Redis 캐시 적용
[Infra] Docker Compose 구성
[Docs] README 작성
```

### Issue Template

```markdown
## 작업 목적

## 작업 내용

- [ ] 

## 완료 조건

- [ ] 

## 참고 사항
```

## 19. 환경 변수

운영 환경에서는 민감 정보를 `.env` 파일로 분리합니다.

```env
MYSQL_USER=shortlink
MYSQL_PASSWORD=change_me_password
MYSQL_ROOT_PASSWORD=change_me_root_password
```

`.env` 파일은 Git에 포함하지 않습니다.

## 20. 트러블슈팅

프로젝트 진행 중 발생한 문제와 해결 과정을 기록합니다.

예시:

```text
문제:
Docker Compose 실행 시 Spring Boot가 MySQL에 연결하지 못함

원인:
app 컨테이너가 mysql 컨테이너 준비 전에 먼저 실행됨

해결:
depends_on 설정 추가 후, 애플리케이션 레벨에서 DB 연결 재시도 가능하도록 조정
```

## 21. 개선 예정 사항

* HTTPS 적용
* 도메인 연결
* 커스텀 shortCode 지정 기능
* 클릭 로그 테이블 추가
* 간단한 Rate Limit 적용
* GitHub Actions 기반 EC2 자동 배포
* Prometheus/Grafana 모니터링 연동
* RDS, ElastiCache 기반 관리형 인프라 전환

## 22. 프로젝트 핵심 포인트

이 프로젝트에서 중점적으로 보여주고자 한 부분은 다음과 같습니다.

* Spring Boot 기반 백엔드 API 구현 능력
* MyBatis를 활용한 SQL 직접 작성 능력
* Redis 캐시 적용 경험
* Docker Compose 기반 실행 환경 구성
* AWS EC2 배포 경험
* 비용을 고려한 인프라 선택
* 운영 환경을 고려한 포트 노출 및 설정 분리
