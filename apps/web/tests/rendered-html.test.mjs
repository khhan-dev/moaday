import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);
  return worker.fetch(
    new Request("http://localhost/", { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the MoaDay entry experience", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);
  const html = await response.text();
  assert.match(html, /MoaDay/);
  assert.match(html, /로그인 정보를 확인하는 중/);
  assert.doesNotMatch(html, /codex-preview|react-loading-skeleton/);
});

test("removes starter preview code and ships product metadata", async () => {
  const [page, layout, packageJson, manifest, app, calendar, shared, coupons, settings, pagination, invitationPage] = await Promise.all([
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    readFile(new URL("../public/manifest.webmanifest", import.meta.url), "utf8"),
    readFile(new URL("../app/components/MoaDayApp.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/CalendarView.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/SharedView.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/CouponView.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/SettingsView.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/components/Pagination.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/invite/[token]/page.tsx", import.meta.url), "utf8"),
  ]);

  assert.match(page, /MoaDayApp/);
  assert.match(layout, /og\.png/);
  assert.match(layout, /lang="ko"/);
  assert.match(manifest, /"display": "standalone"/);
  assert.match(app, /구성원과 초대 관리/);
  assert.match(app, /초대 이력/);
  assert.match(app, /통합검색/);
  assert.match(app, /다가오는 일정/);
  assert.match(app, /moaday\.auth\.v1/);
  assert.match(app, /초대 수락하고 참여/);
  assert.match(app, /공간 탈퇴/);
  assert.match(app, /공간 삭제/);
  assert.match(app, /감사 기록/);
  assert.match(calendar, /반복 일정 전체 편집/);
  assert.match(calendar, /참석자/);
  assert.match(calendar, /알림/);
  assert.match(calendar, /연결 자료/);
  assert.match(calendar, /공유글·첨부파일·쿠폰/);
  assert.match(calendar, /파일 받기/);
  assert.match(shared, /새 글/);
  assert.match(shared, /첨부파일/);
  assert.match(shared, /ImageAttachment/);
  assert.match(shared, /댓글/);
  assert.match(coupons, /15분 선점/);
  assert.match(coupons, /바코드 보기/);
  assert.match(coupons, /선점 남은 시간/);
  assert.match(coupons, /상태 정정/);
  assert.match(coupons, /쿠폰 이력/);
  assert.match(settings, /앱 내 알림/);
  assert.match(settings, /계정 영구 삭제/);
  assert.match(pagination, /목록 페이지/);
  assert.match(invitationPage, /\?invite=/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);
  await access(new URL("../public/og.png", import.meta.url));
  await assert.rejects(access(new URL("../app/_sites-preview/SkeletonPreview.tsx", import.meta.url)));
});
