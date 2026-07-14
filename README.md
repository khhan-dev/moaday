# MoaDay

가족과 친구의 일정, 공유 자료, 모바일 쿠폰을 한 공간에서 관리하는 SaaS 프로젝트입니다.

현재 구현 범위는 10단계입니다.

- 반응형 웹/PWA 기반
- 이메일 회원가입/로그인 API
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
- 선점 사용자 전용 바코드 열람, 사용 완료와 선점 해제
- 앱 내 알림, 읽음 처리와 활동별 알림 설정
- Mailpit 기반 로컬 이메일 알림
- 프로필 변경과 비밀번호 확인 계정 삭제
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

1~10단계의 인증, 공간, 캘린더, 공유함, 쿠폰함, 알림·설정, 통합검색·대시보드, 세션·초대·공간 생명주기, 감사 기록, 일정-자료 연결과 로컬 운영 안정화 기능을 사용할 수 있습니다. 이메일 알림은 외부로 발송하지 않고 Docker의 Mailpit 수신함에서 안전하게 확인합니다.
