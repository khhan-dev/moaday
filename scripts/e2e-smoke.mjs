import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const base = process.env.MOADAY_API_URL ?? "http://localhost:8080/api/v1";
const suffix = Date.now().toString(36);
const password = "MoaDay!234";
const userIds = [];

async function request(pathname, { token, method = "GET", body, expected } = {}) {
    const response = await fetch(`${base}${pathname}`, {
        method,
        headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { "Content-Type": "application/json" } : {}) },
        body: body ? JSON.stringify(body) : undefined,
    });
    if (expected) {
        if (response.status !== expected) throw new Error(`${method} ${pathname}: expected ${expected}, received ${response.status}`);
        return null;
    }
    if (!response.ok) throw new Error(`${method} ${pathname}: ${response.status} ${await response.text()}`);
    return response.status === 204 ? null : response.json();
}

async function register(kind) {
    const result = await request("/auth/register", { method: "POST", body: { email: `e2e-${kind}-${suffix}@example.com`, password, displayName: `E2E ${kind}`, timezone: "Asia/Seoul" } });
    userIds.push(result.user.id);
    return result;
}

function docker(...args) {
    const result = spawnSync("docker", args, { cwd: root, encoding: "utf8", windowsHide: true });
    if (result.status !== 0) throw new Error(result.stderr || result.stdout || "Docker 명령을 실행하지 못했습니다.");
    return result.stdout.trim();
}

function cleanup() {
    if (!userIds.length) return;
    const ids = userIds.map(id => `'${id}'`).join(",");
    const sql = `BEGIN; CREATE TEMP TABLE target_users AS SELECT id FROM users WHERE id IN (${ids}); CREATE TEMP TABLE target_spaces AS SELECT id FROM spaces WHERE owner_user_id IN (SELECT id FROM target_users); DELETE FROM audit_logs WHERE actor_id IN (SELECT id FROM target_users) OR space_id IN (SELECT id FROM target_spaces); DELETE FROM event_reminder_deliveries WHERE user_id IN (SELECT id FROM target_users); DELETE FROM notifications WHERE user_id IN (SELECT id FROM target_users) OR space_id IN (SELECT id FROM target_spaces); DELETE FROM coupons WHERE space_id IN (SELECT id FROM target_spaces) OR owner_id IN (SELECT id FROM target_users) OR claimed_by IN (SELECT id FROM target_users) OR used_by IN (SELECT id FROM target_users); DELETE FROM posts WHERE space_id IN (SELECT id FROM target_spaces) OR author_id IN (SELECT id FROM target_users); DELETE FROM events WHERE space_id IN (SELECT id FROM target_spaces) OR created_by IN (SELECT id FROM target_users); DELETE FROM calendars WHERE space_id IN (SELECT id FROM target_spaces) OR created_by IN (SELECT id FROM target_users); DELETE FROM invitations WHERE space_id IN (SELECT id FROM target_spaces) OR invited_by IN (SELECT id FROM target_users); DELETE FROM space_members WHERE space_id IN (SELECT id FROM target_spaces) OR user_id IN (SELECT id FROM target_users); DELETE FROM spaces WHERE id IN (SELECT id FROM target_spaces); DELETE FROM user_preferences WHERE user_id IN (SELECT id FROM target_users); DELETE FROM users WHERE id IN (SELECT id FROM target_users); COMMIT;`;
    const dbUser = docker("compose", "exec", "-T", "postgres", "printenv", "POSTGRES_USER");
    const dbName = docker("compose", "exec", "-T", "postgres", "printenv", "POSTGRES_DB");
    docker("compose", "exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1", "-U", dbUser, "-d", dbName, "-c", sql);
}

