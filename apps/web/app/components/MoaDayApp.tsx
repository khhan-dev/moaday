"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { api, AuditEntry, AuthResult, DashboardData, EmailDelivery, Invitation, InvitationSummary, ReceivedInvitation, SearchResult, Space, SpaceMember, SpaceRole, SpaceType } from "../lib/api";
import styles from "./CouponWithApp.module.css";
import { CalendarView } from "./CalendarView";
import { SharedView } from "./SharedView";
import { CouponView } from "./CouponView";
import { SettingsView } from "./SettingsView";
import { ModalPortal } from "./ModalPortal";
import { DetailTarget, targetFromUrl, viewForDetail, writeDetailUrl } from "../lib/detail-navigation";

type View = "today" | "calendar" | "posts" | "coupons" | "spaces" | "settings";
const SESSION_KEY = "moaday.auth.v1";

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
const auditActionLabel:Record<string,string>={MEMBER_ROLE_CHANGED:"역할 변경",MEMBER_REMOVED:"구성원 추방",MEMBER_LEFT:"구성원 탈퇴",SPACE_ARCHIVED:"공간 삭제",INVITATION_RESENT:"초대 메일 재발송",FILE_DOWNLOADED:"파일 열람",COUPON_CREATED:"쿠폰 등록",COUPON_UPDATED:"쿠폰 수정",COUPON_IMAGE_UPDATED:"쿠폰 이미지 저장",COUPON_IMAGE_DELETED:"쿠폰 이미지 삭제",COUPON_CLAIMED:"쿠폰 선점",COUPON_RELEASED:"선점 해제",COUPON_AUTO_RELEASED:"자동 해제",COUPON_REVEALED:"바코드 열람",COUPON_USED:"사용 완료",COUPON_EXPIRED:"쿠폰 만료",COUPON_CORRECTED:"상태 정정"};
const spaceRoleLabel=(role:SpaceRole)=>({OWNER:"소유자",ADMIN:"관리자",MEMBER:"멤버",VIEWER:"열람자"})[role];

