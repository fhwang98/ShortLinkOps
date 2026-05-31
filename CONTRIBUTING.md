# Contributing Guide

이 문서는 ShortLinkOps 프로젝트의 개발 규칙을 정의합니다.

## 1. Branch Strategy

본 프로젝트는 Git Flow 기반 브랜치 전략을 사용합니다.

| Branch | Description |
|---|---|
| `main` | 배포 가능한 안정 버전 |
| `dev` | 개발 통합 브랜치 |
| `feature/*` | 기능 개발 브랜치 |
| `hotfix/*` | 운영 긴급 수정 브랜치 |
| `release/*` | 배포 준비 브랜치 |

## 2. Branch Naming

```text
feature/{issue-number}-{feature-name}
hotfix/{issue-number}-{hotfix-name}
release/v{major}.{minor}.{patch}
```

예시:

```text
feature/1-create-link-api
feature/2-redis-cache
hotfix/10-redirect-error
release/v1.0.0
```

## 3. Development Flow

```text
1. dev 브랜치에서 feature 브랜치를 생성한다.
2. feature 브랜치에서 작업한다.
3. 작업 완료 후 Pull Request를 생성한다.
4. CI 통과 여부를 확인한다.
5. dev 브랜치로 merge한다.
6. 배포 준비 시 release 브랜치를 생성한다.
7. 검증 완료 후 main 브랜치로 merge한다.
8. main 브랜치에서 tag를 생성한다.
```

## 4. Commit Convention

커밋 메시지는 다음 형식을 사용합니다.

```text
type: subject
```

예시:

```text
feat: add short link creation api
fix: handle expired short link
docs: update deployment guide
```

### Commit Type

| Type | Description |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅, 세미콜론 누락 등 기능 변경 없는 수정 |
| `refactor` | 리팩토링 |
| `test` | 테스트 코드 추가 또는 수정 |
| `chore` | 빌드, 설정, 패키지 관리 등 기타 작업 |
| `ci` | CI/CD 설정 변경 |
| `infra` | Docker, Nginx, AWS 등 인프라 관련 변경 |
| `db` | schema.sql, SQL Mapper 등 DB 관련 변경 |

## 5. Pull Request Rule

- 하나의 PR은 하나의 목적만 가진다.
- 기능 구현과 리팩토링을 한 PR에 섞지 않는다.
- PR 제목은 변경 내용을 명확히 작성한다.
- 관련 Issue가 있다면 PR 본문에 연결한다.
- CI가 실패한 PR은 merge하지 않는다.
- 민감 정보가 포함된 파일은 PR에 포함하지 않는다.

### PR Title Example

```text
[Feat] 단축 URL 생성 API 구현
[Feat] Redis 캐시 기반 리다이렉트 처리 구현
[Fix] 만료된 단축 URL 응답 처리 수정
[Docs] README 배포 문서 추가
```

## 6. Issue Rule

Issue는 작업 단위로 생성합니다.

### Issue Title Example

```text
[Feature] 단축 URL 생성 API 구현
[Feature] Redis 캐시 적용
[Infra] Docker Compose 구성
[Docs] README 작성
```

## 7. Code Style

- 패키지는 역할 기준으로 분리한다.
- Controller는 요청/응답 처리에 집중한다.
- Service는 비즈니스 로직을 담당한다.
- Mapper는 SQL 실행만 담당한다.
- DTO와 Domain 객체를 구분한다.
- 예외 응답은 GlobalExceptionHandler에서 일관되게 처리한다.

## 8. Security Rule

다음 파일 또는 값은 Git에 포함하지 않습니다.

- `.env`
- DB password
- Docker Hub token
- AWS access key
- EC2 private key
- 운영 서버 IP가 포함된 민감 설정

환경 변수 또는 GitHub Secrets를 사용합니다.
