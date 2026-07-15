# MoaDay 로컬 실행 및 운영 설명서

## 1. 목적

이 문서는 GitHub에서 프로젝트를 받은 사람이 Windows, macOS 또는 Linux에서 MoaDay를 실행하고 검증하는 절차를 설명합니다. 가장 재현성이 높은 Docker 실행을 기본으로 합니다.

## 2. 요구 환경

### Docker 실행

- Git 2.40 이상 권장
- Docker Desktop 또는 Docker Engine + Compose v2
- Docker 메모리 4GB 이상 권장
- 사용 가능한 로컬 포트: `3000`, `5432`, `8025`, `8080`

### Docker 없이 개발할 경우

- Java 21
- Maven 3.9 이상
- Node.js 22.13 이상
- npm 10 이상
- 선택: PostgreSQL 17

## 3. 저장소 받기

```powershell
git clone https://github.com/khhan-dev/moaday.git
cd moaday
```

저장소 이름이나 로컬 폴더 이름은 달라도 실행에 영향을 주지 않습니다.

## 4. 가장 빠른 실행

Docker Desktop을 실행하고 프로젝트 루트에서 다음 명령을 실행합니다.

```powershell
docker compose up --build -d
docker compose ps
```

모든 서비스의 상태가 `healthy` 또는 `running`이면 다음 주소를 엽니다.

| 서비스 | 주소 | 용도 |
| --- | --- | --- |
| 웹 | http://localhost:3000 | MoaDay 화면 |
| API 상태 | http://localhost:8080/actuator/health | 백엔드 준비 상태 |
| Mailpit | http://localhost:8025 | 기본 로컬 메일 수신함 |
| PostgreSQL | localhost:5432 | 로컬 DB 연결 |

웹에서 회원가입하거나 `데모 화면 둘러보기`를 선택할 수 있습니다. 실제 저장·초대 흐름은 회원가입 계정으로 확인합니다.

## 5. 환경 변수

외부 메일이나 기본 DB 암호를 변경하지 않는다면 `.env` 없이 실행할 수 있습니다. 변경이 필요하면 다음과 같이 예제 파일을 복사합니다.

```powershell
Copy-Item .env.example .env
```

`.env`는 `.gitignore`에 포함되어 GitHub에 올라가지 않습니다.

### 주요 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `POSTGRES_DB` | `moaday` | PostgreSQL 데이터베이스 이름 |
| `POSTGRES_USER` | `moaday` | PostgreSQL 사용자 |
| `POSTGRES_PASSWORD` | 로컬 개발값 | PostgreSQL 암호 |
| `JWT_SECRET` | 로컬 개발값 | HS256 JWT 서명 키 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 허용 웹 출처 목록 |
| `NEXT_PUBLIC_API_URL` | `/api/v1` | 웹에서 사용하는 API 기준 경로 |
| `COUPON_CLAIM_MINUTES` | `15` | 쿠폰 선점 유지 시간 |
| `MAIL_HOST` | `mailpit` | SMTP 서버 |
| `MAIL_PORT` | `1025` | SMTP 포트 |
| `MAIL_USERNAME` | 빈 값 | SMTP 사용자 |
| `MAIL_PASSWORD` | 빈 값 | SMTP 비밀번호 또는 앱 비밀번호 |
| `MAIL_SMTP_AUTH` | `false` | SMTP 인증 여부 |
| `MAIL_STARTTLS_ENABLED` | `false` | STARTTLS 사용 여부 |
| `MAIL_STARTTLS_REQUIRED` | `false` | STARTTLS 필수 여부 |
| `MAIL_FROM` | `no-reply@moaday.local` | 발신 주소 |
| `MOADAY_WEB_BASE_URL` | `http://localhost:3000` | 활동 알림 메일의 상세 화면 링크 기준 주소 |
| `LOGIN_MAXIMUM_ATTEMPTS` | `5` | 임시 잠금 전 연속 로그인 실패 횟수 |
| `LOGIN_LOCK_SECONDS` | `900` | 로그인 임시 잠금 시간(초) |
| `PASSWORD_RESET_EXPIRY_MINUTES` | `30` | 비밀번호 복구 링크 유효 시간 |
| `PASSWORD_RESET_COOLDOWN_SECONDS` | `60` | 같은 계정의 복구 메일 재요청 제한 |
| `MAIL_OUTBOX_INTERVAL_MS` | `10000` | Outbox 확인 간격(ms) |
| `MAIL_OUTBOX_INITIAL_DELAY_MS` | `5000` | 서버 시작 후 첫 처리 대기 시간(ms) |
| `MAIL_OUTBOX_BATCH_SIZE` | `20` | 한 번에 처리할 이메일 수 |
| `MAIL_OUTBOX_MAX_ATTEMPTS` | `5` | 최종 실패 전 최대 발송 시도 수 |
| `MAIL_OUTBOX_RETRY_BASE_SECONDS` | `60` | 첫 실패 후 재시도 대기 시간 |
| `MAIL_OUTBOX_RETRY_MAX_SECONDS` | `3600` | 재시도 대기 시간 상한 |
| `MAIL_OUTBOX_PROCESSING_TIMEOUT_SECONDS` | `300` | 중단된 처리 작업을 복구하는 기준 시간 |

공개 저장소에는 실제 비밀번호, JWT 키, SMTP 앱 비밀번호를 절대 커밋하지 않습니다.

## 6. Gmail SMTP 설정

기본 Mailpit 대신 Gmail로 초대 메일을 보내려면 Google 계정의 2단계 인증과 앱 비밀번호가 필요합니다. 일반 Google 계정 비밀번호를 사용하지 않습니다.

`.env` 예시:

