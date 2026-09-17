import { profileService, CustomerIdentity } from "./profileService";
import { adminReportService } from "./adminReportService";
import { ApiError } from "./apiError";

export type SessionRoute = {kind: "customer"; profile: CustomerIdentity} | {kind: "admin" | "guest" | "unavailable"};
export async function checkSessionRoute(): Promise<SessionRoute> {
  try { return {kind: "customer", profile: await profileService.getCurrent()}; }
  catch (e) {
    if (e instanceof ApiError && e.status === 401) return {kind: "guest"};
    if (!(e instanceof ApiError) || e.status !== 403) return {kind: "unavailable"};
  }
  // A customer endpoint's 403 alone does not prove the caller is an administrator.
  try { await adminReportService.summary(); return {kind: "admin"}; }
  catch (e) { return {kind: e instanceof ApiError && e.status === 401 ? "guest" : "unavailable"}; }
}
