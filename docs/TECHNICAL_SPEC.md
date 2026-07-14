# MoaDay MVP 기술 상세 설계

작성일: 2026-07-13  
기준 문서: `PRODUCT_DESIGN.md`, `MVP_PRD.md`

## 1. 구현 원칙

- 프론트엔드는 반응형 웹/PWA로 제공한다.
- 백엔드는 Spring Boot 모듈형 모놀리스로 시작한다.
- PostgreSQL을 정합성의 최종 권위로 사용한다.
- 모든 공유 리소스는 공간 경계를 갖고 서버가 멤버십을 검증한다.
- 쿠폰/파일의 민감 원본은 공개 URL이나 일반 JSON 응답에 포함하지 않는다.
- 모듈 간 후속 작업은 Transactional Outbox를 사용한다.

## 2. 저장소 제안 구조

```text
coupon_with/
  apps/
    web/                         # React 기반 SSR/PWA
    api/                         # Spring Boot
  packages/
    ui/                          # 공통 UI 토큰/컴포넌트
    api-client/                  # OpenAPI 생성 클라이언트
  infra/
    local/                       # Docker Compose
    deploy/                      # 배포 정의
  docs/
    PRODUCT_DESIGN.md
    MVP_PRD.md
    TECHNICAL_SPEC.md
```

백엔드 패키지:

```text
com.couponwith
  identity
  space
  calendar
  content
  coupon
  notification
  audit
  common
```

각 모듈은 `domain`, `application`, `infrastructure`, `web` 계층을 갖는다. 다른 모듈의 Repository를 직접 호출하지 않는다.

## 3. 인증과 요청 문맥

### 인증

- 웹은 HttpOnly, Secure, SameSite 쿠키 기반 세션 또는 짧은 Access Token + 회전 Refresh Token을 사용한다.
- 소셜 로그인은 OIDC/OAuth 공급자 어댑터로 분리한다.
- 상태 변경 요청은 CSRF 방어를 적용한다.
- 세션에는 사용자 식별자만 신뢰하고 공간 역할은 요청마다 DB/짧은 캐시에서 확인한다.

### 공간 문맥

1. 라우트의 `spaceId`를 읽는다.
2. 인증 사용자와 `space_members(space_id, user_id, status=ACTIVE)`를 조회한다.
3. 서버 내부 `SpaceContext`에 spaceId, userId, role, capabilities를 구성한다.
4. Application/Repository 호출에 이 문맥을 명시적으로 전달한다.
5. 요청 종료 시 문맥을 폐기한다.

클라이언트의 역할/소유자/선점자 필드는 권한 판단에 사용하지 않는다.

## 4. 물리 데이터 모델

모든 PK는 UUIDv7 계열, 모든 시각은 `timestamptz`, 사용자 선택 시간대는 IANA 문자열을 사용한다.

### 4.1 Identity/Space

```text
users(
  id PK, email CITEXT UNIQUE, status,
  created_at, updated_at, deleted_at
)

auth_identities(
  id PK, user_id FK, provider, provider_subject,
  created_at, UNIQUE(provider, provider_subject)
)

profiles(
  user_id PK/FK, display_name, avatar_asset_id NULL,
  locale, timezone, onboarding_completed_at NULL
)

spaces(
  id PK, type, name, owner_user_id FK, timezone,
  color, plan, status, created_at, deleted_at,
  UNIQUE(id, owner_user_id)
)

space_members(
  space_id FK, user_id FK, role, status,
  coupon_redeem_allowed, joined_at, removed_at NULL,
  PRIMARY KEY(space_id, user_id)
)

invitations(
  id PK, space_id FK, email CITEXT, role,
  token_hash UNIQUE, invited_by FK, expires_at,
  accepted_at NULL, revoked_at NULL, created_at
)
```

활성 초대 중복은 부분 유니크 인덱스 또는 애플리케이션 트랜잭션으로 `space_id + email`을 제한한다.

### 4.2 Calendar