export function MoaDayApp() {
  const [session, setSession] = useState<AuthResult | null>(null);
  const [spaces, setSpaces] = useState<Space[]>([]);
  const [view, setView] = useState<View>("today");
  const [isDemo, setIsDemo] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [searchOpen, setSearchOpen] = useState(false);
  const [sessionChecked,setSessionChecked]=useState(false);
  const [inviteToken,setInviteToken]=useState("");
  const [receivedInvitations,setReceivedInvitations]=useState<ReceivedInvitation[]>([]);
  const [detailTarget,setDetailTarget]=useState<DetailTarget|null>(null);

  useEffect(()=>{let active=true;async function restore(){await Promise.resolve();const url=new URL(window.location.href);const queryToken=url.searchParams.get("invite")??"";const target=targetFromUrl(url);if(active){setInviteToken(queryToken);setDetailTarget(target);if(target)setView(viewForDetail(target.type))}const saved=window.localStorage.getItem(SESSION_KEY);if(!saved){if(active)setSessionChecked(true);return}try{const restored=JSON.parse(saved) as AuthResult;if(new Date(restored.expiresAt).getTime()<=Date.now())throw new Error("expired");const loadedSpaces=await api.listSpaces(restored.accessToken);if(active){setSession(restored);setSpaces(loadedSpaces)}}catch{window.localStorage.removeItem(SESSION_KEY)}finally{if(active)setSessionChecked(true)}}void restore();return()=>{active=false}},[]);

  useEffect(() => {
    if (!session) return;
    let active = true;
    const load = async () => { try { const result = await api.unreadNotifications(session.accessToken); if (active) setUnreadCount(result.count); } catch {} };
    void load();
    const interval = window.setInterval(load, 30000);
    return () => { active = false; window.clearInterval(interval); };
  }, [session]);

  useEffect(() => {
    if (!session || isDemo) return;
    let active = true;
    const load = async () => { try { const items = await api.listReceivedInvitations(session.accessToken); if (active) setReceivedInvitations(items); } catch {} };
    void load();
    const interval = window.setInterval(load, 30000);
    return () => { active = false; window.clearInterval(interval); };
  }, [isDemo, session]);

  const enterDemo = () => {
    setIsDemo(true);
    setSpaces(demoSpaces);
    setView("today");
    setUnreadCount(1);
    setReceivedInvitations([]);
  };

  const saveSession=(next:AuthResult,loadedSpaces?:Space[])=>{window.localStorage.setItem(SESSION_KEY,JSON.stringify(next));setSession(next);if(loadedSpaces)setSpaces(loadedSpaces)};
  const logout=()=>{window.localStorage.removeItem(SESSION_KEY);setSession(null);setSpaces([]);setUnreadCount(0);setReceivedInvitations([]);setView("today")};
  const exitCurrent=()=>{if(isDemo){setIsDemo(false);setSpaces([]);setUnreadCount(0);setReceivedInvitations([]);setView("today")}else logout()};
  const clearInvitation=()=>{setInviteToken("");const url=new URL(window.location.href);url.searchParams.delete("invite");window.history.replaceState({},"",`${url.pathname}${url.search}${url.hash}`)};
  const navigate=useCallback((nextView:View,target:DetailTarget|null=null)=>{setView(nextView);setDetailTarget(target);writeDetailUrl(target)},[]);
  const openTarget=useCallback((target:DetailTarget)=>navigate(viewForDetail(target.type),target),[navigate]);
  const clearTarget=useCallback(()=>{setDetailTarget(null);writeDetailUrl(null)},[]);
  const acceptReceived=async(invitation:ReceivedInvitation)=>{const space=await api.acceptReceivedInvitation(session!.accessToken,invitation.id);setSpaces(current=>current.some(item=>item.id===space.id)?current:[...current,space]);setReceivedInvitations(current=>current.filter(item=>item.id!==invitation.id));setView("spaces")};
  const declineReceived=async(invitation:ReceivedInvitation)=>{await api.declineReceivedInvitation(session!.accessToken,invitation.id);setReceivedInvitations(current=>current.filter(item=>item.id!==invitation.id))};

  if(!sessionChecked)return <main className={styles.authLoading}><Brand/><p>로그인 정보를 확인하는 중…</p></main>;

  if (!session && !isDemo) {
    return <AuthScreen invitePending={Boolean(inviteToken)} onAuthenticated={(result, loadedSpaces) => saveSession(result,loadedSpaces)} onDemo={enterDemo} />;
  }

  const displayName = session?.user.displayName ?? "훈";
  return (
    <div className={styles.appShell}>
      <aside className={styles.sidebar}>
        <Brand />
        <nav aria-label="주요 메뉴" className={styles.sideNav}>
          {navigation.map((item) => (
            <button key={item.id} type="button" className={view === item.id ? styles.navActive : styles.navButton} onClick={() => navigate(item.id)}>
              <span aria-hidden="true">{item.mark}</span>{item.label}{item.id === "spaces" && receivedInvitations.length > 0 && <b className={styles.navBadge}>{Math.min(receivedInvitations.length,99)}</b>}{item.id === "settings" && unreadCount > 0 && <b className={styles.navBadge}>{Math.min(unreadCount, 99)}</b>}
            </button>
          ))}
        </nav>
        <div className={styles.account}><span className={styles.avatar}>{displayName.slice(0, 1)}</span><span><strong>{displayName}</strong><small>{isDemo ? "데모 모드" : session?.user.email}</small></span><button type="button" className={styles.logoutButton} onClick={exitCurrent}>로그아웃</button></div>
      </aside>

      <main className={styles.main}>
        <header className={styles.topbar}>
          <div><p className={styles.eyebrow}>{view === "today" ? new Date().toLocaleDateString("ko-KR", { year:"numeric", month:"long", day:"numeric", weekday:"long" }) : "우리의 생활을 한곳에"}</p><h1>{navigation.find((item) => item.id === view)?.label}</h1></div>
          <div className={styles.topActions}><button type="button" className={styles.iconButton} aria-label="통합검색" onClick={() => setSearchOpen(true)}>⌕</button><button type="button" className={styles.iconButton} aria-label={`알림 보기${unreadCount ? `, 읽지 않은 알림 ${unreadCount}개` : ""}`} onClick={() => navigate("settings")}>♢{unreadCount > 0 && <b>{Math.min(unreadCount,99)}</b>}</button><button type="button" className={styles.primaryButton} onClick={() => navigate(view === "spaces" ? "spaces" : "calendar")}>＋ {view === "spaces" ? "공간 만들기" : "일정 추가"}</button></div>
        </header>

        {receivedInvitations.length>0&&<ReceivedInvitationsPanel invitations={receivedInvitations} onAccept={acceptReceived} onDecline={declineReceived}/>}

        {view === "today" && <TodayView session={session} demo={isDemo} onNavigate={navigate} onOpenTarget={openTarget} />}
        {view === "calendar" && <CalendarView spaces={spaces} session={session} demo={isDemo} target={detailTarget?.type==="EVENT"?detailTarget:null} onTargetClose={clearTarget} onOpenResource={openTarget} />}
        {view === "posts" && <SharedView spaces={spaces} session={session} demo={isDemo} target={detailTarget?.type==="POST"?detailTarget:null} onTargetClose={clearTarget} />}
        {view === "coupons" && <CouponView spaces={spaces} session={session} demo={isDemo} target={detailTarget?.type==="COUPON"?detailTarget:null} onTargetClose={clearTarget} />}
        {view === "spaces" && <SpacesView spaces={spaces} session={session} demo={isDemo} onSpacesChange={setSpaces} />}
        {view === "settings" && <SettingsView session={session} demo={isDemo} onSessionChange={saveSession} onUnreadChange={setUnreadCount} onOpenTarget={openTarget} onLogout={exitCurrent} onDeleted={() => { window.localStorage.removeItem(SESSION_KEY); setSession(null); setIsDemo(false); setSpaces([]); setUnreadCount(0); setView("today"); }} />}
      </main>

      {searchOpen && <SearchModal session={session} demo={isDemo} onClose={() => setSearchOpen(false)} onSelect={(result) => { openTarget({type:result.type,id:result.id,spaceId:result.spaceId}); setSearchOpen(false); }} />}
      {inviteToken&&session&&<InvitationAcceptance token={inviteToken} session={session} onClose={clearInvitation} onAccepted={(space)=>{setSpaces(current=>current.some(item=>item.id===space.id)?current:[...current,space]);setReceivedInvitations(current=>current.filter(item=>item.spaceId!==space.id));clearInvitation();setView("spaces")}}/>}

      <nav className={styles.bottomNav} aria-label="모바일 주요 메뉴">
        {navigation.map((item) => <button type="button" key={item.id} aria-current={view === item.id ? "page" : undefined} onClick={() => navigate(item.id)}><span aria-hidden="true">{item.mark}</span>{item.label}</button>)}
      </nav>
    </div>
  );
}

