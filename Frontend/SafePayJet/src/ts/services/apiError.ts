export type ApiErrorKind = "http" | "network" | "csrf" | "validation" | "response";
const messages: Record<number, string> = {
  400: "Please check the information you entered.",
  401: "Please log in to continue.",
  403: "Access denied or the security token has expired.",
  404: "The requested resource was not found.",
  409: "This request conflicts with the current data.",
  500: "The server could not complete your request. Please try again later."
};
export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly kind: ApiErrorKind = "http") {
    super(message); this.name = "ApiError";
  }
}
export function httpError(status: number, body: unknown): ApiError {
  let message = messages[status] || (status >= 500 ? messages[500] : "The request could not be completed.");
  const candidate = body && typeof body === "object" && "message" in body
    ? (body as { message?: unknown }).message : undefined;
  // Never propagate HTML, SQL diagnostics, stack traces or arbitrary server failures to UI.
  if (status < 500 && typeof candidate === "string" && candidate.length <= 300 &&
      !/[<>\r\n]|ORA-\d|SYS\.|SQLException|Exception:|\bat com\./i.test(candidate)) message = candidate || message;
  return new ApiError(status, message);
}
export function requireText(value: string, label: string): void {
  if (typeof value !== "string" || !value.trim()) throw new ApiError(400, `${label} is required.`, "validation");
}
export function resourceId(value: number): number {
  if (!Number.isSafeInteger(value) || value <= 0) throw new ApiError(400, "A valid resource ID is required.", "validation");
  return value;
}