```text
calendars(
  id PK, space_id FK, name, color, visibility,
  created_by FK, created_at, archived_at NULL,
  UNIQUE(space_id, id)
)

events(
  id PK, space_id FK, calendar_id FK,
  uid VARCHAR(255), title, description, location, external_url,
  starts_at, ends_at, all_day, timezone,
  rrule NULL, visibility, version INT,
  created_by FK, updated_by FK, created_at, updated_at, deleted_at NULL,
  UNIQUE(space_id, id), UNIQUE(uid)
)

event_exceptions(
  id PK, space_id FK, event_id FK, recurrence_id,
  cancelled, override_json JSONB, created_by FK, created_at,
  UNIQUE(event_id, recurrence_id)
)

event_attendees(
  space_id FK, event_id FK, user_id FK,
  response, responded_at NULL,
  PRIMARY KEY(event_id, user_id)
)

reminders(
  id PK, space_id FK, event_id FK,
  user_id NULL, channel, minutes_before,
  UNIQUE(event_id, user_id, channel, minutes_before)
)
```

검사 제약:

- `ends_at >= starts_at`
- `version >= 0`
- `visibility IN (DETAILS, TITLE_ONLY, BUSY_ONLY)`
- 이벤트의 `space_id`와 캘린더의 `space_id`가 같아야 함

### 4.3 Content/File

```text
posts(
  id PK, space_id FK, title, content_json JSONB, content_text,
  pinned, version, created_by FK, updated_by FK,
  created_at, updated_at, deleted_at NULL,
  UNIQUE(space_id, id)
)

post_revisions(
  id PK, space_id FK, post_id FK, version,
  title, content_json JSONB, edited_by FK, created_at,
  UNIQUE(post_id, version)
)

comments(
  id PK, space_id FK, post_id FK, body,
  created_by FK, created_at, updated_at, deleted_at NULL
)

file_assets(
  id PK, space_id FK, storage_key UNIQUE, original_name,
  media_type, size_bytes, sha256, sensitivity,
  scan_status, created_by FK, created_at, deleted_at NULL,
  UNIQUE(space_id, id)
)

file_links(
  file_id FK, space_id FK, resource_type, resource_id,
  created_by FK, created_at,
  PRIMARY KEY(file_id, resource_type, resource_id)
)
```

게시글 본문은 에디터 중립적인 JSON과 검색/내보내기용 평문을 함께 저장한다. HTML을 저장하거나 반환할 때 허용 태그 기반으로 정화한다.

### 4.4 Coupon

```text
coupons(
  id PK, space_id FK, owner_user_id FK,
  brand, product_name, expires_on DATE,
  status, encrypted_code BYTEA NULL, masked_code,
  barcode_asset_id FK NULL, terms_note NULL,
  current_claim_id NULL, version INT,
  created_by FK, created_at, updated_at, deleted_at NULL,
  UNIQUE(space_id, id)
)

coupon_claims(
  id PK, space_id FK, coupon_id FK, claimed_by FK,
  claimed_at, lease_expires_at, revealed_at NULL,
  completed_at NULL, released_at NULL, release_reason NULL,
  UNIQUE(coupon_id, id)
)

coupon_history(
  id PK, space_id FK, coupon_id FK, actor_user_id FK,
  action, from_status NULL, to_status NULL,
  reason NULL, metadata JSONB, created_at
)
```

상태 값:

```text
AVAILABLE -> RESERVED -> USED
AVAILABLE -> EXPIRED
RESERVED  -> AVAILABLE   # 해제/lease 만료
RESERVED  -> EXPIRED
AVAILABLE -> VOIDED
USED      -> AVAILABLE   # 관리자 정정만 허용
```

쿠폰 번호 중복 감지는 암호문 비교가 아닌 정규화 값의 keyed HMAC을 별도 컬럼으로 저장해 같은 공간 안에서만 경고할 수 있다. 전역 중복 여부는 다른 공간 정보 유출 가능성이 있어 사용자에게 노출하지 않는다.

### 4.5 운영