function ReceivedInvitationsPanel({invitations,onAccept,onDecline}:{invitations:ReceivedInvitation[];onAccept:(invitation:ReceivedInvitation)=>Promise<void>;onDecline:(invitation:ReceivedInvitation)=>Promise<void>}){const [busyId,setBusyId]=useState("");const [error,setError]=useState("");async function respond(invitation:ReceivedInvitation,action:"accept"|"decline"){setBusyId(invitation.id);setError("");try{if(action==="accept")await onAccept(invitation);else await onDecline(invitation)}catch(reason){setError(reason instanceof Error?reason.message:"초대에 응답하지 못했습니다.")}finally{setBusyId("")}}return <section className={`${styles.card} ${styles.receivedInvitations}`} aria-labelledby="received-invitations-title"><div className={styles.sectionHeader}><div><h2 id="received-invitations-title">받은 공간 초대</h2><p>초대 내용을 확인하고 참여 여부를 선택해 주세요.</p></div><span className={styles.invitationCount}>{invitations.length}개</span></div><div className={styles.receivedInvitationList}>{invitations.map(invitation=><article key={invitation.id}><span className={styles.spaceGlyph}>{invitation.spaceType==="FAMILY"?"가":"친"}</span><div><strong>{invitation.spaceName}</strong><small>{invitation.invitedByName} 님이 {spaceRoleLabel(invitation.role)} 역할로 초대 · {new Date(invitation.expiresAt).toLocaleDateString("ko-KR")}까지</small></div><div className={styles.receivedInvitationActions}><button type="button" disabled={busyId===invitation.id} onClick={()=>void respond(invitation,"decline")}>거절</button><button type="button" className={styles.primaryButton} disabled={busyId===invitation.id} onClick={()=>void respond(invitation,"accept")}>{busyId===invitation.id?"처리 중…":"수락"}</button></div></article>)}</div>{error&&<p className={styles.error} role="alert">{error}</p>}</section>}

function InvitationAcceptance({token,session,onClose,onAccepted}:{token:string;session:AuthResult;onClose:()=>void;onAccepted:(space:Space)=>void}){const [busy,setBusy]=useState(false);const [error,setError]=useState("");async function accept(){setBusy(true);setError("");try{onAccepted(await api.acceptInvitation(session.accessToken,token))}catch(reason){setError(reason instanceof Error?reason.message:"초대를 수락하지 못했습니다.")}finally{setBusy(false)}}return <ModalPortal><div className={styles.modalBackdrop}><div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="invitation-accept-title"><button className={styles.closeButton} type="button" aria-label="나중에 하기" onClick={onClose}>×</button><p className={styles.eyebrow}>MoaDay 공간 초대</p><h2 id="invitation-accept-title">초대를 수락할까요?</h2><p className={styles.modalDescription}>{session.user.email} 계정으로 초대된 공간에 참여합니다.</p>{error&&<p className={styles.error} role="alert">{error}</p>}<button type="button" className={styles.primaryButtonLarge} disabled={busy} onClick={accept}>{busy?"참여하는 중…":"초대 수락하고 참여"}</button><button type="button" className={styles.textButton} onClick={onClose}>나중에 하기</button></div></div></ModalPortal>}

