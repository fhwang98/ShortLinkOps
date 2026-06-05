# ShortLinkOps

> MyBatis SQL, Redis 캐시, Docker Compose, AWS EC2 배포를 포함한 Spring Boot 기반 URL 단축 서비스

## 1. 프로젝트 개요

**ShortLinkOps**는 긴 URL을 짧은 URL로 변환하고, 단축 URL 접속 시 원본 URL로 리다이렉트하는 Spring Boot 기반 URL 단축 서비스입니다.

단순 CRUD 구현에 그치지 않고, URL 리다이렉트 요청의 특성을 고려하여 Redis 캐시를 적용하고, MyBatis를 사용해 SQL을 직접 작성합니다. 또한 Docker Compose 기반으로 애플리케이션, MySQL, Redis를 분리 실행하며, AWS EC2 환경에 배포 가능한 구조로 구성합니다.

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
Route53 Domain
  |
  v
Elastic IP
  |
  v
EC2 Host Nginx :443
  |
  v
EC2 localhost :8081
  |
  v
Spring Boot Container :8080
  |
  +--> MySQL Container :3306
  |
  +--> Redis Container :6379
```

외부에는 EC2 호스트 Nginx의 80, 443 포트만 노출합니다. Spring Boot 컨테이너는 EC2 내부의 `127.0.0.1:8081`에서만 접근하고, MySQL과 Redis는 Docker 내부 네트워크에서만 접근하도록 구성합니다.

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

테이블 생성 SQL은 [schema.sql](./src/main/resources/schema.sql)에 정의되어 있습니다.

## 8. Redis 캐시 전략

```text
shortlink:{shortCode} -> originalUrl
```

리다이렉트 요청 시 Redis를 먼저 조회하고, 캐시 미스가 발생하면 MySQL에서 조회한 뒤 Redis에 저장합니다.

```text
Cache Hit  -> Redis originalUrl 반환 -> click_count 증가 -> redirect
Cache Miss -> MySQL 조회 -> 만료 확인 -> Redis 저장 -> click_count 증가 -> redirect
```

클릭 수 증가는 정합성을 위해 Redis가 아니라 MySQL의 단일 `UPDATE` 쿼리로 처리합니다.

Redis key TTL은 링크 만료 시간이 있으면 만료 시간까지로 설정하고, 만료 시간이 없으면 기본 24시간으로 설정합니다. Redis 조회/저장 실패 시에는 로그만 남기고 MySQL 조회 흐름으로 fallback합니다.

## 9. 로컬 실행 방법

### Docker Compose 전체 실행

로컬 Docker Compose 실행은 Spring Boot, MySQL, Redis를 함께 실행합니다.

```bash
git clone https://github.com/{github-username}/shortlinkops.git
cd shortlinkops
```

JAR 빌드:

```bash
./gradlew clean bootJar
```

전체 컨테이너 실행:

```bash
docker compose up -d --build
```

상태 확인:

```bash
docker compose ps
docker compose logs -f app
```

접속:

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

종료:

```bash
docker compose down
```

데이터까지 삭제하려면 다음 명령을 사용합니다.

```bash
docker compose down -v
```

### Spring Boot 직접 실행

로컬에 MySQL과 Redis를 직접 띄워서 Spring Boot만 실행할 수도 있습니다.

```bash
cp .env.dev.example .env
set -a
source .env
set +a

./gradlew bootRun
```

접속:

```text
http://localhost:8080
```

## 10. AWS EC2 배포

AWS 비용을 최소화하기 위해 RDS와 ElastiCache를 사용하지 않고, 단일 EC2 인스턴스에서 Docker Compose로 실행합니다. 도메인은 Route53 Hosted Zone으로 관리하고, Elastic IP를 A 레코드에 연결합니다.

```text
Route53
  ├─ fhwang.cloud      -> Elastic IP
  └─ www.fhwang.cloud  -> Elastic IP

EC2 Host
  ├─ nginx :80/:443
  └─ Docker Compose
      ├─ spring boot app :8081 -> :8080
      ├─ mysql
      └─ redis
