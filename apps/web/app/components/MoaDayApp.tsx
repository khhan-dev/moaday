"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, AuthResult, DashboardData, Invitation, InvitationSummary, SearchResult, Space, SpaceMember, SpaceRole, SpaceType } from "../lib/api";
import styles from "./CouponWithApp.module.css";
import { CalendarView } from "./CalendarView";
import { SharedView } from "./SharedView";
import { CouponView } from "./CouponView";
import { SettingsView } from "./SettingsView";

type View = "today" | "calendar" | "posts" | "coupons" | "spaces" | "settings";

const demoSpaces: Space[] = [
  { id: "personal", type: "PERSONAL", name: "훈의 개인 공간", timezone: "Asia/Seoul", color: "sky", role: "OWNER" },
  { id: "family", type: "FAMILY", name: "우리 가족", timezone: "Asia/Seoul", color: "orange", role: "OWNER" },
  { id: "friends", type: "FRIENDS", name: "고등학교 친구들", timezone: "Asia/Seoul", color: "green", role: "MEMBER" },
];

const navigation: Array<{ id: View; label: string; mark: string }> = [
  { id: "today", label: "오늘", mark: "⌂" },
  { id: "calendar", label: "캘린더", mark: "▦" },
  { id: "posts", label: "공유함", mark: "▤" },
  { id: "coupons", label: "쿠폰함", mark: "◇" },
  { id: "spaces", label: "공간", mark: "◎" },
  { id: "settings", label: "알림·설정", mark: "⚙" },
];

export function MoaDayApp() {
  const [session, setSession] = useState<AuthResult | null>(null);
  const [spaces, setSpaces] = useState<Space[]>([]);
  const [view, setView] = useState<View>("today");
  const [isDemo, setIsDemo] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [searchOpen, setSearchOpen] = useState(false);

  useEffect(() => {
    if (!session) return;
    let active = true;
    const load = async () => { try { const result = await api.unreadNotifications(session.accessToken); if (active) setUnreadCount(result.count); } catch {} };
    void load();
    const interval = window.setInterval(load, 30000);
    return () => { active = false; window.clearInterval(interval); };
  }, [session]);

  const enterDemo = () => {
    setIsDemo(true);
    setSpaces(demoSpaces);
    setView("today");
    setUnreadCount(1);
  };

  if (!session && !isDemo) {
    return <AuthScreen onAuthenticated={(result, loadedSpaces) => { setSession(result); setSpaces(loadedSpaces); }} onDemo={enterDemo} />;
  }

  const displayName = session?.user.displayName ?? "훈";
  return (
    <div className={styles.appShell}>
      <aside className={styles.sidebar}>
        <Brand />
        <div className={styles.spacePicker}><span className={styles.spaceDot} />우리 가족 <span aria-hidden="true">⌄</span></div>
        <nav aria-label="주요 메뉴" className={styles.sideNav}>
          {navigation.map((item) => (
            <button key={item.id} type="button" className={view === item.id ? styles.navActive : styles.navButton} onClick={() => setView(item.id)}>
              <span aria-hidden="true">{item.mark}</span>{item.label}{item.id === "settings" && unreadCount > 0 && <b className={styles.navBadge}>{Math.min(unreadCount, 99)}</b>}
            </button>
          ))}
        </nav>
        <div className={styles.account}><span className={styles.avatar}>{displayName.slice(0, 1)}</span><span><strong>{displayName}</strong><small>{isDemo ? "데모 모드" : session?.user.email}</small></span></div>
      </aside>

      <main className={styles.main}>
        <header className={styles.topbar}>
          <div><p className={styles.eyebrow}>{view === "today" ? new Date().toLocaleDateString("ko-KR", { year:"numeric", month:"long", day:"numeric", weekday:"long" }) : "우리의 생활을 한곳에"}</p><h1>{navigation.find((item) => item.id === view)?.label}</h1></div>
          <div className={styles.topActions}><button type="button" className={styles.iconButton} aria-label="통합검색" onClick={() => setSearchOpen(true)}>⌕</button><button type="button" className={styles.iconButton} aria-label={`알림 보기${unreadCount ? `, 읽지 않은 알림 ${unreadCount}개` : ""}`} onClick={() => setView("settings")}>♢{unreadCount > 0 && <b>{Math.min(unreadCount,99)}</b>}</button><button type="button" className={styles.primaryButton} onClick={() => setView(view === "spaces" ? "spaces" : "calendar")}>＋ {view === "spaces" ? "공간 만들기" : "일정 추가"}</button></div>
        </header>

        {view === "today" && <TodayView session={session} demo={isDemo} onNavigate={setView} />}
        {view === "calendar" && <CalendarView spaces={spaces} session={session} demo={isDemo} />}
        {view === "posts" && <SharedView spaces={spaces} session={session} demo={isDemo} />}
        {view === "coupons" && <CouponView spaces={spaces} session={session} demo={isDemo} />}
        {view === "spaces" && <SpacesView spaces={spaces} session={session} demo={isDemo} onSpacesChange={setSpaces} />}
        {view === "settings" && <SettingsView session={session} demo={isDemo} onSessionChange={setSession} onUnreadChange={setUnreadCount} onDeleted={() => { setSession(null); setIsDemo(false); setSpaces([]); setUnreadCount(0); setView("today"); }} />}
      </main>

      {searchOpen && <SearchModal session={session} demo={isDemo} onClose={() => setSearchOpen(false)} onSelect={(target) => { setView(target); setSearchOpen(false); }} />}

      <nav className={styles.bottomNav} aria-label="모바일 주요 메뉴">
        {navigation.map((item) => <button type="button" key={item.id} aria-current={view === item.id ? "page" : undefined} onClick={() => setView(item.id)}><span aria-hidden="true">{item.mark}</span>{item.label}</button>)}
      </nav>
    </div>
  );
}

