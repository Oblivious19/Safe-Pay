import { ApiError, httpError } from "./apiError";
import { clearRetryKeys } from "./retryKeys";
import { clearPendingPayment } from "./pendingPayment";
import { clearCreditDraft } from "./adminCreditDraft";

export const DEFAULT_API_BASE_URL = "http://localhost:8080";
type CsrfPath = "/api/accounts/current" | "/api/admin/reports/transactions/summary";
interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  csrf?: boolean;
  idempotencyKey?: string;
}

/** One browser transport. No cookie access, token storage, automatic payment retries or risk logic. */
export class ApiClient {
  private baseUrl = DEFAULT_API_BASE_URL;
  private csrfToken: string | undefined;
  private csrfPath: CsrfPath = "/api/accounts/current";
  private generation = 0;
  private pendingCsrf: Promise<void> | undefined;

  configure(baseUrl: string): void {
    const url = new URL(baseUrl);
    if (!/^https?:$/.test(url.protocol) || url.username || url.password || url.search || url.hash) {
      throw new ApiError(400, "API base URL must be an HTTP(S) URL without credentials, query or fragment.", "validation");
    }
    this.baseUrl = url.href.replace(/\/+$/, "");
    this.clearSession();
  }

  clearSession(): void {
    clearRetryKeys();
    clearCreditDraft();
    clearPendingPayment();
    this.csrfToken = undefined; this.pendingCsrf = undefined; this.generation++;
    this.csrfPath = "/api/accounts/current";
  }

  useAdminCsrf(admin: boolean): void {
    this.csrfPath = admin ? "/api/admin/reports/transactions/summary" : "/api/accounts/current";
  }

  /** Operation retry keys must never carry over to a different signed-in session. */
  sessionRevision(): number { return this.generation; }

  async ensureCsrfToken(): Promise<void> {
    if (!this.csrfToken) await this.refreshCsrfToken();
  }

  async refreshCsrfToken(): Promise<void> {
    if (!this.pendingCsrf) {
      const generation = this.generation;
      this.pendingCsrf = this.request<unknown>(this.csrfPath).then(() => {
        if (generation !== this.generation || !this.csrfToken) {
          throw new ApiError(403, "Security token unavailable. Check session and CORS header exposure.", "csrf");
        }
      }).finally(() => { if (generation === this.generation) this.pendingCsrf = undefined; });
    }
    return this.pendingCsrf;
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    if (!path.startsWith("/api/") || /[\\\r\n]/.test(path) || path.includes("..")) {
      throw new ApiError(400, "Only local API paths are supported.", "validation");
    }
    const generation = this.generation;
    if (options.csrf) await this.ensureCsrfToken();
    if (generation !== this.generation) throw new ApiError(401, "Session changed. Please retry explicitly.");
    const headers = new Headers({ Accept: "application/json" });
    if (options.body !== undefined) headers.set("Content-Type", "application/json");
    if (options.csrf && this.csrfToken) headers.set("X-CSRF-TOKEN", this.csrfToken);
    if (options.idempotencyKey) headers.set("Idempotency-Key", options.idempotencyKey);
    let response: Response;
    let text: string;
    try {
      response = await fetch(this.baseUrl + path, {
        method: options.method || "GET", headers, credentials: "include", cache: "no-store", redirect: "error",
        ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {})
      });
      text = await response.text();
    } catch {
      throw new ApiError(0, "Cannot reach SafePay. Check the backend connection and browser CORS configuration.", "network");
    }
    if (generation === this.generation) {
      const token = response.headers.get("X-CSRF-TOKEN");
      if (token) this.csrfToken = token;
      if (response.status === 401) this.clearSession();
      else if (response.status === 403) this.csrfToken = undefined;
    }
    let body: unknown;
    try { body = text ? JSON.parse(text) : undefined; }
    catch {
      if (!response.ok) throw httpError(response.status, undefined);
      throw new ApiError(response.status, "The server returned an invalid JSON response.", "response");
    }
    if (!response.ok) throw httpError(response.status, body);
    return body as T;
  }
}

export const apiClient = new ApiClient();
/** Call before any service request; default is http://localhost:8080 (without /api). */
export function configureApi(baseUrl: string): void { apiClient.configure(baseUrl); }