```text
notifications(
  id PK, user_id FK, space_id FK NULL,
  type, resource_type NULL, resource_id NULL,
  safe_payload JSONB, dedupe_key UNIQUE,
  read_at NULL, created_at
)

outbox_events(
  id PK, aggregate_type, aggregate_id, space_id NULL,
  event_type, payload JSONB, occurred_at,
  available_at, processed_at NULL, attempts, last_error NULL
)

audit_logs(
  id PK, space_id FK NULL, actor_user_id FK NULL,
  action, resource_type, resource_id,
  outcome, ip_hash NULL, user_agent_family NULL,
  safe_metadata JSONB, created_at
)
```

## 5. 인덱스

```text
space_members(user_id, status)
invitations(space_id, lower(email)) WHERE accepted_at IS NULL AND revoked_at IS NULL
events(calendar_id, starts_at) WHERE deleted_at IS NULL
events(space_id, starts_at, ends_at) WHERE deleted_at IS NULL
event_exceptions(event_id, recurrence_id)
posts(space_id, updated_at DESC) WHERE deleted_at IS NULL
comments(post_id, created_at) WHERE deleted_at IS NULL
coupons(space_id, status, expires_on) WHERE deleted_at IS NULL
coupon_claims(coupon_id, lease_expires_at DESC)
notifications(user_id, read_at, created_at DESC)
outbox_events(processed_at, available_at) WHERE processed_at IS NULL
audit_logs(space_id, created_at DESC)
```

전문 검색용 GIN 인덱스는 `posts.content_text/title`, `events.title`, `coupons.brand/product_name`에 적용하되 권한 필터를 먼저 결합한다.

## 6. API 규약

### 공통 오류

```json
{
  "code": "COUPON_ALREADY_CLAIMED",
  "message": "다른 구성원이 이 쿠폰을 사용 중입니다.",
  "requestId": "...",
  "details": {}
}
```

| HTTP | 용도 |
|---:|---|
| 400 | 필드/상태 입력 오류 |
| 401 | 인증 필요/세션 만료 |
| 403 | 공간 또는 작업 권한 없음 |
| 404 | 존재하지 않거나 공개할 수 없는 리소스 |
| 409 | 버전 충돌, 이미 선점됨, 중복 초대 |
| 410 | 만료/취소된 초대 또는 삭제된 리소스 |
| 413 | 파일/요청 용량 초과 |
| 422 | 형식은 맞지만 도메인 규칙 위반 |
| 429 | 요청 제한 초과 |

권한이 없는 다른 공간의 리소스는 존재 여부 노출을 줄이기 위해 기본적으로 404를 사용한다.

### 페이지네이션

```json
{
  "items": [],
  "nextCursor": "opaque-or-null"
}
```

정렬 필드와 ID를 서명된 불투명 cursor로 인코딩한다.

## 7. 주요 API 상세

### 공간 생성

```http
POST /api/v1/spaces
Idempotency-Key: ...

{
  "type": "FAMILY",
  "name": "우리 가족",
  "timezone": "Asia/Seoul",
  "color": "token-or-approved-value"
}
```

성공: `201`, 소유자 멤버십과 기본 캘린더를 같은 트랜잭션에서 생성한다.

### 초대 발행

```http
POST /api/v1/spaces/{spaceId}/invitations

{
  "email": "member@example.com",
  "role": "MEMBER"
}
```

성공: `202`. 응답과 로그에 원문 초대 토큰을 포함하지 않고 이메일 전송용 Outbox에만 안전하게 전달한다. 링크 방식은 생성 직후 한 번만 반환하거나 별도 안전한 전달 정책을 사용한다.

### 일정 조회

```http
GET /api/v1/events?from=2026-07-01T00:00:00Z&to=2026-08-01T00:00:00Z&spaceIds=...
```

- 최대 조회 범위 1년
- 반복 원본을 범위 내 occurrence로 확장
- occurrence에는 `seriesId`, `recurrenceId`, `canEdit`, `visibilityApplied` 포함
- `BUSY_ONLY`는 서버가 제목/설명/참석자/연결 리소스를 제거해 반환

### 게시글 수정

```http
PATCH /api/v1/posts/{postId}
If-Match: "7"

{
  "title": "여행 준비물",
  "content": {"type": "doc", "content": []},
  "attachmentIds": []
}
```