function Brand() {
  return <div className={styles.brand}><span className={styles.brandMark}>M</span><span>MoaDay</span></div>;
}

function SearchModal({ session, demo, onClose, onSelect }: { session: AuthResult | null; demo: boolean; onClose: () => void; onSelect: (view: View) => void }) {
  const [query,setQuery]=useState(""); const [results,setResults]=useState<SearchResult[]>([]); const [loading,setLoading]=useState(false); const [error,setError]=useState("");
  useEffect(()=>{const value=query.trim();if(value.length<2)return;let active=true;const timer=window.setTimeout(async()=>{setLoading(true);setError("");try{const loaded=demo?demoSearch(value):await api.search(session!.accessToken,value);if(active)setResults(loaded)}catch(reason){if(active)setError(reason instanceof Error?reason.message:"검색하지 못했습니다.")}finally{if(active)setLoading(false)}},250);return()=>{active=false;window.clearTimeout(timer)}},[demo,query,session]);
  const labels={EVENT:"일정",POST:"공유글",COUPON:"쿠폰"};
  return <div className={styles.modalBackdrop}><div className={`${styles.modal} ${styles.searchModal}`} role="dialog" aria-modal="true" aria-labelledby="search-title"><button className={styles.closeButton} type="button" aria-label="닫기" onClick={onClose}>×</button><p className={styles.eyebrow}>모든 공간에서 찾기</p><h2 id="search-title">통합검색</h2><label className={styles.searchInput}><span aria-hidden="true">⌕</span><input autoFocus value={query} onChange={event=>{const value=event.target.value;setQuery(value);if(value.trim().length<2){setResults([]);setLoading(false);setError("")}}} maxLength={100} placeholder="일정, 공유글, 쿠폰을 검색하세요" aria-label="통합검색어"/></label>{query.trim().length<2&&<p className={styles.searchHint}>두 글자 이상 입력해 주세요.</p>}{loading&&<p className={styles.searchHint}>검색 중…</p>}{error&&<p className={styles.error}>{error}</p>}<div className={styles.searchResults}>{results.map(item=><button type="button" key={`${item.type}-${item.id}`} onClick={()=>onSelect(item.targetView)}><span data-type={item.type}>{labels[item.type]}</span><div><strong>{item.title}</strong><small>{item.spaceName} · {item.summary}</small></div>{item.occurredAt&&<time>{new Date(item.occurredAt).toLocaleDateString("ko-KR")}</time>}</button>)}</div>{query.trim().length>=2&&!loading&&results.length===0&&<p className={styles.empty}>검색 결과가 없습니다.</p>}</div></div>;
}