```

Security Group은 다음 포트만 엽니다.

```text
22  - 0.0.0.0/0
80  - 0.0.0.0/0
443 - 0.0.0.0/0
```

외부에 열지 않는 포트:

```text
8080
8081
3306
6379
```

### Terraform으로 인프라 생성

EC2, Security Group, Elastic IP, Route53 Hosted Zone, A 레코드는 Terraform으로 생성할 수 있습니다.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
aws sts get-caller-identity
terraform init
terraform plan
terraform apply
```

자세한 사용 방법은 [infra/terraform/README.md](./infra/terraform/README.md)를 참고하세요.

Terraform 구성은 dev 배포에서 default VPC를 사용하고, Ubuntu 24.04 LTS x86_64 EC2를 생성합니다. 보안그룹은 SSH 22, HTTP 80, HTTPS 443만 허용하며, 키페어는 Terraform이 생성합니다. AWS 인증은 Access Key를 tfvars에 넣지 않고 AWS CLI 로그인 또는 AWS profile을 사용합니다.

Terraform 적용 후 `hosted_zone_name_servers` 출력값을 도메인 등록기관의 name server로 설정합니다. NS 변경이 반영되면 EC2의 Certbot 타이머가 `fhwang.cloud`, `www.fhwang.cloud` 인증서를 발급하고 HTTP 요청을 HTTPS로 리다이렉트하도록 Nginx 설정을 적용합니다.

Let's Encrypt 인증서는 기본 90일 동안 유효합니다. EC2에서는 Certbot 기본 갱신 타이머가 주기적으로 갱신을 확인하고, 갱신 완료 후 Nginx를 reload합니다.

### EC2 Docker 설치

Terraform으로 생성한 EC2는 `user_data`가 Docker, Docker Compose, Git, Nginx, Certbot을 자동 설치합니다. 수동으로 같은 상태를 만들거나 설치 상태를 확인할 때는 아래 명령을 사용합니다.

Ubuntu 기준:

```bash
sudo apt-get update -y
sudo apt-get install -y docker.io docker-compose-v2 git

sudo systemctl enable docker
sudo systemctl start docker

sudo usermod -aG docker ubuntu
```

SSH 재접속 후 확인:

```bash
docker version
docker compose version
```

### Docker Hub image로 실행

Docker Hub에 image가 push되어 있으면 운영 Compose 파일을 사용합니다.

```bash
git clone https://github.com/{github-username}/ShortLinkOps.git
cd ShortLinkOps
cp .env.prod.example .env
```

`.env`에서 아래 값을 운영 환경에 맞게 수정합니다.

```env
APP_IMAGE=placeholder
MYSQL_USER=shortlink
MYSQL_PASSWORD=change_me
MYSQL_ROOT_PASSWORD=change_me_root
SHORTLINKOPS_BASE_URL=https://www.fhwang.cloud
```

`APP_IMAGE`는 GitHub Actions 자동 배포 시 현재 commit SHA 이미지로 덮어씁니다. EC2에서 수동으로 운영 Compose를 실행할 때만 실제 Docker Hub 이미지 태그로 변경합니다.

실행:

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

운영 Compose의 Spring Boot 컨테이너는 EC2 내부 `127.0.0.1:8081`에만 바인딩됩니다. 외부 80, 443 포트와 HTTPS 인증서는 EC2 호스트 Nginx가 처리합니다.

### EC2에서 직접 build

Docker Hub push 전이면 EC2에서 직접 build할 수 있습니다.

```bash
git clone https://github.com/{github-username}/ShortLinkOps.git
cd ShortLinkOps
./gradlew clean bootJar
docker compose up -d --build
```

### 배포 확인

```bash
docker compose ps
curl https://www.fhwang.cloud/actuator/health
```

브라우저에서 다음 주소로 접속해 URL 생성, 상세 조회, 리다이렉트, 클릭 수 증가를 확인합니다.

```text
https://www.fhwang.cloud
```

## 11. GitHub Actions

GitHub Actions를 사용하여 다음 작업을 자동화합니다.

- Gradle test
- Gradle build
- Docker image build
- Docker Hub push
- 운영 배포 자동화

브랜치별 자동화 기준은 다음과 같습니다.

```text
dev        -> CI
release/*  -> CI
main       -> CI, Deploy Prod
```

