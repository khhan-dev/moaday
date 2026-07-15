# MoaDay

가족과 친구의 일정, 공유 자료, 모바일 쿠폰을 한 공간에서 관리하는 SaaS 프로젝트입니다.

![MoaDay - 가족과 친구의 일정·자료·쿠폰을 한곳에](apps/web/public/og.png)

## 면접관·리뷰어를 위한 안내

프로젝트의 문제 정의, 기술 선택과 시연 흐름은 [프로젝트 소개 및 면접 가이드](docs/INTERVIEW_GUIDE.md)에 정리했습니다.

| 문서 | 내용 |
| --- | --- |
| [프로젝트 소개 및 면접 가이드](docs/INTERVIEW_GUIDE.md) | 한 줄 소개, 구현 범위, 아키텍처, 기술적 판단, 7분 시연 |
| [로컬 실행 및 운영 설명서](docs/SETUP_GUIDE.md) | 요구 환경, Docker 실행, 환경 변수, Gmail, 검증, 문제 해결 |
| [요구사항 명세서](docs/REQUIREMENTS_SPEC.md) | 기능·비기능 요구사항, 권한, 수용 기준, 구현 상태 |
| [현재 구현 상세 설계서](docs/DETAILED_DESIGN.md) | 모듈, 데이터 모델, 보안, 주요 흐름, API, 테스트, 기술 부채 |
| [GitHub 공개 체크리스트](docs/GITHUB_PUBLISH_CHECKLIST.md) | 비밀정보, 테스트, 저장소와 면접 공유 최종 확인 |
| [기존 제품 PRD](docs/MVP_PRD.md) | 사용자 관점 제품 요구사항 |
| [제품·시스템 설계 배경](docs/PRODUCT_DESIGN.md) | 제품 방향, 리서치와 초기 설계 판단 |

가장 빠른 실행 방법:

```powershell
docker compose up --build -d
```

실행 후 `http://localhost:3000`을 열고 회원가입하거나 데모 화면을 확인합니다. 자세한 절차는 [실행 설명서](docs/SETUP_GUIDE.md)를 참고합니다.

현재 구현 범위는 12단계입니다.

- 반응형 웹/PWA 기반
- 이메일 회원가입/로그인 API
- 이메일 일회용 링크 기반 비밀번호 복구와 로그인 화면 비밀번호 찾기
- 로그인 반복 실패 잠금과 비밀번호 변경 후 기존 JWT 무효화
- 가입 시 개인 공간 자동 생성
- 가족/친구 공간 생성
- 역할 기반 구성원 초대와 초대 수락
- 공간별 구성원 목록과 역할 변경
- 구성원 추방과 즉시 접근 차단
- 초대 목록, 취소, 만료 상태와 만료 후 재초대
- 공간별 다중 캘린더와 일정 CRUD
- 월·주·일 반응형 캘린더 보기
- 매일·매주·매월·매년 반복 일정과 기간별 발생 일정 계산
- 참석자 지정과 참석·불참·미정 응답
- 일정별 인앱 리마인더 설정 저장
- 공유 게시글 CRUD, 고정, 태그와 본문 검색
- 20MB 이하 첨부파일 업로드·다운로드와 댓글 CRUD
- 쿠폰 등록, 만료 처리와 동시 선점 방지
- 쿠폰 원본 이미지 첨부·교체·삭제와 인증된 구성원 미리보기
- 이미지 쿠폰의 선택적 바코드 등록
- 선점 사용자 전용 바코드 열람, 사용 완료와 선점 해제
- 앱 내 알림, 읽음 처리와 활동별 알림 설정
- DB Outbox 기반 비동기 이메일, 자동 재시도와 선택적 Gmail SMTP
- 공간 관리자용 이메일 발송 이력과 초대 메일 재발송
- 프로필 변경과 비밀번호 확인 계정 삭제
- 설정 화면의 현재 비밀번호 확인 기반 비밀번호 변경
- 쿠폰 만료와 일정 리마인더 1분 주기 자동 처리
- 반복 일정 리마인더 중복 방지 발송 이력
- 위험 첨부파일 내용·확장자 차단과 게시글당 파일 수 제한
- 역할별 권한·입력 보안 통합 테스트
- DB·첨부파일 백업 및 복원 도구
- 테스트 데이터를 자동 정리하는 전체 흐름 점검
- 모든 참여 공간의 일정·공유글·쿠폰 통합검색
- 실제 데이터 기반 오늘 대시보드와 읽지 않은 알림 배지
- 공유글·쿠폰·알림 목록 페이지 이동
- 인증된 이미지 첨부 미리보기와 확대 보기
- 새로고침 후 로그인 세션 복원과 명시적 로그아웃
- 초대 링크에서 로그인·회원가입 후 공간 참여
- 일반 구성원의 그룹 탈퇴와 소유자의 그룹 삭제
- 쿠폰 15분 선점과 만료 시 자동 해제
- 쿠폰 상태·바코드 열람 이력과 관리자 사유 기반 정정
- 역할 변경·추방·탈퇴·파일 열람·쿠폰 활동 감사 기록
- 일정에 같은 공간의 공유글·첨부파일·쿠폰 연결
- 일정 상세에서 연결 자료 확인과 첨부파일 다운로드
- 일정 자료 연결 권한·공간 격리와 바코드 비노출 검증
- 반복 일정의 특정 회차만 수정·취소·변경 복원
- 공간 일정 ICS 내보내기와 캘린더별 ICS 가져오기
- ICS UID 중복 방지와 최대 200개 일정 가져오기
- 일정별 IANA 시간대 저장·입력·표시 강화
- 로컬 데모 화면