function demoSearch(query:string):SearchResult[]{const values:SearchResult[]=[{type:"EVENT",id:"demo-event",spaceId:"family",spaceName:"우리 가족",title:"엄마 병원 진료",summary:"서울병원",occurredAt:new Date().toISOString(),targetView:"calendar"},{type:"POST",id:"demo-post",spaceId:"family",spaceName:"우리 가족",title:"여름휴가 준비물",summary:"여권, 충전기, 상비약",occurredAt:new Date().toISOString(),targetView:"posts"},{type:"COUPON",id:"demo-coupon",spaceId:"family",spaceName:"우리 가족",title:"아메리카노 쿠폰",summary:"Moa Cafe",occurredAt:new Date().toISOString(),targetView:"coupons"}];return values.filter(item=>`${item.title} ${item.summary}`.includes(query));}

function AuthScreen({ onAuthenticated, onDemo }: { onAuthenticated: (result: AuthResult, spaces: Space[]) => void; onDemo: () => void }) {
  const [mode, setMode] = useState<"login" | "register">("register");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      const result = mode === "register"
        ? await api.register({ email: String(form.get("email")), password: String(form.get("password")), displayName: String(form.get("displayName")), timezone: "Asia/Seoul" })
        : await api.login({ email: String(form.get("email")), password: String(form.get("password")) });
      const loadedSpaces = await api.listSpaces(result.accessToken);
      onAuthenticated(result, loadedSpaces);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "API 서버에 연결하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className={styles.authPage}>
      <section className={styles.authIntro}>
        <Brand />
        <p className={styles.kicker}>함께 알아야 할 것과 함께 쓸 것을 한곳에</p>
        <h1>가족과 친구의 하루를<br />더 가볍게 연결하세요.</h1>
        <p>일정, 준비 자료, 모바일 쿠폰까지. 대화방에서 다시 찾지 않아도 모두가 같은 정보를 봅니다.</p>
        <div className={styles.previewStack}>
          <div><span className={styles.previewTime}>10:00</span><strong>엄마 병원 진료</strong><small>진료 의뢰서.pdf 연결</small></div>
          <div><span className={styles.previewTime}>D-2</span><strong>아메리카노 쿠폰</strong><small>우리 가족 · 사용 가능</small></div>
        </div>
      </section>

      <section className={styles.authPanel}>
        <p className={styles.eyebrow}>{mode === "register" ? "첫 공간을 시작해 보세요" : "다시 만나서 반가워요"}</p>
        <h2>{mode === "register" ? "무료로 시작하기" : "로그인"}</h2>
        <div className={styles.modeTabs} role="tablist" aria-label="인증 방식">
          <button type="button" role="tab" aria-selected={mode === "register"} onClick={() => setMode("register")}>회원가입</button>
          <button type="button" role="tab" aria-selected={mode === "login"} onClick={() => setMode("login")}>로그인</button>
        </div>
        <form onSubmit={submit} className={styles.form}>
          {mode === "register" && <label>이름<input required name="displayName" maxLength={40} placeholder="가족과 친구에게 보일 이름" /></label>}
          <label>이메일<input required type="email" name="email" autoComplete="email" placeholder="name@example.com" /></label>
          <label>비밀번호<input required minLength={8} maxLength={72} type="password" name="password" autoComplete={mode === "register" ? "new-password" : "current-password"} placeholder="8자 이상" /></label>
          {error && <p className={styles.error} role="alert">{error}</p>}
          <button className={styles.primaryButtonLarge} disabled={busy}>{busy ? "처리 중…" : mode === "register" ? "내 공간 만들기" : "로그인"}</button>
        </form>
        <div className={styles.divider}><span>또는</span></div>
        <button type="button" className={styles.demoButton} onClick={onDemo}>데모 화면 둘러보기</button>
        <p className={styles.terms}>계속하면 서비스 이용약관과 개인정보 처리방침에 동의하게 됩니다.</p>
      </section>
    </main>
  );
}