`main` 브랜치에 push되면 `Deploy Prod` workflow가 테스트와 빌드를 실행하고 Docker Hub에 현재 commit SHA 태그 이미지를 push한 뒤 EC2에서 운영 Compose 컨테이너를 갱신합니다. `Deploy Prod`는 수동 실행도 가능하지만, `main` 브랜치에서 실행할 때만 실제 배포 job이 동작합니다.

Docker Hub push와 EC2 배포를 사용하려면 repository secrets에 다음 값을 등록합니다.

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
EC2_HOST
EC2_SSH_KEY
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
```

`EC2_HOST`는 EC2 Elastic IP 또는 접속 가능한 도메인입니다. `EC2_SSH_KEY`는 EC2 접속에 사용하는 private key 전체 내용입니다. `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`는 운영 MySQL 컨테이너에서 사용할 비밀번호입니다.

운영 자동 배포 시 GitHub Secrets와 Variables는 EC2에 `.env` 파일로 저장되지 않고, `docker compose` 실행 시점의 환경변수로 직접 주입됩니다. EC2에 직접 접속해서 `.env`를 수정할 필요가 없습니다.

MySQL 비밀번호는 최초 DB volume 초기화 시점에 적용됩니다. 이미 운영 DB volume이 생성된 뒤 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 변경하려면 DB 계정 비밀번호도 함께 변경하거나 volume 초기화를 별도로 진행합니다.

EC2는 배포 시 Docker Hub에서 현재 commit SHA 태그 이미지를 pull합니다. 별도 서버 로그인을 구성하지 않았으므로 MVP 단계에서는 Docker Hub repository를 public으로 운영합니다.

필요하면 repository variables로 아래 값을 변경할 수 있습니다.

```text
EC2_USER=ubuntu
EC2_APP_DIR=/home/ubuntu/ShortLinkOps
MYSQL_USER=shortlink
SHORTLINKOPS_BASE_URL=https://www.fhwang.cloud
```

`Docker Publish` workflow는 수동 실행 전용입니다. 운영 배포는 `Deploy Prod` workflow가 이미지 push와 EC2 배포를 함께 처리합니다.

## 12. 개발 규칙

본 프로젝트는 Git Flow 기반 브랜치 전략과 커밋 컨벤션을 사용합니다.

자세한 내용은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 참고하세요.

## 13. Release 준비

v1.0.0 릴리스 전 아래 항목을 확인합니다.

- `dev` 브랜치 CI 통과
- EC2 운영 배포 성공
- `https://www.fhwang.cloud/actuator/health` 응답 확인
- 단축 URL 생성, 조회, 리다이렉트 동작 확인
- Route53 A 레코드와 HTTPS 리다이렉트 확인
- `main` 병합 전 민감 정보 미포함 확인

## 14. 트러블슈팅

프로젝트 진행 중 발생한 문제와 해결 과정은 README 또는 별도 문서로 정리합니다.

## 15. 인증/인가 기능 제외 이유

본 프로젝트의 1차 목표는 URL 단축 서비스의 핵심 기능, MyBatis 기반 SQL 처리, Redis 캐시, Docker Compose 기반 실행 환경, AWS EC2 배포를 완성하는 것입니다.

인증/인가 기능은 프로젝트 범위를 과도하게 확장할 수 있으므로 1차 버전에서는 제외했습니다. 추후 Spring Security를 도입하여 회원가입, 로그인, 사용자별 링크 관리 기능을 확장할 예정입니다.

## 16. 개선 예정 사항

- HTTPS 인증서 자동 갱신 상태 모니터링
- Spring Security 기반 회원가입/로그인
- 사용자별 링크 관리
- 커스텀 shortCode 지정 기능
- 클릭 로그 테이블 추가
- 간단한 Rate Limit 적용
- Redis 장애 시 graceful fallback 강화
- Prometheus/Grafana 모니터링 연동
- RDS, ElastiCache 기반 관리형 인프라 전환

## 17. 프로젝트 핵심 포인트

- Spring Boot 기반 백엔드 API 구현 능력
- MyBatis를 활용한 SQL 직접 작성 능력
- Redis 캐시 적용 경험
- Docker Compose 기반 실행 환경 구성
- AWS EC2 배포 경험
- 비용을 고려한 인프라 선택
- 운영 환경을 고려한 포트 노출 및 설정 분리