try {
    const health = await fetch("http://localhost:8080/actuator/health");
    if (!health.ok) throw new Error("API가 준비되지 않았습니다.");
    const owner = await register("owner");
    const member = await register("member");
    const viewer = await register("viewer");
    const space = await request("/spaces", { token: owner.accessToken, method: "POST", body: { type: "FAMILY", name: `E2E 가족 ${suffix}`, timezone: "Asia/Seoul", color: "#6750A4" } });
    for (const [target, role] of [[member, "MEMBER"], [viewer, "VIEWER"]]) {
        const invite = await request(`/spaces/${space.id}/invitations`, { token: owner.accessToken, method: "POST", body: { email: target.user.email, role } });
        await request("/invitations/accept", { token: target.accessToken, method: "POST", body: { token: invite.oneTimeToken } });
    }
    const post = await request(`/spaces/${space.id}/posts`, { token: member.accessToken, method: "POST", body: { title: `E2E 공유글 ${suffix}`, content: "전체 흐름 점검", tags: ["e2e"] } });
    const visible = await request(`/spaces/${space.id}/posts?query=${suffix}`, { token: viewer.accessToken });
    await request(`/spaces/${space.id}/posts`, { token: viewer.accessToken, method: "POST", body: { title: "차단", content: "차단", tags: [] }, expected: 403 });
    const coupon = await request(`/spaces/${space.id}/coupons`, { token: owner.accessToken, method: "POST", body: { title: `E2E 쿠폰 ${suffix}`, brand: "Moa Cafe", description: "점검", expiresAt: new Date(Date.now() + 86400000).toISOString(), barcodeValue: "8801234567893", barcodeFormat: "EAN13" } });
    const calendars = await request(`/spaces/${space.id}/calendars`, { token: owner.accessToken });
    const startsAt = new Date(Date.now() + 7200000);
    const event = await request(`/calendars/${calendars[0].id}/events`, { token: owner.accessToken, method: "POST", body: {
        title: `E2E 일정 ${suffix}`, description: "연결 자료 점검", location: "Moa Lab", allDay: false,
        startsAt: startsAt.toISOString(), endsAt: new Date(startsAt.getTime() + 3600000).toISOString(), timezone: "Asia/Seoul",
        recurrence: "NONE", recurrenceUntil: null, attendeeUserIds: [member.user.id, viewer.user.id], reminderMinutes: [30],
    } });
    const linked = await request(`/events/${event.id}/resources`, { token: owner.accessToken, method: "PUT", body: { resources: [
        { type: "POST", resourceId: post.post.id }, { type: "COUPON", resourceId: coupon.id },
    ] } });
    const visibleLinks = await request(`/events/${event.id}/resources`, { token: viewer.accessToken });
    const linkable = await request(`/spaces/${space.id}/linkable-resources`, { token: member.accessToken });
    await request(`/coupons/${coupon.id}/claim`, { token: member.accessToken, method: "POST" });
    const barcode = await request(`/coupons/${coupon.id}/barcode`, { token: member.accessToken });
    const search = await request(`/search?query=${suffix}`, { token: owner.accessToken });
    const dashboard = await request("/dashboard", { token: owner.accessToken });
    const used = await request(`/coupons/${coupon.id}/use`, { token: member.accessToken, method: "POST" });
    const couponHistory = await request(`/coupons/${coupon.id}/history`, { token: member.accessToken });
    const auditLogs = await request(`/spaces/${space.id}/audit-logs`, { token: owner.accessToken });
    await request(`/coupons/${coupon.id}/correct`, { token: owner.accessToken, method: "POST", body: { status: "AVAILABLE", reason: "E2E 사용 상태 정정 점검" } });
    const searchTypes = new Set(search.map(item => item.type));
    await request(`/spaces/${space.id}/membership`, { token: viewer.accessToken, method: "DELETE", expected: 204 });
    const viewerSpaces = await request("/spaces", { token: viewer.accessToken });
    await request(`/spaces/${space.id}`, { token: owner.accessToken, method: "DELETE", expected: 204 });
    const memberSpaces = await request("/spaces", { token: member.accessToken });
    const checks = { postVisible: visible.some(item => item.id === post.post.id), viewerBlocked: true, barcodeProtected: barcode.value === "8801234567893", couponUsed: used.status === "USED", couponHistory: couponHistory.some(item => item.action === "COUPON_REVEALED") && couponHistory.some(item => item.action === "COUPON_USED"), auditLog: auditLogs.some(item => item.action === "COUPON_REVEALED"), integratedSearch: searchTypes.has("EVENT") && searchTypes.has("POST") && searchTypes.has("COUPON"), dashboard: dashboard.recentPosts.some(item => item.id === post.post.id) && dashboard.expiringCoupons.some(item => item.id === coupon.id), eventResources: linked.length === 2 && visibleLinks.length === 2 && visibleLinks.every(item => !("barcodeValue" in item)), linkableResources: linkable.some(item => item.resourceId === post.post.id) && linkable.some(item => item.resourceId === coupon.id), memberLeft: !viewerSpaces.some(item => item.id === space.id), spaceArchived: !memberSpaces.some(item => item.id === space.id) };
    if (Object.values(checks).some(value => !value)) throw new Error(`E2E 점검 실패: ${JSON.stringify(checks)}`);
    console.log(`MoaDay E2E 점검 통과 (${Object.keys(checks).length}개 핵심 흐름)`);
} finally {
    cleanup();
}