성공: 새 개정 저장 후 `version=8`. 버전 불일치: `409 RESOURCE_VERSION_CONFLICT`.

### 파일 업로드

1. `POST /spaces/{spaceId}/files/upload-intents`로 크기/MIME/권한 검사
2. 짧은 수명의 업로드 URL로 직접 업로드
3. `POST /files/{fileId}/complete`로 SHA-256/실제 크기 확인
4. 비동기 악성코드 검사
5. `scan_status=CLEAN`일 때만 일반 열람 허용

### 쿠폰 선점

```http
POST /api/v1/coupons/{couponId}/claims
Idempotency-Key: ...

{
  "expectedVersion": 3
}
```

서버 트랜잭션:

```sql
UPDATE coupons
SET status = 'RESERVED', version = version + 1
WHERE id = :coupon_id
  AND space_id = :authorized_space_id
  AND status = 'AVAILABLE'
  AND version = :expected_version;
```

영향 행이 1개일 때만 claim/history/outbox를 삽입하고 성공한다. 실패하면 최신 안전 상태와 함께 409를 반환한다.

성공 응답에는 바코드를 넣지 않는다.

```json
{
  "claimId": "...",
  "status": "RESERVED",
  "leaseExpiresAt": "2026-07-13T15:15:00Z",
  "version": 4
}
```

### 쿠폰 열람

```http
POST /api/v1/coupons/{couponId}/reveal
Idempotency-Key: ...
```

- 활성 claim의 선점자와 현재 사용자가 일치해야 함
- lease 만료 전이어야 함
- 이미지 검사 상태가 CLEAN이어야 함
- 열람 이력을 먼저 기록
- 60초 내외 서명 URL 또는 일회성 암호 해제 응답 반환
- 응답에 `Cache-Control: no-store` 적용

### 쿠폰 완료

```http
POST /api/v1/coupons/{couponId}/complete

{
  "claimId": "...",
  "expectedVersion": 4
}
```

활성 선점자만 완료 가능하다. `RESERVED -> USED`, claim 완료, history/outbox를 한 트랜잭션에서 처리한다.

## 8. 쿠폰 선점 스케줄러

- Worker는 `lease_expires_at <= now()`인 활성 claim을 작은 batch로 조회한다.
- `FOR UPDATE SKIP LOCKED` 또는 조건부 갱신으로 다중 Worker를 지원한다.
- 쿠폰이 여전히 RESERVED이고 current_claim_id가 일치할 때만 AVAILABLE로 되돌린다.
- 이미 USED/EXPIRED인 쿠폰은 변경하지 않는다.
- 만료 해제 이벤트에는 고유 dedupe key를 부여한다.

유효기간은 그룹 시간대의 날짜 끝을 기준으로 하되 서비스 정책에서 정확한 경계를 고지한다.

## 9. 반복 일정 처리

- DB에는 반복 원본과 예외만 저장한다.
- 요청 범위에서 RFC 5545 RRULE을 확장한다.
- 반복 무한 범위 조회를 금지한다.
- `이번 일정`: exception 생성
- `이후 일정`: 기존 series 종료 + 새 series 생성
- `전체 일정`: 원본 수정, 기존 exception 처리 정책 확인
- 캐시 키에 event version, 조회 범위, 사용자 시간대를 포함한다.

테스트 케이스:

- 매월 31일
- 2월 29일
- 매월 마지막 평일
- DST 시작/종료 시간
- 종일 일정의 시간대 이동
- occurrence 취소 후 전체 series 변경

## 10. 파일/쿠폰 저장소

경로 예시:

```text
spaces/{hashed-space-id}/files/{asset-id}/original
spaces/{hashed-space-id}/coupons/{asset-id}/encrypted-original
```

- 스토리지 버킷은 공개 접근 차단
- API가 권한 검사 후 서명 URL 발급
- Content-Disposition 파일명 정화
- 이미지 EXIF 제거
- 쿠폰 이미지는 일반 파일보다 짧은 URL TTL과 `no-store`
- 삭제는 DB soft-delete 후 비동기 스토리지 제거, 실패 재시도 및 감사 기록