function Brand() {
  return <div className={styles.brand}><span className={styles.brandMark}>M</span><span>MoaDay</span></div>;
}

function SearchModal({ session, demo, onClose, onSelect }: { session: AuthResult | null; demo: boolean; onClose: () => void; onSelect: (result: SearchResult) => void }) {
  const [query,setQuery]=useState(""); const [results,setResults]=useState<SearchResult[]>([]); const [loading,setLoading]=useState(false); const [error,setError]=useState("");
  useEffect(()=>{const value=query.trim();if(value.length<2)return;let active=true;const timer=window.setTimeout(async()=>{setLoading(true);setError("");try{const loaded=demo?demoSearch(value):await api.search(session!.accessToken,value);if(active)setResults(loaded)}catch(reason){if(active)setError(reason instanceof Error?reason.message:"검색하지 못했습니다.")}finally{if(active)setLoading(false)}},250);return()=>{active=false;window.clearTimeout(timer)}},[demo,query,session]);
  const labels={EVENT:"일정",POST:"공유글",COUPON:"쿠폰"};
  return <ModalPortal><div className={styles.modalBackdrop}><div className={`${styles.modal} ${styles.searchModal}`} role="dialog" aria-modal="true" aria-labelledby="search-title"><button className={styles.closeButton} type="button" aria-label="닫기" onClick={onClose}>×</button><p className={styles.eyebrow}>모든 공간에서 찾기</p><h2 id="search-title">통합검색</h2><label className={styles.searchInput}><span aria-hidden="true">⌕</span><input autoFocus value={query} onChange={event=>{const value=event.target.value;setQuery(value);if(value.trim().length<2){setResults([]);setLoading(false);setError("")}}} maxLength={100} placeholder="일정, 공유글, 쿠폰을 검색하세요" aria-label="통합검색어"/></label>{query.trim().length<2&&<p className={styles.searchHint}>두 글자 이상 입력해 주세요.</p>}{loading&&<p className={styles.searchHint}>검색 중…</p>}{error&&<p className={styles.error}>{error}</p>}<div className={styles.searchResults}>{results.map(item=><button type="button" key={`${item.type}-${item.id}`} onClick={()=>onSelect(item)}><span data-type={item.type}>{labels[item.type]}</span><div><strong>{item.title}</strong><small>{item.spaceName} · {item.summary}</small></div>{item.occurredAt&&<time>{new Date(item.occurredAt).toLocaleDateString("ko-KR")}</time>}</button>)}</div>{query.trim().length>=2&&!loading&&results.length===0&&<p className={styles.empty}>검색 결과가 없습니다.</p>}</div></div></ModalPortal>;
}

function demoSearch(query:string):SearchResult[]{const values:SearchResult[]=[{type:"EVENT",id:"demo-event",spaceId:"family",spaceName:"우리 가족",title:"엄마 병원 진료",summary:"서울병원",occurredAt:new Date().toISOString(),targetView:"calendar"},{type:"POST",id:"demo-post",spaceId:"family",spaceName:"우리 가족",title:"여름휴가 준비물",summary:"여권, 충전기, 상비약",occurredAt:new Date().toISOString(),targetView:"posts"},{type:"COUPON",id:"demo-coupon",spaceId:"family",spaceName:"우리 가족",title:"아메리카노 쿠폰",summary:"Moa Cafe",occurredAt:new Date().toISOString(),targetView:"coupons"}];return values.filter(item=>`${item.title} ${item.summary}`.includes(query));}