function TodayView({session,demo,onNavigate}:{session:AuthResult|null;demo:boolean;onNavigate:(view:View)=>void}) {
  const [dashboard,setDashboard]=useState<DashboardData|null>(null);const [error,setError]=useState("");
  const [referenceTime]=useState(()=>Date.now());
  useEffect(()=>{let active=true;async function load(){try{const result=demo?demoDashboard():await api.dashboard(session!.accessToken);if(active)setDashboard(result)}catch(reason){if(active)setError(reason instanceof Error?reason.message:"오늘 정보를 불러오지 못했습니다.")}}void load();return()=>{active=false}},[demo,session]);
  if(!dashboard)return <section className={styles.card}><p className={styles.empty}>{error||"오늘 정보를 불러오는 중…"}</p></section>;
  const attendanceLabel:Record<string,string>={ACCEPTED:"참석",DECLINED:"불참",MAYBE:"미정",PENDING:"대기","":""};
  return (
    <><div className={styles.metricStrip}><div><span>공간</span><strong>{dashboard.spaceCount}</strong></div><div><span>다가오는 일정</span><strong>{dashboard.upcomingEventCount}</strong></div><div><span>만료 임박</span><strong>{dashboard.expiringCouponCount}</strong></div><div><span>읽지 않은 알림</span><strong>{dashboard.unreadNotificationCount}</strong></div></div><div className={styles.dashboardGrid}>
      <section className={styles.card}>
        <div className={styles.sectionHeader}><div><h2>다가오는 일정</h2><p>14일 안에 {dashboard.upcomingEventCount}개</p></div><button type="button" onClick={()=>onNavigate("calendar")}>전체 보기 →</button></div>
        <div className={styles.timeline}>
          {dashboard.upcomingEvents.map((item,index)=><TimelineItem key={`${item.id}-${item.startsAt}`} time={item.allDay?"종일":new Date(item.startsAt).toLocaleTimeString("ko-KR",{hour:"2-digit",minute:"2-digit"})} title={item.title} meta={`${item.spaceName}${item.location?` · ${item.location}`:""}`} badge={attendanceLabel[item.attendance]??item.attendance} color={index%3===0?"orange":index%3===1?"green":"blue"} onClick={()=>onNavigate("calendar")}/>) }
          {dashboard.upcomingEvents.length===0&&<p className={styles.empty}>14일 안에 예정된 일정이 없습니다.</p>}
        </div>
      </section>
      <div className={styles.sideColumn}>
        <section className={styles.card}>
          <div className={styles.sectionHeader}><div><h2>만료 임박 쿠폰</h2><p>7일 안에 만료 {dashboard.expiringCouponCount}개</p></div><button type="button" onClick={()=>onNavigate("coupons")}>전체 보기 →</button></div>
          <div className={styles.compactList}>{dashboard.expiringCoupons.map(item=><button type="button" key={item.id} onClick={()=>onNavigate("coupons")}><span>◇</span><p><strong>{item.title}</strong><small>{item.spaceName} · {item.brand}</small></p><b>D-{Math.max(0,Math.ceil((new Date(item.expiresAt).getTime()-referenceTime)/86400000))}</b></button>)}{dashboard.expiringCoupons.length===0&&<p className={styles.empty}>곧 만료되는 쿠폰이 없습니다.</p>}</div>
        </section>
        <section className={styles.card}><div className={styles.sectionHeader}><div><h2>최근 공유글</h2><p>모든 공간의 최신 소식</p></div><button type="button" onClick={()=>onNavigate("posts")}>전체 보기 →</button></div><div className={styles.dashboardPosts}>{dashboard.recentPosts.map(item=><button type="button" key={item.id} onClick={()=>onNavigate("posts")}><span>⌖</span><p><strong>{item.title}</strong><small>{item.spaceName} · 댓글 {item.commentCount} · 파일 {item.attachmentCount}</small></p></button>)}{dashboard.recentPosts.length===0&&<p className={styles.empty}>최근 공유글이 없습니다.</p>}</div></section>
      </div>
    </div></>
  );
}