## 11. 알림 Outbox

도메인 트랜잭션에서 아래 이벤트를 기록한다.

```text
InvitationCreated
EventReminderDue
CommentCreated
CouponExpiring
CouponClaimed
CouponClaimExpired
CouponUsed
MemberRemoved
```

Worker는 `dedupe_key = event_id + recipient + channel + template_version`으로 중복 발송을 억제한다. 안전 payload에는 글 본문, 쿠폰 번호, 비공개 일정 상세를 넣지 않는다.

## 12. 캐시 정책

- 공간 멤버십: 짧은 TTL + 역할/제거 시 즉시 무효화
- 월 캘린더 occurrence: event version 기반
- 공개되지 않은 파일/쿠폰 원본: 공유 캐시 금지
- 캐시 키에 반드시 `space_id`, 필요 시 `user_id/visibility` 포함
- 권한 검사 결과를 장시간 캐시하지 않는다.

## 13. 보안 테스트 매트릭스

각 보호 API에 대해 아래를 자동 생성해 테스트한다.

```text
unauthenticated
same-space OWNER
same-space ADMIN
same-space MEMBER resource-owner
same-space MEMBER non-owner
same-space VIEWER
removed-member
different-space user
expired-invitation user
```

필수 검증:

- UI에서 버튼이 숨겨지는지와 무관하게 API가 거부
- `resource_id`만 바꿔 다른 공간 데이터 조회 불가
- 검색/자동완성/알림/감사 로그에서도 제목 유출 없음
- 기존 서명 URL은 짧은 TTL 뒤 무효, 민감 쿠폰은 상태 재검증 가능한 전달 방식 우선
- 로그에 토큰·쿠폰 번호·본문·서명 URL 미포함

## 14. PWA 요구사항

- Web App Manifest, 설치 가능한 아이콘, 테마 색상
- 서비스 워커는 정적 자산만 안전하게 캐시
- 쿠폰 바코드, 비공개 글/일정 API 응답을 오프라인 캐시에 저장하지 않음
- 네트워크 단절 시 읽기 전용 안내와 재시도 제공
- 백그라운드 동기화로 쿠폰 사용 상태를 늦게 보내지 않음. 매장에서 서버 확인 없이 사용 완료로 오인할 수 있기 때문
- Web Push는 권한 요청 전에 이점을 설명하고 사용자가 명시적으로 켜게 함

## 15. 관측과 운영

구조화 로그 공통 필드:

```text
timestamp, level, service, environment, request_id,
actor_id_hash, space_id_hash, route, outcome, error_code, duration_ms
```

알람:

- 5xx 비율과 p95 지연 급증
- 로그인/초대/쿠폰 열람 요청 제한 초과 급증
- 교차 공간 접근 거부 급증
- Outbox 적체/재시도 초과
- 쿠폰 선점 만료 Worker 지연
- 스토리지 삭제/악성코드 검사 실패
- 백업 실패와 복구 시점 지연

## 16. CI/CD 게이트

PR마다:

- 단위/통합 테스트
- OpenAPI 호환성 검사
- DB migration 검증과 롤백 가능성 확인
- 의존성/비밀/정적 보안 검사
- 권한 회귀 테스트
- 웹 접근성 기본 검사
- 프로덕션 빌드

배포 전:

- staging migration 및 smoke test
- 두 공간 교차 접근 E2E
- 동일 쿠폰 동시 선점 부하 테스트
- 파일 업로드/검사/권한 회수 E2E
- 반복 일정 경계 테스트

## 17. 구현 순서

1. 저장소/로컬 인프라/CI와 공통 오류·ID·시간 규칙
2. 인증, 개인 공간, 그룹, 멤버십, 초대
3. 권한 필터와 교차 공간 자동 테스트
4. 캘린더/일정/반복/알림
5. 게시글/첨부/댓글/검색
6. 쿠폰 등록/선점/열람/완료/만료
7. 오늘 화면과 도메인 연결
8. PWA/알림/관측/삭제/백업 복구
9. 비공개 베타와 지표 수집
