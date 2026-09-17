import * as ko from "knockout";
import { adminHoldService } from "../services/adminHoldService";
import { adminReportService, AdminBookRow, AdminUserSnapshot } from "../services/adminReportService";
import { ApiError } from "../services/apiError";
import { authService } from "../services/authService";
import { AdminUsersModel } from "./adminUsers";
import { AdminHoldsModel } from "./adminHolds";
import { DailyTransactionSummary, HeldPayment, TransactionSummary } from "../services/types";
import { statusLabel, tierRisk } from "../utils/protection";

const isoDate = (date: Date): string => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
const daysAgo = (n: number): string => {
  const date = new Date();
  date.setDate(date.getDate() - n);
  return isoDate(date);
};

class AdminViewModel {
  usersPage = ko.observable<AdminUsersModel | null>(null);
  holdsPage = ko.observable<AdminHoldsModel | null>(null);
  summary = ko.observable<TransactionSummary | null>(null);
  daily = ko.observableArray<DailyTransactionSummary>([]);
  holds = ko.observableArray<HeldPayment>([]);
  ledger = ko.observableArray<AdminBookRow>([]);
  people = ko.observableArray<AdminUserSnapshot>([]);
  directoryError=ko.observable("");holdsError=ko.observable("");
  loading = ko.observable(false); signingOut = ko.observable(false);
  forbidden = ko.observable(false); error = ko.observable("");
  from = ko.observable(daysAgo(13));
  to = ko.observable(isoDate(new Date()));
  loadedFrom = ko.observable(""); loadedTo = ko.observable(""); updated = ko.observable("");
  private generation = 0;
  constructor(private context: any = {}) {}
  money = (value: number): string => new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(value);
  count = (value: number): string => new Intl.NumberFormat("en-IN").format(value);
  date = (value: string): string => new Intl.DateTimeFormat("en-IN", {
    day: "numeric", month: "short", year: "numeric"
  }).format(new Date(value + "T00:00:00"));
  shortDate = (value: string): string => new Intl.DateTimeFormat("en-IN", {
    day: "numeric", month: "short"
  }).format(new Date(value + "T00:00:00"));
  pendingHolds = ko.pureComputed(() => this.holds().filter((row) => row.decision === "PENDING"));
  decidedHolds = ko.pureComputed(() => this.holds().filter((row) => row.decision !== "PENDING"));
  heldAmount = ko.pureComputed(() => this.pendingHolds().reduce((total, row) => total + (Number(row.amount) || 0), 0));
  settleRate = ko.pureComputed(() => this.share(this.summary()?.settledTransactions));
  holdRate = ko.pureComputed(() => this.share(this.summary()?.hardHolds));
  protectRate = ko.pureComputed(() => this.share(this.summary()?.protectedTransactions));
  highRiskRate = ko.pureComputed(() => this.share(this.summary()?.highRiskTransactions));
  outcomeBars = ko.pureComputed(() => {
    const row = this.summary();
    if (!row) return [];
    const items = [
      { key: "settled", label: "Settled", value: row.settledTransactions, color: "#12675f" },
      { key: "protected", label: "Protected", value: row.protectedTransactions, color: "#bd8a27" },
      { key: "holds", label: "Hard holds", value: row.hardHolds, color: "#b23c3c" },
      { key: "cancelled", label: "Cancelled", value: row.cancelledTransactions, color: "#5b7470" },
      { key: "rejected", label: "Rejected", value: row.rejectedTransactions, color: "#8a2f2f" }
    ];
    const peak = Math.max(1, ...items.map((item) => item.value));
    const total = Math.max(1, row.totalTransactions);
    return items.map((item) => ({
      ...item,
      share: Math.round((item.value / total) * 100),
      pct: Math.round((item.value / peak) * 100)
    }));
  });
  riskBars = ko.pureComputed(() => {
    const counts = { LOW: 0, MEDIUM: 0, HIGH: 0, VERY_HIGH: 0 };
    for (const row of this.ledger()) {
      const tier = row.riskTier === "HARD_HOLD" ? "VERY_HIGH" : row.riskTier;
      if (tier in counts) counts[tier as keyof typeof counts] += 1;
    }
    const items = [
      { label: "Low", value: counts.LOW, color: "#12675f" },
      { label: "Medium", value: counts.MEDIUM, color: "#bd8a27" },
      { label: "High", value: counts.HIGH, color: "#7a4ea3" },
      { label: "Very high", value: counts.VERY_HIGH, color: "#b23c3c" }
    ];
    const peak = Math.max(1, ...items.map((item) => item.value));
    const total = Math.max(1, this.ledger().length);
    return items.map((item) => ({
      ...item,
      share: Math.round((item.value / total) * 100),
      pct: Math.round((item.value / peak) * 100)
    }));
  });
  amountBars = ko.pureComputed(() => {
    const row = this.summary();
    if (!row) return [];
    const items = [
      { label: "All recorded volume", value: row.totalAmount, color: "#244f59" },
      { label: "Settled volume", value: row.settledAmount, color: "#12675f" },
      { label: "Still on hold", value: this.heldAmount(), color: "#b23c3c" }
    ];
    const peak = Math.max(1, ...items.map((item) => item.value));
    return items.map((item) => ({ ...item, pct: Math.round((item.value / peak) * 100) }));
  });
  dailyBars = ko.pureComputed(() => {
    const rows = this.chartDays();
    const peak = Math.max(1, ...rows.map((row) => row.summary.totalTransactions));
    return rows.map((row) => ({
      date: row.date,
      label: this.shortDate(row.date),
      value: row.summary.totalTransactions,
      settled: row.summary.settledTransactions,
      holds: row.summary.hardHolds,
      height: row.summary.totalTransactions ? Math.max(8, Math.round((row.summary.totalTransactions / peak) * 140)) : 3,
      settledPct: row.summary.totalTransactions
        ? Math.round((row.summary.settledTransactions / row.summary.totalTransactions) * 100) : 0
    }));
  });
  donutStyle = ko.pureComputed(() => {
    const row = this.summary();
    if (!row || !row.totalTransactions) return "conic-gradient(#dce3e6 0 100%)";
    const settled = (row.settledTransactions / row.totalTransactions) * 100;
    const protectedShare = settled + (row.protectedTransactions / row.totalTransactions) * 100;
    const holds = protectedShare + (row.hardHolds / row.totalTransactions) * 100;
    const cancelled = holds + (row.cancelledTransactions / row.totalTransactions) * 100;
    return `conic-gradient(#12675f 0 ${settled}%, #bd8a27 ${settled}% ${protectedShare}%, #b23c3c ${protectedShare}% ${holds}%, #5b7470 ${holds}% ${cancelled}%, #8a2f2f ${cancelled}% 100%)`;
  });
  private share(value: number | undefined): string {
    const total = this.summary()?.totalTransactions || 0;
    if (!total) return "0%";
    return `${Math.round(((value || 0) / total) * 100)}%`;
  }
  private chartDays(): DailyTransactionSummary[] {
    return this.daily().slice().sort((a, b) => a.date.localeCompare(b.date));
  }
  stateLabel = (state: string): string => statusLabel(state);
  riskLabel = (tier: string): string => tierRisk(tier);
  stateClass = (state: string): string => "admin-pill admin-pill-" + (state || "").toLowerCase().replace(/_/g, "-");
  masked = (accountNumber: string): string => accountNumber ? "•••• " + accountNumber.slice(-4) : "—";
  when = (value: string): string => value
    ? new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
    : "—";
  load = async (): Promise<void> => {
    if (this.loading()) return;
    const from = this.from(), to = this.to();
    const valid = (v: string): boolean => /^\d{4}-\d{2}-\d{2}$/.test(v) && Number.isFinite(Date.parse(v)) && new Date(v).toISOString().slice(0, 10) === v;
    if (!valid(from) || !valid(to) || from > to || Date.parse(to) - Date.parse(from) > 365 * 86400000) {
      this.error("Choose a valid date range of at most 366 days."); return;
    }
    const generation = ++this.generation;
    this.loading(true); this.error("");this.directoryError("");this.holdsError(""); this.forbidden(false); this.summary(null); this.daily([]); this.updated("");
    try {
      const summary = await adminReportService.summary();
      if (generation !== this.generation) return;
      const daily = await adminReportService.daily(from, to);
      if (generation !== this.generation) return;
      this.loadedFrom(from); this.loadedTo(to);
      this.daily(daily.slice().sort((a, b) => b.date.localeCompare(a.date)));
      this.summary(summary);
      this.updated(new Date().toLocaleTimeString("en-IN"));
      try {
        const holds = await adminHoldService.list();
        if (generation === this.generation) this.holds(holds.slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
      } catch { if (generation === this.generation) {this.holds([]);this.holdsError("Held payments could not be loaded. Refresh to retry.");} }
      try {
        const book = await adminReportService.book();
        if (generation === this.generation) {
          this.ledger(book.payments);
          this.people(book.users);
        }
      } catch { if (generation === this.generation) { this.ledger([]); this.people([]);this.directoryError("The user directory could not be loaded."); } }
    } catch (e) {
      if (generation !== this.generation) return;
      this.summary(null); this.daily([]); this.holds([]); this.ledger([]); this.people([]);
      if (e instanceof ApiError && e.status === 401) window.location.replace("/admin/login");
      else if (e instanceof ApiError && e.status === 403) this.forbidden(true);
      else this.error("Reports are unavailable right now. Please try again.");
    } finally { if (generation === this.generation) this.loading(false); }
  };
  logout = async (): Promise<void> => {
    if (this.signingOut()) return;
    this.signingOut(true);
    try {
      await authService.logout();
      window.location.replace("/login");
    } catch {
      this.signingOut(false);
      this.error("We couldn’t sign you out. Please try again.");
    }
  };
  parametersChanged(params:{page?:string}):void{
    this.generation++;this.usersPage()?.disconnected();this.holdsPage()?.disconnected();this.usersPage(null);this.holdsPage(null);this.loading(false);
    this.context.params=params;this.connected();
  }
  connected(): void {
    if (this.context.params?.page === "users") {
      const model = new AdminUsersModel(); this.usersPage(model);
      document.title = "Admin users | SafePay"; void model.load(); return;
    }
    if (this.context.params?.page === "holds") {
      const model = new AdminHoldsModel(); this.holdsPage(model);
      document.title = "Held payments | SafePay"; void model.load(); return;
    }
    if (this.context.params?.page !== "dashboard") { window.location.replace("/login?admin=1&reason=session-expired"); return; }
    document.title = "Admin dashboard | SafePay"; void this.load();
  }
  disconnected(): void {
    this.generation++; this.summary(null); this.daily([]); this.holds([]); this.ledger([]); this.people([]);
    this.usersPage()?.disconnected(); this.holdsPage()?.disconnected();
  }
}
export = AdminViewModel;