function TimelineItem({ time, title, meta, badge, color,onClick }: { time: string; title: string; meta: string; badge: string; color: string;onClick:()=>void }) {
  return <button type="button" className={styles.timelineItem} onClick={onClick}><time>{time}</time><span className={`${styles.eventLine} ${styles[color]}`} /><p><strong>{title}</strong><small>{meta}</small></p>{badge&&<em>{badge}</em>}</button>;
}

function demoDashboard():DashboardData{const now=new Date(),tomorrow=new Date(now.getTime()+86400000),couponDate=new Date(now.getTime()+2*86400000);return{spaceCount:3,unreadNotificationCount:1,upcomingEventCount:2,expiringCouponCount:1,upcomingEvents:[{id:"event-1",title:"엄마 병원 진료",location:"서울병원",startsAt:tomorrow.toISOString(),allDay:false,spaceId:"family",spaceName:"우리 가족",attendance:"ACCEPTED"},{id:"event-2",title:"친구들과 저녁",location:"우리 동네",startsAt:new Date(tomorrow.getTime()+8*3600000).toISOString(),allDay:false,spaceId:"friends",spaceName:"고등학교 친구들",attendance:"MAYBE"}],expiringCoupons:[{id:"coupon-1",title:"카페 아메리카노",brand:"Moa Cafe",expiresAt:couponDate.toISOString(),status:"AVAILABLE",spaceId:"family",spaceName:"우리 가족"}],recentPosts:[{id:"post-1",title:"여름휴가 준비물",excerpt:"여권과 충전기를 확인해 주세요.",updatedAt:now.toISOString(),commentCount:3,attachmentCount:2,spaceId:"family",spaceName:"우리 가족"}]}}

