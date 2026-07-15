# GitHub 공개 및 면접 공유 체크리스트

## 공개 전 필수

- [ ] `.env`가 Git 추적 대상이 아닌지 확인
- [ ] Gmail 앱 비밀번호, JWT 키, DB 비밀번호가 커밋에 없는지 확인
- [ ] 채팅이나 화면 공유에 노출된 앱 비밀번호는 새로 발급
- [ ] `git diff --check` 통과
- [ ] API 테스트 통과: `cd apps/api && mvn test`
- [ ] 웹 테스트 통과: `cd apps/web && npm test`
- [ ] Docker 실행 확인: `docker compose up --build -d`
- [ ] 웹 `http://localhost:3000`과 API health 확인
- [ ] README의 저장소 URL·실행 명령 확인
- [ ] 라이선스와 저장소 공개 범위 결정

## 면접관 공유 전 권장

- [ ] GitHub 저장소 설명에 `calendar`, `collaboration`, `coupon`, `spring-boot`, `react`, `docker` 주제 추가
- [ ] README 상단에 실제 화면 캡처 2~4장 추가
- [ ] 데모 계정 대신 회원가입 또는 데모 화면 이용 방법 안내
- [ ] 7분 시연 순서를 한 번 실제로 진행
- [ ] 현재 한계와 다음 개발 항목을 설명할 준비
- [ ] 본인의 역할과 AI 도구 사용 범위를 사실대로 정리

## 권장 커밋 확인 명령

```powershell
git status --short
git diff --check
git check-ignore -v .env
git diff --cached
```

`.env`가 `git check-ignore` 결과에 나타나야 합니다. `git diff --cached`에는 실제 비밀번호나 개인 토큰이 없어야 합니다.

## 공유 메시지 예시

> 가족·친구 그룹의 일정, 게시글·첨부파일, 모바일 쿠폰을 하나의 공간에서 관리하는 반응형 웹 프로젝트입니다. Spring Boot, PostgreSQL, React와 Docker Compose로 구현했으며, 역할 기반 권한, 반복 일정, 쿠폰 동시 선점, 감사 이력과 백업·복원 흐름을 포함합니다. README의 Docker 명령으로 로컬에서 실행할 수 있습니다.
