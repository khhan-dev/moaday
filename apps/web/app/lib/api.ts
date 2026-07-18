export type SpaceType = "PERSONAL" | "FAMILY" | "FRIENDS";
export type SpaceRole = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";
export type User = { id: string; email: string; displayName: string; timezone: string };
export type AuthResult = { accessToken: string; expiresAt: string; user: User };
export type RegistrationPending = { email: string; message: string };
export type Space = { id: string; type: SpaceType; name: string; timezone: string; color: string; role: SpaceRole };
export type Invitation = { id: string; spaceId: string; email: string; role: SpaceRole; expiresAt: string; oneTimeToken: string; emailQueued: boolean };
export type SpaceMember = { userId: string; displayName: string; email: string; role: SpaceRole; joinedAt: string; currentUser: boolean };
export type InvitationStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "REVOKED" | "EXPIRED";
export type InvitationSummary = { id: string; spaceId: string; email: string; role: SpaceRole; expiresAt: string; status: InvitationStatus; createdAt: string };
export type ReceivedInvitation = { id: string; spaceId: string; spaceName: string; spaceType: SpaceType; role: SpaceRole; invitedByName: string; expiresAt: string; createdAt: string };
export type EmailDeliveryStatus = "PENDING" | "PROCESSING" | "RETRY" | "SENT" | "DEAD" | "CANCELLED";
export type EmailDelivery = { id: string; invitationId?: string; category: string; recipient: string; subject: string; status: EmailDeliveryStatus; attemptCount: number; maxAttempts: number; nextAttemptAt?: string; lastAttemptAt?: string; sentAt?: string; lastError?: string; createdAt: string };
export type Calendar = { id: string; spaceId: string; name: string; color: string };
export type EventRecurrence = "NONE" | "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";
export type AttendanceStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "MAYBE";
export type EventAttendee = { userId: string; displayName: string; email: string; response: AttendanceStatus; currentUser: boolean };
export type CalendarEvent = {
  id: string; calendarId: string; calendarName: string; calendarColor: string; spaceId: string;
  title: string; description?: string; location?: string; externalUrl?: string; allDay: boolean;
  startsAt: string; endsAt: string; timezone: string; recurrence: EventRecurrence; recurrenceUntil?: string;
  createdBy: string; version: number; attendees: EventAttendee[]; reminderMinutes: number[]; canEdit: boolean;
};
export type OccurrenceExceptionAction = "OVERRIDE" | "CANCELLED";
export type EventOccurrence = Omit<CalendarEvent, "id" | "externalUrl" | "createdBy" | "version"> & {
  occurrenceId: string; eventId: string; originalStartsAt: string; exceptionAction?: OccurrenceExceptionAction;
};
export type EventInput = {
  calendarId?: string; title: string; description?: string; location?: string; externalUrl?: string;
  allDay: boolean; startsAt: string; endsAt: string; timezone: string; recurrence: EventRecurrence;
  recurrenceUntil?: string | null; attendeeUserIds: string[]; reminderMinutes: number[];
};
export type EventResourceType = "POST" | "ATTACHMENT" | "COUPON";
export type EventResource = {
  type: EventResourceType; resourceId: string; title: string; subtitle: string;
  status?: CouponStatus; expiresAt?: string; postId?: string; contentType?: string; sizeBytes?: number;
};
export type EventResourceReference = { type: EventResourceType; resourceId: string };
export type IcsImportResult = { imported: number; skipped: number; errors: string[] };
export type PostAttachment = { id: string; originalName: string; contentType: string; sizeBytes: number };
export type PostComment = { id: string; authorId: string; authorName: string; content: string; createdAt: string; updatedAt: string; canEdit: boolean };
export type SharedPost = { id: string; spaceId: string; title: string; content: string; pinned: boolean; authorId: string; authorName: string; tags: string[]; attachments: PostAttachment[]; commentCount: number; createdAt: string; updatedAt: string; canEdit: boolean };
export type PostDetail = { post: SharedPost; comments: PostComment[] };
export type CouponStatus = "AVAILABLE" | "CLAIMED" | "USED" | "EXPIRED";
export type CouponImage = { id: string; originalName: string; contentType: string; sizeBytes: number };
export type Coupon = { id: string; spaceId: string; title: string; brand: string; description?: string; expiresAt: string; barcodeFormat?: string; status: CouponStatus; ownerId: string; claimedBy?: string; claimedByName?: string; claimedAt?: string; claimExpiresAt?: string; usedBy?: string; usedAt?: string; image?: CouponImage; hasBarcode: boolean; barcodeAvailable: boolean; canEdit: boolean; canCorrect: boolean; version: number; createdAt: string; updatedAt: string };
export type Barcode = { couponId: string; value: string; format: string; status: CouponStatus };
export type AuditEntry = { id: string; spaceId: string; actorId?: string; actorName: string; action: string; resourceType: string; resourceId?: string; summary: string; reason?: string; createdAt: string };
export type CouponInput = { title: string; brand: string; description?: string; expiresAt: string; barcodeValue: string; barcodeFormat: string };
export type AppNotification = { id: string; spaceId?: string; type: string; title: string; message: string; link?: string; readAt?: string; createdAt: string };
export type NotificationPreferences = { appNotifications: boolean; emailNotifications: boolean; eventReminders: boolean; postActivity: boolean; couponActivity: boolean };
export type DashboardData = {
  spaceCount: number; unreadNotificationCount: number; upcomingEventCount: number; expiringCouponCount: number;
  upcomingEvents: Array<{ id: string; title: string; location?: string; startsAt: string; allDay: boolean; spaceId: string; spaceName: string; attendance: string }>;
  expiringCoupons: Array<{ id: string; title: string; brand: string; expiresAt: string; status: CouponStatus; spaceId: string; spaceName: string }>;
  recentPosts: Array<{ id: string; title: string; excerpt: string; updatedAt: string; commentCount: number; attachmentCount: number; spaceId: string; spaceName: string }>;
};
export type SearchResult = { type: "EVENT" | "POST" | "COUPON"; id: string; spaceId: string; spaceName: string; title: string; summary: string; occurredAt?: string; targetView: "calendar" | "posts" | "coupons" };

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1";

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? "요청을 처리하지 못했습니다.");
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  register: (input: { email: string; password: string; displayName: string; timezone: string }) => request<RegistrationPending>("/auth/register", { method: "POST", body: JSON.stringify(input) }),
  login: (input: { email: string; password: string }) => request<AuthResult>("/auth/login", { method: "POST", body: JSON.stringify(input) }),
  resendEmailVerification: (email: string) => request<{ message: string }>("/auth/email-verification/resend", { method: "POST", body: JSON.stringify({ email }) }),
  confirmEmailVerification: (token: string) => request<void>("/auth/email-verification/confirm", { method: "POST", body: JSON.stringify({ token }) }),
  requestPasswordReset: (email: string) => request<{ message: string }>("/auth/password-reset/request", { method: "POST", body: JSON.stringify({ email }) }),
  resetPassword: (token: string, newPassword: string) => request<void>("/auth/password-reset/confirm", { method: "POST", body: JSON.stringify({ token, newPassword }) }),
  listSpaces: (token: string) => request<Space[]>("/spaces", {}, token),
  createSpace: (token: string, input: { type: SpaceType; name: string; timezone: string; color: string }) => request<Space>("/spaces", { method: "POST", body: JSON.stringify(input) }, token),
  invite: (token: string, spaceId: string, input: { email: string; role: SpaceRole }) => request<Invitation>(`/spaces/${spaceId}/invitations`, { method: "POST", body: JSON.stringify(input) }, token),
  acceptInvitation: (token: string, invitationToken: string) => request<Space>("/invitations/accept", { method: "POST", body: JSON.stringify({ token: invitationToken }) }, token),
  listReceivedInvitations: (token: string) => request<ReceivedInvitation[]>("/invitations", {}, token),
  acceptReceivedInvitation: (token: string, invitationId: string) => request<Space>(`/invitations/${invitationId}/accept`, { method: "POST" }, token),
  declineReceivedInvitation: (token: string, invitationId: string) => request<InvitationSummary>(`/invitations/${invitationId}/decline`, { method: "POST" }, token),
  listMembers: (token: string, spaceId: string) => request<SpaceMember[]>(`/spaces/${spaceId}/members`, {}, token),
  changeMemberRole: (token: string, spaceId: string, memberUserId: string, role: SpaceRole) => request<SpaceMember>(`/spaces/${spaceId}/members/${memberUserId}`, { method: "PATCH", body: JSON.stringify({ role }) }, token),
  removeMember: (token: string, spaceId: string, memberUserId: string) => request<void>(`/spaces/${spaceId}/members/${memberUserId}`, { method: "DELETE" }, token),
  leaveSpace: (token: string, spaceId: string) => request<void>(`/spaces/${spaceId}/membership`, { method: "DELETE" }, token),
  archiveSpace: (token: string, spaceId: string) => request<void>(`/spaces/${spaceId}`, { method: "DELETE" }, token),
  listInvitations: (token: string, spaceId: string) => request<InvitationSummary[]>(`/spaces/${spaceId}/invitations`, {}, token),
  revokeInvitation: (token: string, spaceId: string, invitationId: string) => request<InvitationSummary>(`/spaces/${spaceId}/invitations/${invitationId}`, { method: "DELETE" }, token),
  resendInvitation: (token: string, spaceId: string, invitationId: string) => request<Invitation>(`/spaces/${spaceId}/invitations/${invitationId}/resend`, { method: "POST" }, token),
  listEmailDeliveries: (token: string, spaceId: string) => request<EmailDelivery[]>(`/spaces/${spaceId}/email-deliveries`, {}, token),
  listCalendars: (token: string, spaceId: string) => request<Calendar[]>(`/spaces/${spaceId}/calendars`, {}, token),
  createCalendar: (token: string, spaceId: string, input: { name: string; color: string }) => request<Calendar>(`/spaces/${spaceId}/calendars`, { method: "POST", body: JSON.stringify(input) }, token),
  updateCalendar: (token: string, calendarId: string, input: { name: string; color: string }) => request<Calendar>(`/calendars/${calendarId}`, { method: "PATCH", body: JSON.stringify(input) }, token),
  deleteCalendar: (token: string, calendarId: string) => request<void>(`/calendars/${calendarId}`, { method: "DELETE" }, token),
  listEvents: (token: string, spaceId: string, from: string, to: string) => request<EventOccurrence[]>(`/spaces/${spaceId}/events?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, {}, token),
  getEvent: (token: string, eventId: string) => request<CalendarEvent>(`/events/${eventId}`, {}, token),
  createEvent: (token: string, calendarId: string, input: EventInput) => request<CalendarEvent>(`/calendars/${calendarId}/events`, { method: "POST", body: JSON.stringify(input) }, token),
  updateEvent: (token: string, eventId: string, input: EventInput) => request<CalendarEvent>(`/events/${eventId}`, { method: "PATCH", body: JSON.stringify(input) }, token),
  deleteEvent: (token: string, eventId: string) => request<void>(`/events/${eventId}`, { method: "DELETE" }, token),
  respondAttendance: (token: string, eventId: string, response: Exclude<AttendanceStatus, "PENDING">) => request<CalendarEvent>(`/events/${eventId}/attendance`, { method: "POST", body: JSON.stringify({ response }) }, token),
  updateOccurrence: (token: string, eventId: string, input: { originalStartsAt: string; title: string; description?: string; location?: string; allDay: boolean; startsAt: string; endsAt: string; timezone: string }) => request<void>(`/events/${eventId}/occurrences`, { method: "PATCH", body: JSON.stringify(input) }, token),
  cancelOccurrence: (token: string, eventId: string, originalStartsAt: string) => request<void>(`/events/${eventId}/occurrences?originalStartsAt=${encodeURIComponent(originalStartsAt)}`, { method: "DELETE" }, token),
  restoreOccurrence: (token: string, eventId: string, originalStartsAt: string) => request<void>(`/events/${eventId}/occurrence-exceptions?originalStartsAt=${encodeURIComponent(originalStartsAt)}`, { method: "DELETE" }, token),
  exportIcs: async (token: string, spaceId: string, filename: string) => {
    const response = await fetch(`${API_URL}/spaces/${spaceId}/calendar.ics`, { headers: { Authorization: `Bearer ${token}` } });
    if (!response.ok) throw new Error("ICS 파일을 내보내지 못했습니다.");
    const url = URL.createObjectURL(await response.blob()); const link = document.createElement("a");
    link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url);
  },
  importIcs: async (token: string, calendarId: string, content: string) => {
    const response = await fetch(`${API_URL}/calendars/${calendarId}/imports/ics`, { method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "text/calendar" }, body: content });
    if (!response.ok) { const error = await response.json().catch(() => ({})) as { message?: string }; throw new Error(error.message ?? "ICS 파일을 가져오지 못했습니다."); }
    return response.json() as Promise<IcsImportResult>;
  },
  listLinkableResources: (token: string, spaceId: string) => request<EventResource[]>(`/spaces/${spaceId}/linkable-resources`, {}, token),
  listEventResources: (token: string, eventId: string) => request<EventResource[]>(`/events/${eventId}/resources`, {}, token),
  replaceEventResources: (token: string, eventId: string, resources: EventResourceReference[]) => request<EventResource[]>(`/events/${eventId}/resources`, { method: "PUT", body: JSON.stringify({ resources }) }, token),
  listPosts: (token: string, spaceId: string, query = "", tag = "", pinned = false) => request<SharedPost[]>(`/spaces/${spaceId}/posts?query=${encodeURIComponent(query)}&tag=${encodeURIComponent(tag)}&pinned=${pinned}`, {}, token),
  getPost: (token: string, postId: string) => request<PostDetail>(`/posts/${postId}`, {}, token),
  createPost: (token: string, spaceId: string, input: { title: string; content: string; tags: string[] }) => request<PostDetail>(`/spaces/${spaceId}/posts`, { method: "POST", body: JSON.stringify(input) }, token),
  updatePost: (token: string, postId: string, input: { title: string; content: string; tags: string[] }) => request<PostDetail>(`/posts/${postId}`, { method: "PATCH", body: JSON.stringify(input) }, token),
  deletePost: (token: string, postId: string) => request<void>(`/posts/${postId}`, { method: "DELETE" }, token),
  pinPost: (token: string, postId: string, pinned: boolean) => request<SharedPost>(`/posts/${postId}/pin`, { method: "POST", body: JSON.stringify({ pinned }) }, token),
  uploadAttachment: async (token: string, postId: string, file: File) => {
    const body = new FormData(); body.append("file", file);
    const response = await fetch(`${API_URL}/posts/${postId}/attachments`, { method: "POST", headers: { Authorization: `Bearer ${token}` }, body });
    if (!response.ok) { const error = await response.json().catch(() => ({})) as { message?: string }; throw new Error(error.message ?? "첨부파일을 올리지 못했습니다."); }
    return response.json() as Promise<PostAttachment>;
  },
  downloadAttachment: async (token: string, attachment: PostAttachment) => {
    const response = await fetch(`${API_URL}/attachments/${attachment.id}/download`, { headers: { Authorization: `Bearer ${token}` } });
    if (!response.ok) throw new Error("첨부파일을 내려받지 못했습니다.");
    const url = URL.createObjectURL(await response.blob()); const link = document.createElement("a"); link.href = url; link.download = attachment.originalName; link.click(); URL.revokeObjectURL(url);
  },
  attachmentBlob: async (token: string, attachmentId: string) => {
    const response = await fetch(`${API_URL}/attachments/${attachmentId}/download`, { headers: { Authorization: `Bearer ${token}` } });
    if (!response.ok) throw new Error("이미지를 불러오지 못했습니다.");
    return response.blob();
  },
  deleteAttachment: (token: string, attachmentId: string) => request<void>(`/attachments/${attachmentId}`, { method: "DELETE" }, token),
  addComment: (token: string, postId: string, content: string) => request<PostComment>(`/posts/${postId}/comments`, { method: "POST", body: JSON.stringify({ content }) }, token),
  updateComment: (token: string, commentId: string, content: string) => request<PostComment>(`/comments/${commentId}`, { method: "PATCH", body: JSON.stringify({ content }) }, token),
  deleteComment: (token: string, commentId: string) => request<void>(`/comments/${commentId}`, { method: "DELETE" }, token),
  listCoupons: (token: string, spaceId: string, status = "", query = "") => request<Coupon[]>(`/spaces/${spaceId}/coupons?status=${encodeURIComponent(status)}&query=${encodeURIComponent(query)}`, {}, token),
  createCoupon: (token: string, spaceId: string, input: CouponInput) => request<Coupon>(`/spaces/${spaceId}/coupons`, { method: "POST", body: JSON.stringify(input) }, token),
  updateCoupon: (token: string, couponId: string, input: CouponInput) => request<Coupon>(`/coupons/${couponId}`, { method: "PATCH", body: JSON.stringify(input) }, token),
  uploadCouponImage: async (token: string, couponId: string, file: File) => {
    const body = new FormData(); body.append("file", file);
    const response = await fetch(`${API_URL}/coupons/${couponId}/image`, { method: "POST", headers: { Authorization: `Bearer ${token}` }, body });
    if (!response.ok) { const error = await response.json().catch(() => ({})) as { message?: string }; throw new Error(error.message ?? "쿠폰 이미지를 올리지 못했습니다."); }
    return response.json() as Promise<CouponImage>;
  },
  couponImageBlob: async (token: string, imageId: string) => {
    const response = await fetch(`${API_URL}/coupon-images/${imageId}/content`, { headers: { Authorization: `Bearer ${token}` } });
    if (!response.ok) throw new Error("쿠폰 이미지를 불러오지 못했습니다.");
    return response.blob();
  },
  deleteCouponImage: (token: string, couponId: string) => request<void>(`/coupons/${couponId}/image`, { method: "DELETE" }, token),
  deleteCoupon: (token: string, couponId: string) => request<void>(`/coupons/${couponId}`, { method: "DELETE" }, token),
  claimCoupon: (token: string, couponId: string) => request<Coupon>(`/coupons/${couponId}/claim`, { method: "POST" }, token),
  releaseCoupon: (token: string, couponId: string) => request<Coupon>(`/coupons/${couponId}/release`, { method: "POST" }, token),
  useCoupon: (token: string, couponId: string) => request<Coupon>(`/coupons/${couponId}/use`, { method: "POST" }, token),
  revealBarcode: (token: string, couponId: string) => request<Barcode>(`/coupons/${couponId}/barcode`, {}, token),
  couponHistory: (token: string, couponId: string) => request<AuditEntry[]>(`/coupons/${couponId}/history`, {}, token),
  correctCoupon: (token: string, couponId: string, status: Exclude<CouponStatus, "CLAIMED">, reason: string) => request<Coupon>(`/coupons/${couponId}/correct`, { method: "POST", body: JSON.stringify({ status, reason }) }, token),
  listAuditLogs: (token: string, spaceId: string) => request<AuditEntry[]>(`/spaces/${spaceId}/audit-logs`, {}, token),
  listNotifications: (token: string) => request<AppNotification[]>("/notifications", {}, token),
  unreadNotifications: (token: string) => request<{ count: number }>("/notifications/unread-count", {}, token),
  readNotification: (token: string, id: string) => request<AppNotification>(`/notifications/${id}/read`, { method: "POST" }, token),
  readAllNotifications: (token: string) => request<void>("/notifications/read-all", { method: "POST" }, token),
  getPreferences: (token: string) => request<NotificationPreferences>("/preferences", {}, token),
  updatePreferences: (token: string, input: NotificationPreferences) => request<NotificationPreferences>("/preferences", { method: "PATCH", body: JSON.stringify(input) }, token),
  updateProfile: (token: string, input: { displayName: string; timezone: string }) => request<User>("/profile", { method: "PATCH", body: JSON.stringify(input) }, token),
  changePassword: (token: string, input: { currentPassword: string; newPassword: string }) => request<AuthResult>("/account/password", { method: "PATCH", body: JSON.stringify(input) }, token),
  deleteAccount: (token: string, password: string) => request<void>("/account", { method: "DELETE", body: JSON.stringify({ password }) }, token),
  dashboard: (token: string) => request<DashboardData>("/dashboard", {}, token),
  search: (token: string, query: string) => request<SearchResult[]>(`/search?query=${encodeURIComponent(query)}`, {}, token),
};
