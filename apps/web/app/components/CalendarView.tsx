"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  api, AttendanceStatus, AuthResult, Calendar, CalendarEvent, EventInput, EventOccurrence,
  EventRecurrence, EventResource, EventResourceReference, EventResourceType, Space, SpaceMember,
} from "../lib/api";
import styles from "./CouponWithApp.module.css";
import { ModalPortal } from "./ModalPortal";

type CalendarMode = "month" | "week" | "day";

const recurrenceLabels: Record<EventRecurrence, string> = {
  NONE: "반복 안 함", DAILY: "매일", WEEKLY: "매주", MONTHLY: "매월", YEARLY: "매년",
};
const attendanceLabels: Record<AttendanceStatus, string> = {
  PENDING: "응답 대기", ACCEPTED: "참석", DECLINED: "불참", MAYBE: "미정",
};
const resourceLabels: Record<EventResourceType, string> = { POST: "공유글", ATTACHMENT: "첨부파일", COUPON: "쿠폰" };
const couponStatusLabels = { AVAILABLE: "사용 가능", CLAIMED: "선점", USED: "사용 완료", EXPIRED: "만료" } as const;
function resourceKey(item: Pick<EventResource, "type" | "resourceId">) { return `${item.type}:${item.resourceId}`; }

function startOfDay(value: Date) { const result = new Date(value); result.setHours(0, 0, 0, 0); return result; }
function addDays(value: Date, amount: number) { const result = new Date(value); result.setDate(result.getDate() + amount); return result; }
function startOfWeek(value: Date) { const result = startOfDay(value); return addDays(result, -result.getDay()); }
function startOfMonth(value: Date) { return new Date(value.getFullYear(), value.getMonth(), 1); }
function zonedParts(value: string | Date | number, timezone: string) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: timezone, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23" }).formatToParts(new Date(value));
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return { year: Number(values.year), month: Number(values.month), day: Number(values.day), hour: Number(values.hour), minute: Number(values.minute), second: Number(values.second) };
}
function toZonedLocalInput(value: string | Date, timezone: string) {
  const part = zonedParts(value, timezone);
  return `${part.year}-${String(part.month).padStart(2, "0")}-${String(part.day).padStart(2, "0")}T${String(part.hour).padStart(2, "0")}:${String(part.minute).padStart(2, "0")}`;
}
function zonedLocalToIso(value: string, timezone: string) {
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/);
  if (!match) throw new Error("일정 시각을 확인해 주세요.");
  const target = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5]));
  let instant = target;
  for (let index = 0; index < 3; index++) {
    const part = zonedParts(instant, timezone);
    const represented = Date.UTC(part.year, part.month - 1, part.day, part.hour, part.minute, part.second);
    instant += target - represented;
  }
  return new Date(instant).toISOString();
}
function dateKeyInZone(value: string, timezone: string) {
  const part = zonedParts(value, timezone);
  return `${part.year}-${String(part.month).padStart(2, "0")}-${String(part.day).padStart(2, "0")}`;
}
function dateKey(value: string | Date) {
  const date = typeof value === "string" ? new Date(value) : value;
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
function colorValue(color: string) {
  return ({ sky: "#63a6d8", orange: "#e89a3d", green: "#2d8a61", leaf: "#2d8a61" } as Record<string, string>)[color] ?? color;
}
function rangeFor(mode: CalendarMode, cursor: Date) {
  if (mode === "month") {
    const from = startOfWeek(startOfMonth(cursor));
    return { from, to: addDays(from, 42), days: Array.from({ length: 42 }, (_, index) => addDays(from, index)) };
  }
  if (mode === "week") {
    const from = startOfWeek(cursor);
    return { from, to: addDays(from, 7), days: Array.from({ length: 7 }, (_, index) => addDays(from, index)) };
  }
  const from = startOfDay(cursor);
  return { from, to: addDays(from, 1), days: [from] };
}
function newEventTimes(day: Date) {
  const start = new Date(day); start.setHours(10, 0, 0, 0);
  const end = new Date(start); end.setHours(11, 0, 0, 0);
  return { start, end };
}

export function CalendarView({ spaces, session, demo }: {
  spaces: Space[]; session: AuthResult | null; demo: boolean;
}) {
  const [spaceId, setSpaceId] = useState(spaces[0]?.id ?? "");
  const [calendars, setCalendars] = useState<Calendar[]>([]);
  const [members, setMembers] = useState<SpaceMember[]>([]);
  const [events, setEvents] = useState<EventOccurrence[]>([]);
  const [mode, setMode] = useState<CalendarMode>("month");
  const [cursor, setCursor] = useState(() => new Date());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [editorOpen, setEditorOpen] = useState(false);
  const [calendarCreatorOpen, setCalendarCreatorOpen] = useState(false);
  const [editing, setEditing] = useState<CalendarEvent | null>(null);
  const [editingOccurrence, setEditingOccurrence] = useState<EventOccurrence | null>(null);
  const [resourceOptions, setResourceOptions] = useState<EventResource[]>([]);
  const [linkedResources, setLinkedResources] = useState<EventResource[]>([]);
  const [draftDay, setDraftDay] = useState(() => new Date());
  const [reloadKey, setReloadKey] = useState(0);
  const [notice, setNotice] = useState("");
  const range = useMemo(() => rangeFor(mode, cursor), [mode, cursor]);
  const selectedSpace = spaces.find((space) => space.id === spaceId) ?? spaces[0];
  const selectedSpaceId = selectedSpace?.id;
  const selectedSpaceColor = selectedSpace?.color;
  const selectedSpaceType = selectedSpace?.type;
  const token = session?.accessToken;
  const rangeFrom = selectedSpace ? zonedLocalToIso(`${dateKey(range.from)}T00:00`, selectedSpace.timezone) : range.from.toISOString();
  const rangeTo = selectedSpace ? zonedLocalToIso(`${dateKey(range.to)}T00:00`, selectedSpace.timezone) : range.to.toISOString();

  useEffect(() => {
    let active = true;
    async function load() {
      await Promise.resolve();
      if (!active || !selectedSpaceId) return;
      setLoading(true); setError("");
      try {
        if (demo) {
          const demoCalendar = { id: `calendar-${selectedSpaceId}`, spaceId: selectedSpaceId, name: "기본 캘린더", color: selectedSpaceColor ?? "green" };
          const first = newEventTimes(new Date());
          if (!active) return;
          setCalendars([demoCalendar]);
          setMembers([
            { userId: "demo-owner", displayName: "김모아", email: "owner@moaday.test", role: "OWNER", joinedAt: new Date().toISOString(), currentUser: true },
            ...(selectedSpaceType === "PERSONAL" ? [] : [{ userId: "demo-member", displayName: "이하루", email: "member@moaday.test", role: "MEMBER" as const, joinedAt: new Date().toISOString(), currentUser: false }]),
          ]);
          setResourceOptions([
            { type: "POST", resourceId: "demo-post", title: "저녁 메뉴 후보", subtitle: "공유글" },
            { type: "ATTACHMENT", resourceId: "demo-file", title: "장보기 목록.pdf", subtitle: "저녁 메뉴 후보", contentType: "application/pdf", sizeBytes: 245760 },
            { type: "COUPON", resourceId: "demo-coupon", title: "패밀리 레스토랑 1만원권", subtitle: "Moa Dining", status: "AVAILABLE", expiresAt: addDays(new Date(), 14).toISOString() },
          ]);
          setEvents((current) => current.length > 0 ? current : [{ occurrenceId: "demo-event", eventId: "demo-event", calendarId: demoCalendar.id, calendarName: demoCalendar.name,
            calendarColor: demoCalendar.color, spaceId: selectedSpaceId, title: "함께 저녁 먹기", description: "이번 주 메뉴 정하기",
            location: "우리 동네", allDay: false, startsAt: first.start.toISOString(), endsAt: first.end.toISOString(), recurrence: "NONE",
            timezone: "Asia/Seoul", originalStartsAt: first.start.toISOString(), attendees: [], reminderMinutes: [30], canEdit: true }]);
        } else if (token) {
          const [loadedCalendars, loadedMembers, loadedEvents, loadedResources] = await Promise.all([
            api.listCalendars(token, selectedSpaceId), api.listMembers(token, selectedSpaceId),
            api.listEvents(token, selectedSpaceId, rangeFrom, rangeTo),
            api.listLinkableResources(token, selectedSpaceId),
          ]);
          if (!active) return;
          setCalendars(loadedCalendars); setMembers(loadedMembers); setEvents(loadedEvents); setResourceOptions(loadedResources);
        }
      } catch (reason) { if (active) setError(reason instanceof Error ? reason.message : "캘린더를 불러오지 못했습니다."); }
      finally { if (active) setLoading(false); }
    }
    void load();
    return () => { active = false; };
  }, [demo, rangeFrom, rangeTo, reloadKey, selectedSpaceColor, selectedSpaceId, selectedSpaceType, token]);

  function move(direction: number) {
    const next = new Date(cursor);
    if (mode === "month") next.setMonth(next.getMonth() + direction);
    else next.setDate(next.getDate() + direction * (mode === "week" ? 7 : 1));
    setCursor(next);
  }

  function openNew(day: Date) { setEditing(null); setEditingOccurrence(null); setLinkedResources([]); setDraftDay(day); setEditorOpen(true); setError(""); }

  async function openExisting(occurrence: EventOccurrence) {
    try {
      if (demo) {
        setEditing({ ...occurrence, id: occurrence.eventId, externalUrl: undefined, timezone: "Asia/Seoul", createdBy: "demo-owner", version: 0 });
        setLinkedResources(resourceOptions.slice(0, 2));
      } else {
        const [loadedEvent, loadedResources] = await Promise.all([
          api.getEvent(session!.accessToken, occurrence.eventId),
          api.listEventResources(session!.accessToken, occurrence.eventId),
        ]);
        setEditing(loadedEvent); setLinkedResources(loadedResources);
      }
      setEditingOccurrence(occurrence);
      setEditorOpen(true); setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "일정을 열지 못했습니다."); }
  }

  async function saveEvent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const recurrence = String(form.get("recurrence")) as EventRecurrence;
    const recurrenceDate = String(form.get("recurrenceUntil"));
    const timezone = String(form.get("timezone") || selectedSpace?.timezone || "Asia/Seoul");
    const startsAt = zonedLocalToIso(String(form.get("startsAt")), timezone);
    const endsAt = zonedLocalToIso(String(form.get("endsAt")), timezone);
    const input: EventInput = {
      calendarId: String(form.get("calendarId")), title: String(form.get("title")), description: String(form.get("description")),
      location: String(form.get("location")), allDay: form.get("allDay") === "on",
      startsAt, endsAt, timezone, recurrence,
      recurrenceUntil: recurrence !== "NONE" && recurrenceDate ? zonedLocalToIso(`${recurrenceDate}T23:59`, timezone) : null,
      attendeeUserIds: form.getAll("attendeeUserIds").map(String),
      reminderMinutes: form.get("reminderMinutes") === "" ? [] : [Number(form.get("reminderMinutes"))],
    };
    const resources = form.getAll("resourceKeys").map(String).map((value): EventResourceReference => {
      const [type, resourceId] = value.split(":");
      return { type: type as EventResourceType, resourceId };
    });
    try {
      if (!demo && editing && editingOccurrence?.recurrence !== "NONE" && form.get("editScope") === "OCCURRENCE") {
        await api.updateOccurrence(session!.accessToken, editing.id, { originalStartsAt: editingOccurrence.originalStartsAt,
          title: input.title, description: input.description, location: input.location, allDay: input.allDay,
          startsAt: input.startsAt, endsAt: input.endsAt, timezone: input.timezone });
        setEditorOpen(false); setEditing(null); setEditingOccurrence(null); setReloadKey((value) => value + 1); return;
      }
      if (demo) {
        const eventId = editing?.id ?? crypto.randomUUID();
        const calendar = calendars.find((item) => item.id === input.calendarId)!;
        const occurrence: EventOccurrence = { occurrenceId: eventId, eventId, calendarId: calendar.id, calendarName: calendar.name,
          calendarColor: calendar.color, spaceId, title: input.title, description: input.description, location: input.location,
          allDay: input.allDay, startsAt: input.startsAt, endsAt: input.endsAt, recurrence: input.recurrence,
          recurrenceUntil: input.recurrenceUntil ?? undefined, attendees: [], reminderMinutes: input.reminderMinutes, canEdit: true };
        setEvents((current) => [...current.filter((item) => item.eventId !== eventId), occurrence]);
        setLinkedResources(resourceOptions.filter((item) => resources.some((reference) => resourceKey(reference) === resourceKey(item))));
      } else {
        const saved = editing ? await api.updateEvent(session!.accessToken, editing.id, input)
          : await api.createEvent(session!.accessToken, input.calendarId!, input);
        if (!editing) setEditing(saved);
        await api.replaceEventResources(session!.accessToken, saved.id, resources);
      }
      setEditorOpen(false); setEditing(null); setEditingOccurrence(null); if (!demo) setReloadKey((value) => value + 1);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "일정을 저장하지 못했습니다."); }
  }

  async function removeEvent() {
    if (!editing || !window.confirm(editing.recurrence === "NONE" ? "이 일정을 삭제할까요?" : "반복 일정 전체를 삭제할까요?")) return;
    try {
      if (!demo) await api.deleteEvent(session!.accessToken, editing.id);
      setEvents((current) => current.filter((item) => item.eventId !== editing.id));
      setEditorOpen(false); setEditing(null);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "일정을 삭제하지 못했습니다."); }
  }

  async function cancelSingleOccurrence() {
    if (!editing || !editingOccurrence || !window.confirm("이 회차만 취소할까요? 다른 반복 회차는 유지됩니다.")) return;
    try {
      if (!demo) await api.cancelOccurrence(session!.accessToken, editing.id, editingOccurrence.originalStartsAt);
      setEditorOpen(false); setEditing(null); setEditingOccurrence(null); setReloadKey((value) => value + 1);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "이 회차를 취소하지 못했습니다."); }
  }

  async function restoreSingleOccurrence() {
    if (!editing || !editingOccurrence) return;
    try {
      if (!demo) await api.restoreOccurrence(session!.accessToken, editing.id, editingOccurrence.originalStartsAt);
      setEditorOpen(false); setEditing(null); setEditingOccurrence(null); setReloadKey((value) => value + 1);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "회차 변경을 되돌리지 못했습니다."); }
  }

  async function exportIcs() {
    try {
      if (!demo && token) await api.exportIcs(token, selectedSpace.id, `${selectedSpace.name}-MoaDay.ics`);
      setNotice("ICS 파일을 내보냈습니다.");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "ICS 파일을 내보내지 못했습니다."); }
  }

  async function importIcs(file: File | undefined) {
    if (!file || calendars.length === 0) return;
    try {
      const result = demo ? { imported: 1, skipped: 0, errors: [] } : await api.importIcs(session!.accessToken, calendars[0].id, await file.text());
      setNotice(`${result.imported}개 일정을 가져왔습니다${result.skipped ? ` · ${result.skipped}개 건너뜀` : ""}.`);
      setReloadKey((value) => value + 1);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "ICS 파일을 가져오지 못했습니다."); }
  }

  async function respond(response: Exclude<AttendanceStatus, "PENDING">) {
    if (!editing) return;
    try {
      if (!demo) setEditing(await api.respondAttendance(session!.accessToken, editing.id, response));
      else setEditing({ ...editing, attendees: editing.attendees.map((item) => item.currentUser ? { ...item, response } : item) });
      if (!demo) setReloadKey((value) => value + 1);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "참석 응답을 저장하지 못했습니다."); }
  }

  async function downloadResource(item: EventResource) {
    if (demo || !token || item.type !== "ATTACHMENT") return;
    try {
      await api.downloadAttachment(token, { id: item.resourceId, originalName: item.title,
        contentType: item.contentType ?? "application/octet-stream", sizeBytes: item.sizeBytes ?? 0 });
    } catch (reason) { setError(reason instanceof Error ? reason.message : "첨부파일을 내려받지 못했습니다."); }
  }

  async function addCalendar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSpace || (selectedSpace.role !== "OWNER" && selectedSpace.role !== "ADMIN")) return;
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name")).trim();
    const color = String(form.get("color") || "green");
    if (!name) return;
    try {
      const created = demo ? { id: crypto.randomUUID(), spaceId: selectedSpace.id, name, color }
        : await api.createCalendar(session!.accessToken, selectedSpace.id, { name, color });
      setCalendars((current) => [...current, created]);
      setCalendarCreatorOpen(false);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "캘린더를 만들지 못했습니다."); }
  }

  const groupedEvents = useMemo(() => {
    const map = new Map<string, EventOccurrence[]>();
    range.days.forEach((day) => map.set(dateKey(day), []));
    events.forEach((item) => { const key = dateKeyInZone(item.startsAt, item.timezone || selectedSpace.timezone); if (map.has(key)) map.get(key)!.push(item); });
    return map;
  }, [events, range.days, selectedSpace.timezone]);
  const title = mode === "month" ? cursor.toLocaleDateString("ko-KR", { year: "numeric", month: "long" })
    : mode === "week" ? `${range.from.toLocaleDateString("ko-KR", { month: "long", day: "numeric" })} – ${addDays(range.to, -1).toLocaleDateString("ko-KR", { month: "long", day: "numeric" })}`
      : cursor.toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric", weekday: "long" });

  if (!selectedSpace) return <section className={styles.card}><p className={styles.empty}>먼저 공간을 만들어 주세요.</p></section>;
  return (
    <section className={`${styles.card} ${styles.calendarCard}`}>
      <div className={styles.calendarToolbar}>
        <div className={styles.calendarSelectors}>
          <select aria-label="일정을 볼 공간" value={selectedSpace.id} onChange={(event) => { setSpaceId(event.target.value); setEvents([]); }}>
            {spaces.map((space) => <option key={space.id} value={space.id}>{space.name}</option>)}
          </select>
          <button className={styles.todayButton} type="button" onClick={() => setCursor(new Date())}>오늘</button>
          <button className={styles.arrowButton} type="button" aria-label="이전" onClick={() => move(-1)}>‹</button>
          <button className={styles.arrowButton} type="button" aria-label="다음" onClick={() => move(1)}>›</button>
          <h2>{title}</h2>
        </div>
        <div className={styles.calendarActions}>
          <button className={styles.secondaryButton} type="button" onClick={exportIcs}>ICS 내보내기</button>
          {selectedSpace.role !== "VIEWER" && <label className={styles.secondaryButton}>ICS 가져오기<input className={styles.visuallyHidden} type="file" accept=".ics,text/calendar" onChange={(event) => { void importIcs(event.target.files?.[0]); event.target.value = ""; }} /></label>}
          {(selectedSpace.role === "OWNER" || selectedSpace.role === "ADMIN") && <button className={styles.secondaryButton} type="button" onClick={() => setCalendarCreatorOpen(true)}>＋ 캘린더</button>}
          {selectedSpace.role !== "VIEWER" && <button className={styles.primaryButton} type="button" onClick={() => openNew(cursor)}>＋ 일정</button>}
          <div className={styles.viewTabs} role="tablist" aria-label="캘린더 보기">
            {(["month", "week", "day"] as const).map((item) => <button key={item} type="button" role="tab" aria-selected={mode === item} onClick={() => setMode(item)}>{item === "month" ? "월" : item === "week" ? "주" : "일"}</button>)}
          </div>
        </div>
      </div>

      <div className={styles.calendarLegend}>{calendars.map((calendar) => <span key={calendar.id}><i style={{ background: colorValue(calendar.color) }} />{calendar.name}</span>)}</div>
      {loading && <p className={styles.calendarNotice}>일정을 불러오는 중…</p>}
      {notice && <p className={styles.success}>{notice}</p>}
      {error && <p className={styles.error} role="alert">{error}</p>}

      {mode === "month" ? <div className={styles.monthCalendar}>
        {["일", "월", "화", "수", "목", "금", "토"].map((day) => <div className={styles.weekday} key={day}>{day}</div>)}
        {range.days.map((day) => <div key={day.toISOString()} className={styles.monthDay} data-outside={day.getMonth() !== cursor.getMonth()} data-today={dateKey(day) === dateKey(new Date())} onDoubleClick={() => openNew(day)}>
          <button type="button" className={styles.dayNumber} onClick={() => { setCursor(day); setMode("day"); }}>{day.getDate()}</button>
          <EventList items={groupedEvents.get(dateKey(day)) ?? []} onOpen={openExisting} compact />
        </div>)}
      </div> : <div className={mode === "week" ? styles.weekCalendar : styles.dayCalendar}>
        {range.days.map((day) => <section key={day.toISOString()} className={styles.agendaDay}>
          <button type="button" className={styles.agendaHeading} onClick={() => { setCursor(day); setMode("day"); }}><strong>{day.toLocaleDateString("ko-KR", { weekday: "short" })}</strong><span>{day.getDate()}</span></button>
          <EventList items={groupedEvents.get(dateKey(day)) ?? []} onOpen={openExisting} />
          {(groupedEvents.get(dateKey(day))?.length ?? 0) === 0 && <button className={styles.emptyDay} type="button" onClick={() => openNew(day)}>＋ 일정 추가</button>}
        </section>)}
      </div>}

      {editorOpen && <EventEditor event={editing} occurrence={editingOccurrence} day={draftDay} calendars={calendars} members={members} canCreate={selectedSpace.role !== "VIEWER"} defaultTimezone={selectedSpace.timezone}
        resources={resourceOptions} linkedResources={linkedResources}
        onClose={() => { setEditorOpen(false); setEditing(null); setEditingOccurrence(null); setLinkedResources([]); }} onSubmit={saveEvent} onDelete={removeEvent} onCancelOccurrence={cancelSingleOccurrence} onRestoreOccurrence={restoreSingleOccurrence} onRespond={respond} onDownload={downloadResource} />}
      {calendarCreatorOpen && <CalendarCreator onClose={() => setCalendarCreatorOpen(false)} onSubmit={addCalendar} />}
    </section>
  );
}