function SpacesView({ spaces, session, demo, onSpacesChange }: { spaces: Space[]; session: AuthResult | null; demo: boolean; onSpacesChange: (spaces: Space[]) => void }) {
  const [showCreate, setShowCreate] = useState(false);
  const [managedSpace, setManagedSpace] = useState<Space | null>(null);
  const [spaceMembers, setSpaceMembers] = useState<SpaceMember[]>([]);
  const [spaceInvitations, setSpaceInvitations] = useState<InvitationSummary[]>([]);
  const [createdInvitation, setCreatedInvitation] = useState<Invitation | null>(null);
  const [managementLoading, setManagementLoading] = useState(false);
  const [error, setError] = useState("");
  const grouped = useMemo(() => spaces.filter((space) => space.type !== "PERSONAL"), [spaces]);

  async function createSpace(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input = { type: String(form.get("type")) as SpaceType, name: String(form.get("name")), timezone: "Asia/Seoul", color: "orange" };
    try {
      const created = demo ? { id: crypto.randomUUID(), ...input, role: "OWNER" as const } : await api.createSpace(session!.accessToken, input);
      onSpacesChange([...spaces, created]);
      setShowCreate(false);
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "공간을 만들지 못했습니다."); }
  }

  async function invite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!managedSpace) return;
    const form = new FormData(event.currentTarget);
    const input = { email: String(form.get("email")), role: String(form.get("role")) as SpaceRole };
    try {
      const created = demo
        ? { id: crypto.randomUUID(), spaceId: managedSpace.id, email: input.email, role: input.role, expiresAt: new Date(new Date().getTime() + 604800000).toISOString(), oneTimeToken: "demo-invitation-token" }
        : await api.invite(session!.accessToken, managedSpace.id, input);
      setCreatedInvitation(created);
      setSpaceInvitations((current) => [{ ...created, status: "PENDING", createdAt: new Date().toISOString() }, ...current]);
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "초대를 만들지 못했습니다."); }
  }

  async function openManagement(space: Space) {
    setManagedSpace(space);
    setCreatedInvitation(null);
    setError("");
    setManagementLoading(true);
    const canManage = space.role === "OWNER" || space.role === "ADMIN";
    try {
      if (demo) {
        setSpaceMembers([
          { userId: "demo-owner", displayName: "김모아", email: "owner@moaday.test", role: "OWNER", joinedAt: new Date().toISOString(), currentUser: space.role === "OWNER" },
          { userId: "demo-member", displayName: "이하루", email: "member@moaday.test", role: "MEMBER", joinedAt: new Date().toISOString(), currentUser: space.role === "MEMBER" },
        ]);
        setSpaceInvitations(canManage ? [{ id: "demo-invite", spaceId: space.id, email: "friend@moaday.test", role: "VIEWER", expiresAt: new Date(new Date().getTime() + 604800000).toISOString(), status: "PENDING", createdAt: new Date().toISOString() }] : []);
      } else {
        const [loadedMembers, loadedInvitations] = await Promise.all([
          api.listMembers(session!.accessToken, space.id),
          canManage ? api.listInvitations(session!.accessToken, space.id) : Promise.resolve([]),
        ]);
        setSpaceMembers(loadedMembers);
        setSpaceInvitations(loadedInvitations);
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "공간 정보를 불러오지 못했습니다.");
    } finally {
      setManagementLoading(false);
    }
  }

  async function changeRole(member: SpaceMember, role: SpaceRole) {
    if (!managedSpace) return;
    try {
      const changed = demo ? { ...member, role } : await api.changeMemberRole(session!.accessToken, managedSpace.id, member.userId, role);
      setSpaceMembers((current) => current.map((item) => item.userId === changed.userId ? changed : item));
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "역할을 변경하지 못했습니다."); }
  }

  async function removeMember(member: SpaceMember) {
    if (!managedSpace || !window.confirm(`${member.displayName} 님을 이 공간에서 추방할까요?`)) return;
    try {
      if (!demo) await api.removeMember(session!.accessToken, managedSpace.id, member.userId);
      setSpaceMembers((current) => current.filter((item) => item.userId !== member.userId));
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "구성원을 추방하지 못했습니다."); }
  }

  async function revokeInvitation(invitation: InvitationSummary) {
    if (!managedSpace || !window.confirm(`${invitation.email} 초대를 취소할까요?`)) return;
    try {
      const revoked = demo ? { ...invitation, status: "REVOKED" as const } : await api.revokeInvitation(session!.accessToken, managedSpace.id, invitation.id);
      setSpaceInvitations((current) => current.map((item) => item.id === revoked.id ? revoked : item));
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "초대를 취소하지 못했습니다."); }
  }

  const canManage = managedSpace?.role === "OWNER" || managedSpace?.role === "ADMIN";
  const roleLabel = (role: SpaceRole) => ({ OWNER: "소유자", ADMIN: "관리자", MEMBER: "멤버", VIEWER: "열람자" })[role];
  const invitationStatusLabel = (status: InvitationSummary["status"]) => ({ PENDING: "대기 중", ACCEPTED: "수락됨", REVOKED: "취소됨", EXPIRED: "만료됨" })[status];

  return (
    <section className={styles.card}>
      <div className={styles.sectionHeader}><div><h2>내 공간</h2><p>개인 공간과 함께하는 그룹을 관리합니다.</p></div><button className={styles.primaryButton} type="button" onClick={() => setShowCreate(true)}>＋ 새 공간</button></div>
      <div className={styles.spaceGrid}>
        {spaces.map((space) => <article key={space.id}><span className={styles.spaceGlyph}>{space.type === "PERSONAL" ? "나" : space.type === "FAMILY" ? "집" : "친"}</span><div><small>{space.type === "PERSONAL" ? "개인" : space.type === "FAMILY" ? "가족" : "친구"}</small><h3>{space.name}</h3><p>{roleLabel(space.role)} · Asia/Seoul</p></div>{space.type !== "PERSONAL" && <button type="button" onClick={() => openManagement(space)}>관리</button>}</article>)}
      </div>
      {grouped.length === 0 && <p className={styles.empty}>가족이나 친구 공간을 만들어 첫 구성원을 초대해 보세요.</p>}
      {error && <p className={styles.error} role="alert">{error}</p>}

      {showCreate && <div className={styles.modalBackdrop}><div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="create-title"><button className={styles.closeButton} type="button" aria-label="닫기" onClick={() => setShowCreate(false)}>×</button><p className={styles.eyebrow}>새로운 연결</p><h2 id="create-title">공간 만들기</h2><form className={styles.form} onSubmit={createSpace}><label>공간 종류<select name="type" defaultValue="FAMILY"><option value="FAMILY">가족</option><option value="FRIENDS">친구</option></select></label><label>공간 이름<input required name="name" maxLength={60} placeholder="예: 우리 가족" /></label><button className={styles.primaryButtonLarge}>공간 만들기</button></form></div></div>}

      {managedSpace && <div className={styles.modalBackdrop}><div className={`${styles.modal} ${styles.managementModal}`} role="dialog" aria-modal="true" aria-labelledby="manage-title"><button className={styles.closeButton} type="button" aria-label="닫기" onClick={() => setManagedSpace(null)}>×</button><p className={styles.eyebrow}>{managedSpace.name}</p><h2 id="manage-title">구성원과 초대 관리</h2>
        {managementLoading ? <p className={styles.empty}>구성원 정보를 불러오는 중…</p> : <div className={styles.managementSections}>
          <section><div className={styles.managementHeading}><div><h3>구성원</h3><p>{spaceMembers.length}명 참여 중</p></div></div><div className={styles.memberList}>{spaceMembers.map((member) => {
            const editable = canManage && !member.currentUser && member.role !== "OWNER" && !(managedSpace.role === "ADMIN" && member.role === "ADMIN");
            return <div className={styles.memberRow} key={member.userId}><span className={styles.avatar}>{member.displayName.slice(0, 1)}</span><div><strong>{member.displayName}{member.currentUser ? " (나)" : ""}</strong><small>{member.email}</small></div>{editable ? <select aria-label={`${member.displayName} 역할`} value={member.role} onChange={(event) => changeRole(member, event.target.value as SpaceRole)}>{managedSpace.role === "OWNER" && <option value="ADMIN">관리자</option>}<option value="MEMBER">멤버</option><option value="VIEWER">열람자</option></select> : <span className={styles.statusChip}>{roleLabel(member.role)}</span>}{editable && <button className={styles.dangerButton} type="button" onClick={() => removeMember(member)}>추방</button>}</div>;
          })}</div></section>

          {canManage && <section><div className={styles.managementHeading}><div><h3>새 구성원 초대</h3><p>초대 링크는 7일 동안 유효합니다.</p></div></div>{createdInvitation ? <div className={styles.inviteResult}><strong>{createdInvitation.email}</strong><code>{`${location.origin}/invite/${createdInvitation.oneTimeToken}`}</code><button className={styles.primaryButtonLarge} type="button" onClick={() => navigator.clipboard?.writeText(`${location.origin}/invite/${createdInvitation.oneTimeToken}`)}>초대 링크 복사</button><button className={styles.textButton} type="button" onClick={() => setCreatedInvitation(null)}>다른 구성원 초대</button></div> : <form className={`${styles.form} ${styles.inlineInviteForm}`} onSubmit={invite}><label>이메일<input required type="email" name="email" placeholder="member@example.com" /></label><label>역할<select name="role" defaultValue="MEMBER"><option value="MEMBER">멤버</option>{managedSpace.role === "OWNER" && <option value="ADMIN">관리자</option>}<option value="VIEWER">열람자</option></select></label><button className={styles.primaryButtonLarge}>초대 만들기</button></form>}</section>}

          {canManage && <section><div className={styles.managementHeading}><div><h3>초대 이력</h3><p>만료되거나 취소된 초대도 확인할 수 있습니다.</p></div></div><div className={styles.invitationList}>{spaceInvitations.length === 0 ? <p className={styles.empty}>아직 발급된 초대가 없습니다.</p> : spaceInvitations.map((invitation) => <div key={invitation.id}><div><strong>{invitation.email}</strong><small>{roleLabel(invitation.role)} · {new Date(invitation.expiresAt).toLocaleDateString("ko-KR")}까지</small></div><span className={styles.statusChip} data-status={invitation.status}>{invitationStatusLabel(invitation.status)}</span>{invitation.status === "PENDING" && <button className={styles.dangerButton} type="button" onClick={() => revokeInvitation(invitation)}>취소</button>}</div>)}</div></section>}
        </div>}
        {error && <p className={styles.error} role="alert">{error}</p>}
      </div></div>}
    </section>
  );
}