function AuthScreen({ onAuthenticated, onDemo,invitePending }: { onAuthenticated: (result: AuthResult, spaces: Space[]) => void; onDemo: () => void;invitePending:boolean }) {
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
        {invitePending&&<p className={styles.inviteNotice}>초대받은 이메일 계정으로 로그인하거나 회원가입해 주세요.</p>}
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

function TodayView({session,demo,onNavigate,onOpenTarget}:{session:AuthResult|null;demo:boolean;onNavigate:(view:View)=>void;onOpenTarget:(target:DetailTarget)=>void}) {
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
          {dashboard.upcomingEvents.map((item,index)=><TimelineItem key={`${item.id}-${item.startsAt}`} time={item.allDay?"종일":new Date(item.startsAt).toLocaleTimeString("ko-KR",{hour:"2-digit",minute:"2-digit"})} title={item.title} meta={`${item.spaceName}${item.location?` · ${item.location}`:""}`} badge={attendanceLabel[item.attendance]??item.attendance} color={index%3===0?"orange":index%3===1?"green":"blue"} onClick={()=>onOpenTarget({type:"EVENT",id:item.id,spaceId:item.spaceId})}/>) }
          {dashboard.upcomingEvents.length===0&&<p className={styles.empty}>14일 안에 예정된 일정이 없습니다.</p>}
        </div>
      </section>
      <div className={styles.sideColumn}>
        <section className={styles.card}>
          <div className={styles.sectionHeader}><div><h2>만료 임박 쿠폰</h2><p>7일 안에 만료 {dashboard.expiringCouponCount}개</p></div><button type="button" onClick={()=>onNavigate("coupons")}>전체 보기 →</button></div>
          <div className={styles.compactList}>{dashboard.expiringCoupons.map(item=><button type="button" key={item.id} onClick={()=>onOpenTarget({type:"COUPON",id:item.id,spaceId:item.spaceId})}><span>◇</span><p><strong>{item.title}</strong><small>{item.spaceName} · {item.brand}</small></p><b>D-{Math.max(0,Math.ceil((new Date(item.expiresAt).getTime()-referenceTime)/86400000))}</b></button>)}{dashboard.expiringCoupons.length===0&&<p className={styles.empty}>곧 만료되는 쿠폰이 없습니다.</p>}</div>
        </section>
        <section className={styles.card}><div className={styles.sectionHeader}><div><h2>최근 공유글</h2><p>모든 공간의 최신 소식</p></div><button type="button" onClick={()=>onNavigate("posts")}>전체 보기 →</button></div><div className={styles.dashboardPosts}>{dashboard.recentPosts.map(item=><button type="button" key={item.id} onClick={()=>onOpenTarget({type:"POST",id:item.id,spaceId:item.spaceId})}><span>⌖</span><p><strong>{item.title}</strong><small>{item.spaceName} · 댓글 {item.commentCount} · 파일 {item.attachmentCount}</small></p></button>)}{dashboard.recentPosts.length===0&&<p className={styles.empty}>최근 공유글이 없습니다.</p>}</div></section>
      </div>
    </div></>
  );
}

function TimelineItem({ time, title, meta, badge, color,onClick }: { time: string; title: string; meta: string; badge: string; color: string;onClick:()=>void }) {
  return <button type="button" className={styles.timelineItem} onClick={onClick}><time>{time}</time><span className={`${styles.eventLine} ${styles[color]}`} /><p><strong>{title}</strong><small>{meta}</small></p>{badge&&<em>{badge}</em>}</button>;
}

function demoDashboard():DashboardData{const now=new Date(),tomorrow=new Date(now.getTime()+86400000),couponDate=new Date(now.getTime()+2*86400000);return{spaceCount:3,unreadNotificationCount:1,upcomingEventCount:1,expiringCouponCount:1,upcomingEvents:[{id:"demo-event",title:"함께 저녁 먹기",location:"우리 동네",startsAt:tomorrow.toISOString(),allDay:false,spaceId:"family",spaceName:"우리 가족",attendance:"ACCEPTED"}],expiringCoupons:[{id:"demo-coupon",title:"아메리카노 한 잔",brand:"Moa Cafe",expiresAt:couponDate.toISOString(),status:"AVAILABLE",spaceId:"family",spaceName:"우리 가족"}],recentPosts:[{id:"demo-post",title:"여름휴가 준비물",excerpt:"여권과 충전기를 확인해 주세요.",updatedAt:now.toISOString(),commentCount:3,attachmentCount:2,spaceId:"family",spaceName:"우리 가족"}]}}