## 프로젝트 구조

```text
apps/web  React 기반 Vinext 웹/PWA
apps/api  Spring Boot API
docs      제품·PRD·기술 설계
```

## Docker로 로컬 실행

Docker Desktop을 실행한 뒤 프로젝트 루트에서 다음 명령을 실행합니다.

```powershell
docker compose up --build -d
```

- 웹: `http://localhost:3000`
- API 상태: `http://localhost:8080/actuator/health`
- PostgreSQL: `localhost:5432`
- 로컬 이메일 수신함: `http://localhost:8025`

웹에서 회원가입하면 계정과 개인 공간이 PostgreSQL에 저장됩니다. 컨테이너를 내려도 데이터는 `moaday-postgres` 볼륨에 유지됩니다.

```powershell
# 실행 상태와 로그
docker compose ps
docker compose logs -f

# 종료(데이터 유지)
docker compose down

# 종료하면서 로컬 데이터도 삭제
docker compose down -v
```

기본 로컬 비밀번호와 URL은 별도 설정 없이 동작합니다. 값을 바꾸려면 루트의 `.env.example`을 `.env`로 복사한 뒤 수정합니다. `.env`는 저장소에 포함되지 않습니다.

### 다른 디바이스에서 임시 접속

Cloudflare Quick Tunnel을 Docker 프로필로 실행하면 별도 설치나 도메인 없이 임시 HTTPS 주소가 생성됩니다.

```powershell
docker compose --profile tunnel up -d tunnel
docker compose logs tunnel
```

로그에 표시되는 `https://...trycloudflare.com` 주소를 다른 디바이스에서 엽니다. 웹과 API가 같은 주소를 사용하므로 회원가입, 초대, 일정, 첨부파일과 쿠폰 이미지 기능도 함께 동작합니다. 초대 링크도 이 공개 주소를 기준으로 생성됩니다.

테스트가 끝나면 터널만 종료합니다.

```powershell
docker compose --profile tunnel stop tunnel
docker compose --profile tunnel rm -f tunnel
```

Quick Tunnel 주소는 실행할 때마다 바뀌며 개발·테스트 용도입니다. 터널이 실행되는 동안 주소를 아는 사람은 로그인·회원가입 화면에 접근할 수 있으므로 필요한 동안만 켜 두세요.

### 초대 이메일

공간에서 초대를 만들면 업무 데이터와 이메일 Outbox가 같은 DB 트랜잭션으로 저장됩니다. 별도 작업자가 기본 10초 간격으로 SMTP 발송을 처리하며 실패 시 최대 5회까지 지수형 간격으로 재시도합니다. 공간 관리 화면에서 대기·재시도·성공·최종 실패 상태를 확인하고 대기 중인 초대를 새 일회용 링크로 재발송할 수 있습니다. 기본 Docker 환경에서는 Mailpit(`http://localhost:8025`)에서 안전하게 확인하며, 실제 이메일 주소로 발송하려면 `.env`에 SMTP 설정을 입력합니다.

MoaDay 기존 회원은 로그인 후 화면 상단의 `받은 공간 초대`에서 링크 없이도 초대를 수락하거나 거절할 수 있습니다. 비회원은 이메일 링크를 열어 초대받은 이메일로 회원가입한 뒤 수락할 수 있습니다.

## Docker 없이 로컬 실행

### API

개발 기본값은 파일 기반 H2를 사용합니다.

```powershell
cd apps/api
mvn spring-boot:run
```

API 주소: `http://localhost:8080/api/v1`

H2 대신 PostgreSQL만 Docker로 실행하려면 루트에서 `docker compose up -d postgres`를 실행한 뒤 `apps/api/.env.example`의 값을 환경 변수로 설정합니다.

### 웹

```powershell
cd apps/web
npm install
npm run dev
```

개발 서버가 출력하는 로컬 주소로 접속합니다. API를 먼저 실행하면 회원가입, 공간/초대, 캘린더 기능을 실제로 사용할 수 있고, API 없이도 `데모 화면 둘러보기`로 UI를 확인할 수 있습니다.

## 검증

```powershell
cd apps/api
mvn test

cd ../web
npm run build

cd ../..
node scripts/e2e-smoke.mjs
```

## 백업과 복원

Docker가 실행 중일 때 DB와 첨부파일을 함께 백업할 수 있습니다. 백업 중에는 데이터 일관성을 위해 API와 웹이 잠시 중지된 뒤 자동으로 다시 시작됩니다.

```powershell
.\scripts\backup.ps1
```

기본 백업 위치는 `backups/moaday-날짜-시간`입니다. 복원은 현재 로컬 데이터를 변경하므로 확인 옵션이 필요합니다.

```powershell
.\scripts\restore.ps1 -BackupDirectory .\backups\moaday-20260714-120000 -ConfirmRestore
```

## 현재 로컬 기능 범위

1~12단계의 인증, 공간, 고급 캘린더, 공유함, 쿠폰함, 알림·설정, 통합검색·대시보드, 세션·초대·공간 생명주기, 감사 기록, 일정-자료 연결, 상세 화면 딥링크와 로컬 운영 안정화 기능을 사용할 수 있습니다. 오늘 화면·통합검색·앱 내 알림·이메일 알림·일정 연결 자료에서 일정·공유글·쿠폰 상세 화면으로 바로 이동할 수 있습니다. 이메일은 기본 설정에서 Docker의 Mailpit으로 전달되며, `.env`에 외부 SMTP를 설정하면 실제 수신자에게 발송됩니다.