```dotenv
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-account@gmail.com
MAIL_PASSWORD=your-16-character-app-password
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLED=true
MAIL_STARTTLS_REQUIRED=true
MAIL_CONNECTION_TIMEOUT_MS=10000
MAIL_READ_TIMEOUT_MS=10000
MAIL_WRITE_TIMEOUT_MS=10000
MAIL_FROM=your-account@gmail.com
MOADAY_WEB_BASE_URL=http://localhost:3000
```

설정을 변경한 후 API 컨테이너를 재생성합니다.

```powershell
docker compose up -d --build --force-recreate api web gateway
```

새 공간 초대, 비밀번호 복구 요청 또는 이메일 알림 활동이 발생하면 먼저 DB Outbox에 저장되고 별도 작업자가 SMTP 발송을 시도합니다. 활동·복구 알림에는 `MOADAY_WEB_BASE_URL` 기준 링크가 포함됩니다. 터널을 사용할 때 외부 기기에서 링크를 열려면 이 값을 현재 HTTPS 터널 주소로 설정하고 API를 재생성합니다. 공간 관리 화면에서 발송 상태를 확인하고 대기 중인 초대를 재발송할 수 있습니다.

## 7. 서비스 관리

```powershell
# 상태
docker compose ps

# 전체 로그
docker compose logs -f

# API 로그만 확인
docker compose logs -f api

# 데이터 유지 종료
docker compose down

# 다시 실행
docker compose up -d
```

`docker compose down -v`는 PostgreSQL과 업로드 파일 볼륨까지 삭제하므로 테스트 데이터를 모두 지울 때만 사용합니다.

## 8. 검증

### API 테스트

```powershell
cd apps/api
mvn test
```

### 웹 빌드·테스트

```powershell
cd apps/web
npm ci
npm test
```

### 전체 흐름 스모크 테스트

Docker 서비스가 실행 중인 프로젝트 루트에서 실행합니다.

스모크 테스트는 여러 초대를 생성하므로 외부 SMTP 대신 기본 Mailpit 설정에서 실행하는 것을 권장합니다. Gmail을 사용 중이라면 테스트 전에 `.env`의 메일 설정을 Mailpit 기본값으로 바꾸고 API 컨테이너를 재생성합니다.

```powershell
node scripts/e2e-smoke.mjs
```

스크립트는 테스트 계정과 공간을 만들고 초대, 권한, 일정, ICS, 공유글, 쿠폰, 감사 로그를 확인한 뒤 생성 데이터를 정리합니다.

## 9. 다른 디바이스에서 임시 접속

```powershell
docker compose --profile tunnel up -d tunnel
docker compose logs tunnel
```

로그의 `https://...trycloudflare.com` 주소를 휴대폰 등 다른 기기에서 엽니다. Quick Tunnel은 임시 개발 기능이며 주소가 실행할 때마다 바뀝니다.

```powershell
docker compose --profile tunnel stop tunnel
docker compose --profile tunnel rm -f tunnel
```

## 10. 백업과 복원

### 백업

```powershell
.\scripts\backup.ps1
```

PostgreSQL 덤프, 업로드 파일 압축과 매니페스트가 `backups/moaday-날짜-시간`에 생성됩니다. 일관성을 위해 백업 중 API와 웹이 잠시 중지됩니다.

### 복원

```powershell
.\scripts\restore.ps1 `
  -BackupDirectory .\backups\moaday-20260714-120000 `
  -ConfirmRestore
```

복원은 현재 로컬 데이터를 변경합니다. 중요한 데이터가 있다면 먼저 별도 백업합니다.

## 11. Docker 없이 실행

### API

기본값은 파일 기반 H2입니다.

```powershell
cd apps/api
mvn spring-boot:run
```

### 웹

새 터미널에서 실행합니다.

```powershell
cd apps/web
npm install
npm run dev
```

개발 서버가 출력하는 주소를 엽니다. 이 방식에서는 루트 `.env`가 자동으로 Spring Boot에 로드되지 않으므로 SMTP·DB 값을 운영체제 환경 변수로 전달해야 합니다.

## 12. 문제 해결

### Docker API에 연결할 수 없음

Docker Desktop이 실행 중인지 확인하고 `docker info`가 성공하는지 확인합니다.

### 3000 또는 8080 포트가 이미 사용 중

기존 프로세스를 종료하거나 `docker-compose.yml`의 호스트 포트 매핑을 변경합니다. 웹과 API 기준 주소도 함께 조정해야 합니다.

### API가 unhealthy 상태

```powershell
docker compose logs api
docker compose logs postgres
```

DB 연결 또는 Flyway 마이그레이션 오류를 먼저 확인합니다.

### 메일이 오지 않음

1. 기본 설정이면 Mailpit `http://localhost:8025`를 확인합니다.
2. Gmail이면 앱 비밀번호, 발신 주소, STARTTLS 설정을 확인합니다.
3. `docker compose up -d --force-recreate api`로 환경 변수를 다시 주입합니다.
4. API 로그에서 SMTP 연결 실패 여부를 확인합니다.

### 완전 초기화

```powershell
docker compose down -v
docker compose up --build -d
```

이 작업은 모든 로컬 사용자, 일정, 게시글, 쿠폰과 첨부파일을 삭제합니다.

## 13. GitHub 공개 전 확인

```powershell
git status
git check-ignore -v .env
git diff --check
```

- `.env`, 백업 파일, 로그, 빌드 결과물이 추적되지 않아야 합니다.
- 문서와 `.env.example`에는 예시 값만 있어야 합니다.
- `mvn test`, `npm test`, `node scripts/e2e-smoke.mjs`가 통과하는지 확인합니다.