function SpacesView({ spaces, session, demo, onSpacesChange }: { spaces: Space[]; session: AuthResult | null; demo: boolean; onSpacesChange: (spaces: Space[]) => void }) {
  const [showCreate, setShowCreate] = useState(false);
  const [managedSpace, setManagedSpace] = useState<Space | null>(null);
  const [spaceMembers, setSpaceMembers] = useState<SpaceMember[]>([]);
  const [spaceInvitations, setSpaceInvitations] = useState<InvitationSummary[]>([]);
  const [spaceAudits,setSpaceAudits]=useState<AuditEntry[]>([]);
  const [emailDeliveries,setEmailDeliveries]=useState<EmailDelivery[]>([]);
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
        ? { id: crypto.randomUUID(), spaceId: managedSpace.id, email: input.email, role: input.role, expiresAt: new Date(new Date().getTime() + 604800000).toISOString(), oneTimeToken: "demo-invitation-token", emailQueued: true }
        : await api.invite(session!.accessToken, managedSpace.id, input);
      setCreatedInvitation(created);
      setSpaceInvitations((current) => [{ ...created, status: "PENDING", createdAt: new Date().toISOString() }, ...current]);
      if (!demo) setEmailDeliveries(await api.listEmailDeliveries(session!.accessToken, managedSpace.id));
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
        setSpaceAudits(canManage?[{id:"demo-audit",spaceId:space.id,actorId:"demo-owner",actorName:"김모아",action:"MEMBER_ROLE_CHANGED",resourceType:"MEMBER",summary:"이하루 역할 변경: VIEWER → MEMBER",createdAt:new Date().toISOString()}]:[]);
        setEmailDeliveries(canManage?[{id:"demo-mail",invitationId:"demo-invite",category:"INVITATION",recipient:"friend@moaday.test",subject:"[MoaDay] 친구 공간에 초대되었습니다",status:"SENT",attemptCount:1,maxAttempts:5,sentAt:new Date().toISOString(),createdAt:new Date().toISOString()}]:[]);
      } else {
        const [loadedMembers, loadedInvitations,loadedAudits,loadedDeliveries] = await Promise.all([
          api.listMembers(session!.accessToken, space.id),
          canManage ? api.listInvitations(session!.accessToken, space.id) : Promise.resolve([]),
          canManage ? api.listAuditLogs(session!.accessToken,space.id) : Promise.resolve([]),
          canManage ? api.listEmailDeliveries(session!.accessToken,space.id) : Promise.resolve([]),
        ]);
        setSpaceMembers(loadedMembers);
        setSpaceInvitations(loadedInvitations);
        setSpaceAudits(loadedAudits);
        setEmailDeliveries(loadedDeliveries);
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
      if(!demo)setSpaceAudits(await api.listAuditLogs(session!.accessToken,managedSpace.id));
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "역할을 변경하지 못했습니다."); }
  }

  async function removeMember(member: SpaceMember) {
    if (!managedSpace || !window.confirm(`${member.displayName} 님을 이 공간에서 추방할까요?`)) return;
    try {
      if (!demo) await api.removeMember(session!.accessToken, managedSpace.id, member.userId);
      setSpaceMembers((current) => current.filter((item) => item.userId !== member.userId));
      if(!demo)setSpaceAudits(await api.listAuditLogs(session!.accessToken,managedSpace.id));
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "구성원을 추방하지 못했습니다."); }
  }

  async function revokeInvitation(invitation: InvitationSummary) {
    if (!managedSpace || !window.confirm(`${invitation.email} 초대를 취소할까요?`)) return;
    try {
      const revoked = demo ? { ...invitation, status: "REVOKED" as const } : await api.revokeInvitation(session!.accessToken, managedSpace.id, invitation.id);
      setSpaceInvitations((current) => current.map((item) => item.id === revoked.id ? revoked : item));
      if (!demo) setEmailDeliveries(await api.listEmailDeliveries(session!.accessToken, managedSpace.id));
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "초대를 취소하지 못했습니다."); }
  }

  async function resendInvitation(invitation:InvitationSummary){if(!managedSpace)return;try{const resent=demo?{id:invitation.id,spaceId:managedSpace.id,email:invitation.email,role:invitation.role,expiresAt:invitation.expiresAt,oneTimeToken:"demo-resent-token",emailQueued:true}:await api.resendInvitation(session!.accessToken,managedSpace.id,invitation.id);setCreatedInvitation(resent);if(!demo)setEmailDeliveries(await api.listEmailDeliveries(session!.accessToken,managedSpace.id));setError("")}catch(reason){setError(reason instanceof Error?reason.message:"초대 메일을 재발송하지 못했습니다.")}}

  async function exitSpace(){if(!managedSpace)return;const deleting=managedSpace.role==="OWNER";const prompt=deleting?`“${managedSpace.name}” 공간을 삭제할까요? 모든 구성원의 접근이 즉시 종료됩니다.`:`“${managedSpace.name}” 공간에서 탈퇴할까요?`;if(!window.confirm(prompt))return;try{if(!demo){if(deleting)await api.archiveSpace(session!.accessToken,managedSpace.id);else await api.leaveSpace(session!.accessToken,managedSpace.id)}onSpacesChange(spaces.filter(item=>item.id!==managedSpace.id));setManagedSpace(null);setError("")}catch(reason){setError(reason instanceof Error?reason.message:deleting?"공간을 삭제하지 못했습니다.":"공간에서 탈퇴하지 못했습니다.")}}

  const canManage = managedSpace?.role === "OWNER" || managedSpace?.role === "ADMIN";
  const roleLabel = spaceRoleLabel;
  const invitationStatusLabel = (status: InvitationSummary["status"]) => ({ PENDING: "대기 중", ACCEPTED: "수락됨", DECLINED:"거절됨", REVOKED: "취소됨", EXPIRED: "만료됨" })[status];
  const deliveryStatusLabel=(status:EmailDelivery["status"])=>({PENDING:"발송 대기",PROCESSING:"발송 중",RETRY:"재시도 대기",SENT:"발송 완료",DEAD:"최종 실패",CANCELLED:"발송 취소"})[status];

  return (
    <section className={styles.card}>
      <div className={styles.sectionHeader}><div><h2>내 공간</h2><p>개인 공간과 함께하는 그룹을 관리합니다.</p></div><button className={styles.primaryButton} type="button" onClick={() => setShowCreate(true)}>＋ 새 공간</button></div>
      <div className={styles.spaceGrid}>
        {spaces.map((space) => <article key={space.id}><span className={styles.spaceGlyph}>{space.type === "PERSONAL" ? "나" : space.type === "FAMILY" ? "집" : "친"}</span><div><small>{space.type === "PERSONAL" ? "개인" : space.type === "FAMILY" ? "가족" : "친구"}</small><h3>{space.name}</h3><p>{roleLabel(space.role)} · Asia/Seoul</p></div>{space.type !== "PERSONAL" && <button type="button" onClick={() => openManagement(space)}>관리</button>}</article>)}
      </div>
      {grouped.length === 0 && <p className={styles.empty}>가족이나 친구 공간을 만들어 첫 구성원을 초대해 보세요.</p>}
      {error && <p className={styles.error} role="alert">{error}</p>}

      {showCreate && <ModalPortal><div className={styles.modalBackdrop}><div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="create-title"><button className={styles.closeButton} type="button" aria-label="닫기" onClick={() => setShowCreate(false)}>×</button><p className={styles.eyebrow}>새로운 연결</p><h2 id="create-title">공간 만들기</h2><form className={styles.form} onSubmit={createSpace}><label>공간 종류<select name="type" defaultValue="FAMILY"><option value="FAMILY">가족</option><option value="FRIENDS">친구</option></select></label><label>공간 이름<input required name="name" maxLength={60} placeholder="예: 우리 가족" /></label><button className={styles.primaryButtonLarge}>공간 만들기</button></form></div></div></ModalPortal>}

      {managedSpace && <ModalPortal><div className={styles.modalBackdrop}><div className={`${styles.modal} ${styles.managementModal}`} role="dialog" aria-modal="true" aria-labelledby="manage-title"><button className={styles.closeButton} type="button" aria-label="닫기" onClick={() => setManagedSpace(null)}>×</button><p className={styles.eyebrow}>{managedSpace.name}</p><h2 id="manage-title">구성원과 초대 관리</h2>
        {managementLoading ? <p className={styles.empty}>구성원 정보를 불러오는 중…</p> : <div className={styles.managementSections}>
          <section><div className={styles.managementHeading}><div><h3>구성원</h3><p>{spaceMembers.length}명 참여 중</p></div></div><div className={styles.memberList}>{spaceMembers.map((member) => {
            const editable = canManage && !member.currentUser && member.role !== "OWNER" && !(managedSpace.role === "ADMIN" && member.role === "ADMIN");
            return <div className={styles.memberRow} key={member.userId}><span className={styles.avatar}>{member.displayName.slice(0, 1)}</span><div><strong>{member.displayName}{member.currentUser ? " (나)" : ""}</strong><small>{member.email}</small></div>{editable ? <select aria-label={`${member.displayName} 역할`} value={member.role} onChange={(event) => changeRole(member, event.target.value as SpaceRole)}>{managedSpace.role === "OWNER" && <option value="ADMIN">관리자</option>}<option value="MEMBER">멤버</option><option value="VIEWER">열람자</option></select> : <span className={styles.statusChip}>{roleLabel(member.role)}</span>}{editable && <button className={styles.dangerButton} type="button" onClick={() => removeMember(member)}>추방</button>}</div>;
          })}</div></section>

          {canManage && <section><div className={styles.managementHeading}><div><h3>새 구성원 초대</h3><p>초대 링크는 7일 동안 유효합니다.</p></div></div>{createdInvitation ? <div className={styles.inviteResult}><strong>{createdInvitation.email}</strong><p className={styles.inviteDelivery} data-sent={createdInvitation.emailQueued}>{createdInvitation.emailQueued?"초대 메일을 발송 대기열에 등록했습니다.":"메일을 등록하지 못했습니다. 아래 링크를 직접 전달해 주세요."}</p><code>{`${location.origin}/?invite=${encodeURIComponent(createdInvitation.oneTimeToken)}`}</code><button className={styles.primaryButtonLarge} type="button" onClick={() => navigator.clipboard?.writeText(`${location.origin}/?invite=${encodeURIComponent(createdInvitation.oneTimeToken)}`)}>초대 링크 복사</button><button className={styles.textButton} type="button" onClick={() => setCreatedInvitation(null)}>다른 구성원 초대</button></div> : <form className={`${styles.form} ${styles.inlineInviteForm}`} onSubmit={invite}><label>이메일<input required type="email" name="email" placeholder="member@example.com" /></label><label>역할<select name="role" defaultValue="MEMBER"><option value="MEMBER">멤버</option>{managedSpace.role === "OWNER" && <option value="ADMIN">관리자</option>}<option value="VIEWER">열람자</option></select></label><button className={styles.primaryButtonLarge}>초대 만들기</button></form>}</section>}

          {canManage && <section><div className={styles.managementHeading}><div><h3>초대 이력</h3><p>대기 중인 초대는 새 일회용 링크로 재발송할 수 있습니다.</p></div></div><div className={styles.invitationList}>{spaceInvitations.length === 0 ? <p className={styles.empty}>아직 발급된 초대가 없습니다.</p> : spaceInvitations.map((invitation) => <div key={invitation.id}><div><strong>{invitation.email}</strong><small>{roleLabel(invitation.role)} · {new Date(invitation.expiresAt).toLocaleDateString("ko-KR")}까지</small></div><span className={styles.statusChip} data-status={invitation.status}>{invitationStatusLabel(invitation.status)}</span>{invitation.status === "PENDING" && <span className={styles.invitationActions}><button type="button" onClick={() => void resendInvitation(invitation)}>재발송</button><button className={styles.dangerButton} type="button" onClick={() => revokeInvitation(invitation)}>취소</button></span>}</div>)}</div></section>}
          {canManage&&<section><div className={styles.managementHeading}><div><h3>이메일 발송 이력</h3><p>최근 100건의 대기·성공·재시도·실패 상태를 확인합니다.</p></div></div><div className={styles.deliveryList}>{emailDeliveries.length===0?<p className={styles.empty}>아직 이메일 발송 기록이 없습니다.</p>:emailDeliveries.map(item=><div key={item.id}><div><strong>{item.recipient}</strong><small>{item.subject}</small>{item.lastError&&<em>{item.lastError}</em>}</div><span className={styles.statusChip} data-status={item.status}>{deliveryStatusLabel(item.status)}</span><time>{item.attemptCount}/{item.maxAttempts}회<br/>{new Date(item.sentAt??item.nextAttemptAt??item.createdAt).toLocaleString("ko-KR")}</time></div>)}</div></section>}
          {canManage&&<section><div className={styles.managementHeading}><div><h3>감사 기록</h3><p>최근 중요 작업 100건을 확인합니다.</p></div></div><div className={styles.auditList}>{spaceAudits.length===0?<p className={styles.empty}>아직 기록된 중요 작업이 없습니다.</p>:spaceAudits.map(entry=><div key={entry.id}><i/><span><strong>{auditActionLabel[entry.action]??entry.action}</strong><small>{entry.summary}</small>{entry.reason&&<em>사유: {entry.reason}</em>}</span><time>{entry.actorName}<br/>{new Date(entry.createdAt).toLocaleString("ko-KR")}</time></div>)}</div></section>}
          <section className={styles.spaceLifecycle}><div><h3>{managedSpace.role==="OWNER"?"공간 삭제":"공간 탈퇴"}</h3><p>{managedSpace.role==="OWNER"?"모든 구성원의 접근을 종료하고 공간을 보관 처리합니다.":"내 접근 권한을 종료합니다. 다시 참여하려면 새 초대가 필요합니다."}</p></div><button type="button" className={styles.dangerOutlineButton} onClick={exitSpace}>{managedSpace.role==="OWNER"?"공간 삭제":"공간 탈퇴"}</button></section>
        </div>}
        {error && <p className={styles.error} role="alert">{error}</p>}
      </div></div></ModalPortal>}
    </section>
  );
}