function CalendarCreator({ onClose, onSubmit }: { onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return <ModalPortal><div className={styles.modalBackdrop}><div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="calendar-creator-title">
    <button className={styles.closeButton} type="button" aria-label="닫기" onClick={onClose}>×</button>
    <p className={styles.eyebrow}>공간 캘린더</p>
    <h2 id="calendar-creator-title">새 캘린더</h2>
    <form className={styles.form} onSubmit={onSubmit}>
      <label>이름<input required autoFocus name="name" maxLength={60} placeholder="예: 가족 일정" /></label>
      <label>색상<select name="color" defaultValue="green"><option value="green">초록</option><option value="orange">주황</option><option value="sky">하늘</option></select></label>
      <button className={styles.primaryButtonLarge}>캘린더 만들기</button>
    </form>
  </div></div></ModalPortal>;
}

function EventList({ items, onOpen, compact = false }: { items: EventOccurrence[]; onOpen: (event: EventOccurrence) => void; compact?: boolean }) {
  return <div className={styles.calendarEvents}>{items.map((event) => <button key={event.occurrenceId} type="button" className={styles.calendarEvent} onClick={() => onOpen(event)} style={{ borderLeftColor: colorValue(event.calendarColor) }} title={event.title}>
    {!compact && <time>{event.allDay ? "종일" : new Intl.DateTimeFormat("ko-KR", { timeZone: event.timezone, hour: "2-digit", minute: "2-digit" }).format(new Date(event.startsAt))}</time>}
    <span>{event.title}</span>{event.exceptionAction === "OVERRIDE" && <b aria-label="변경된 회차">✎</b>}{event.recurrence !== "NONE" && <b aria-label="반복 일정">↻</b>}
  </button>)}</div>;
}

function EventEditor({ event, occurrence, day, calendars, members, canCreate, defaultTimezone, resources, linkedResources, onClose, onSubmit, onDelete, onCancelOccurrence, onRestoreOccurrence, onRespond, onDownload }: {
  event: CalendarEvent | null; occurrence: EventOccurrence | null; day: Date; calendars: Calendar[]; members: SpaceMember[]; canCreate: boolean; defaultTimezone: string;
  resources: EventResource[]; linkedResources: EventResource[];
  onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; onDelete: () => void;
  onCancelOccurrence: () => void; onRestoreOccurrence: () => void;
  onRespond: (response: Exclude<AttendanceStatus, "PENDING">) => void;
  onDownload: (item: EventResource) => void;
}) {
  const timezone = occurrence?.timezone ?? event?.timezone ?? defaultTimezone;
  const displayTitle = occurrence?.title ?? event?.title;
  const displayDescription = occurrence?.description ?? event?.description;
  const displayLocation = occurrence?.location ?? event?.location;
  const displayStartsAt = occurrence?.startsAt ?? event?.startsAt;
  const displayEndsAt = occurrence?.endsAt ?? event?.endsAt;
  const displayAllDay = occurrence?.allDay ?? event?.allDay;
  const currentResponse = event?.attendees.find((attendee) => attendee.currentUser)?.response;
  const recurrenceUntil = event?.recurrenceUntil ? dateKeyInZone(event.recurrenceUntil, event.timezone) : "";
  const canEdit = event ? event.canEdit : canCreate;
  const [scope, setScope] = useState<"OCCURRENCE" | "SERIES">(event?.recurrence !== "NONE" && occurrence ? "OCCURRENCE" : "SERIES");
  const occurrenceOnly = scope === "OCCURRENCE" && event?.recurrence !== "NONE" && Boolean(occurrence);
  return <ModalPortal><div className={styles.modalBackdrop}><div className={`${styles.modal} ${styles.eventModal}`} role="dialog" aria-modal="true" aria-labelledby="event-title">
    <button className={styles.closeButton} type="button" aria-label="닫기" onClick={onClose}>×</button>
    <p className={styles.eyebrow}>{event ? (event.recurrence === "NONE" ? "일정 상세" : "반복 일정 상세") : "새 일정"}</p>
    <h2 id="event-title">{event ? displayTitle : "일정 추가"}</h2>
    {currentResponse && <div className={styles.attendancePanel}><span>내 응답: <strong>{attendanceLabels[currentResponse]}</strong></span><div>{(["ACCEPTED", "MAYBE", "DECLINED"] as const).map((status) => <button type="button" key={status} aria-pressed={currentResponse === status} onClick={() => onRespond(status)}>{attendanceLabels[status]}</button>)}</div></div>}
    <form className={`${styles.form} ${styles.eventForm}`} onSubmit={onSubmit}>
      {event?.recurrence !== "NONE" && occurrence && <fieldset className={styles.scopeField}><legend>변경 범위</legend><label><input type="radio" name="editScope" value="OCCURRENCE" checked={scope === "OCCURRENCE"} onChange={() => setScope("OCCURRENCE")} /> 이 회차만</label><label><input type="radio" name="editScope" value="SERIES" checked={scope === "SERIES"} onChange={() => setScope("SERIES")} /> 전체 반복 일정</label>{occurrence.exceptionAction === "OVERRIDE" && <button type="button" onClick={onRestoreOccurrence}>이 회차 변경 되돌리기</button>}</fieldset>}
      <label>캘린더<select name="calendarId" defaultValue={event?.calendarId ?? calendars[0]?.id} disabled={!canEdit || occurrenceOnly}>{calendars.map((calendar) => <option key={calendar.id} value={calendar.id}>{calendar.name}</option>)}</select></label>
      <label>제목<input required name="title" maxLength={120} defaultValue={displayTitle ?? ""} disabled={!canEdit} placeholder="함께 기억할 일정" /></label>
      <div className={styles.formColumns}><label>시작<input required type="datetime-local" name="startsAt" defaultValue={displayStartsAt ? toZonedLocalInput(displayStartsAt, timezone) : `${dateKey(day)}T10:00`} disabled={!canEdit} /></label><label>종료<input required type="datetime-local" name="endsAt" defaultValue={displayEndsAt ? toZonedLocalInput(displayEndsAt, timezone) : `${dateKey(day)}T11:00`} disabled={!canEdit} /></label></div>
      <label>시간대<select name="timezone" defaultValue={timezone} disabled={!canEdit}>{Array.from(new Set([timezone, defaultTimezone, "Asia/Seoul", "Asia/Tokyo", "UTC", "America/New_York", "Europe/London"])).map((item) => <option key={item} value={item}>{item}</option>)}</select><small className={styles.fieldHint}>입력한 시작·종료 시각을 이 시간대 기준으로 저장합니다.</small></label>
      <label className={styles.checkboxLabel}><input type="checkbox" name="allDay" defaultChecked={displayAllDay} disabled={!canEdit} /> 종일 일정</label>
      <label>장소<input name="location" maxLength={200} defaultValue={displayLocation ?? ""} disabled={!canEdit} placeholder="선택 사항" /></label>
      <label>메모<textarea name="description" maxLength={4000} defaultValue={displayDescription ?? ""} disabled={!canEdit} placeholder="준비물이나 공유할 내용을 적어 주세요." /></label>
      <div className={styles.formColumns}><label>반복<select name="recurrence" defaultValue={event?.recurrence ?? "NONE"} disabled={!canEdit || occurrenceOnly}>{Object.entries(recurrenceLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>반복 종료일<input type="date" name="recurrenceUntil" defaultValue={recurrenceUntil} disabled={!canEdit || occurrenceOnly} /></label></div>
      <label>알림<select name="reminderMinutes" defaultValue={event?.reminderMinutes[0] ?? 30} disabled={!canEdit || occurrenceOnly}><option value="">알림 없음</option><option value="0">시작할 때</option><option value="10">10분 전</option><option value="30">30분 전</option><option value="60">1시간 전</option><option value="1440">1일 전</option></select></label>
      <fieldset className={styles.attendeeField} disabled={!canEdit || occurrenceOnly}><legend>참석자</legend>{members.map((member) => <label key={member.userId}><input type="checkbox" name="attendeeUserIds" value={member.userId} defaultChecked={event?.attendees.some((attendee) => attendee.userId === member.userId) ?? member.currentUser} /> <span>{member.displayName}<small>{member.email}</small></span>{event?.attendees.find((attendee) => attendee.userId === member.userId) && <em>{attendanceLabels[event.attendees.find((attendee) => attendee.userId === member.userId)!.response]}</em>}</label>)}</fieldset>
      <fieldset className={styles.resourceField} disabled={!canEdit || occurrenceOnly}>
        <legend>연결 자료 <small>공유글·첨부파일·쿠폰</small></legend>
        {resources.length === 0 && <p>이 공간에 연결할 자료가 아직 없습니다.</p>}
        {resources.map((item) => <label key={resourceKey(item)}>
          <input type="checkbox" name="resourceKeys" value={resourceKey(item)} defaultChecked={linkedResources.some((linked) => resourceKey(linked) === resourceKey(item))} />
          <span><b>{resourceLabels[item.type]}</b><strong>{item.title}</strong><small>{resourceDescription(item)}</small></span>
        </label>)}
      </fieldset>
      {event && linkedResources.length > 0 && <section className={styles.linkedResourcePanel} aria-label="일정 연결 자료">
        <h3>함께 볼 자료</h3>
        <div>{linkedResources.map((item) => <article key={resourceKey(item)}>
          <span>{resourceLabels[item.type]}</span><strong>{item.title}</strong><small>{resourceDescription(item)}</small>
          {item.type === "ATTACHMENT" && <button type="button" onClick={() => onDownload(item)}>파일 받기</button>}
        </article>)}</div>
      </section>}
      {canEdit && <div className={styles.editorActions}>{event && occurrence?.recurrence !== "NONE" && <button className={styles.dangerOutlineButton} type="button" onClick={onCancelOccurrence}>이 회차 취소</button>}{event && <button className={styles.dangerOutlineButton} type="button" onClick={onDelete}>{occurrence?.recurrence !== "NONE" ? "전체 삭제" : "삭제"}</button>}<button className={styles.primaryButtonLarge}>{event ? "변경 저장" : "일정 만들기"}</button></div>}
    </form>
  </div></div></ModalPortal>;
}

function resourceDescription(item: EventResource) {
  if (item.type === "COUPON") {
    const status = item.status ? couponStatusLabels[item.status] : "";
    const expiry = item.expiresAt ? ` · ${new Date(item.expiresAt).toLocaleDateString("ko-KR")}까지` : "";
    return `${item.subtitle} · ${status}${expiry}`;
  }
  if (item.type === "ATTACHMENT") {
    const size = item.sizeBytes == null ? "" : ` · ${formatBytes(item.sizeBytes)}`;
    return `${item.subtitle}${size}`;
  }
  return item.subtitle;
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}
