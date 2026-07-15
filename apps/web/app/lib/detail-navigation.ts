export type DetailType = "EVENT" | "POST" | "COUPON";
export type DetailTarget = { type: DetailType; id: string; spaceId?: string };

export const viewForDetail = (type: DetailType) =>
  ({ EVENT: "calendar", POST: "posts", COUPON: "coupons" } as const)[type];

export function targetFromLink(link?: string, spaceId?: string): DetailTarget | null {
  if (!link) return null;
  try {
    const path = new URL(link, "http://moaday.local").pathname;
    const match = path.match(/^\/(events|posts|coupons)\/([^/]+)$/);
    if (!match) return null;
    const type = ({ events: "EVENT", posts: "POST", coupons: "COUPON" } as const)[match[1] as "events" | "posts" | "coupons"];
    return { type, id: decodeURIComponent(match[2]), spaceId };
  } catch {
    return null;
  }
}

export function targetFromUrl(url: URL): DetailTarget | null {
  const value = url.searchParams.get("detail");
  if (!value) return targetFromLink(url.pathname, url.searchParams.get("space") ?? undefined);
  const separator = value.indexOf(":");
  if (separator < 1) return null;
  const type = value.slice(0, separator);
  const id = value.slice(separator + 1);
  if (!(["EVENT", "POST", "COUPON"] as string[]).includes(type) || !id) return null;
  return { type: type as DetailType, id, spaceId: url.searchParams.get("space") ?? undefined };
}

export function writeDetailUrl(target: DetailTarget | null) {
  const url = new URL(window.location.href);
  url.searchParams.delete("view");
  url.searchParams.delete("space");
  url.searchParams.delete("detail");
  if (target) {
    url.searchParams.set("view", viewForDetail(target.type));
    if (target.spaceId) url.searchParams.set("space", target.spaceId);
    url.searchParams.set("detail", `${target.type}:${target.id}`);
  }
  window.history.replaceState({}, "", `${url.pathname}${url.search}${url.hash}`);
}
