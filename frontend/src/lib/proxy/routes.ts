import { isUuid } from "../api/bodies";

export type AllowedRoute = {
  method: "GET" | "POST";
  upstreamPath: string;
};

export function matchRoute(method: string, segments: string[]): AllowedRoute | null {
  const [kind, id, action] = segments;
  if (kind !== "resources" && kind !== "reservations") return null;
  const allowed = (method === "POST" && segments.length === 1)
    || (method === "GET" && segments.length === 2 && isUuid(id))
    || (method === "POST" && kind === "reservations" && segments.length === 3 && isUuid(id) && action === "cancel");
  return allowed ? { method: method as "GET" | "POST", upstreamPath: `/api/${segments.join("/")}` } : null;
}
