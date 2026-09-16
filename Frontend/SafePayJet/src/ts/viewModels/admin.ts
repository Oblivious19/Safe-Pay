import * as ko from "knockout";
import { adminService, AdminUser, AdminAccount, UserAccounts, CreditReceipt, ApprovalRequest } from "../services/adminService";
import { adminReportService } from "../services/adminReportService";
import { apiClient } from "../services/apiClient";
import { ApiError } from "../services/apiError";
import { AccountType, UserStatus, TransactionSummary, DailyTransactionSummary } from "../services/types";

import { CreditDraft, readCreditDraft, saveCreditDraft, clearCreditDraft, validCredit } from "../services/adminCreditDraft";
import { RefreshLoop } from "../services/refreshLoop";
import { withRetryKey } from "../services/api";
class AdminViewModel {
  approvals = ko.observableArray<ApprovalRequest>([]);
  approvalsError = ko.observable("");
  approvalTarget = ko.observable<ApprovalRequest | null>(null);
  private approvalPoll = new RefreshLoop(
    () => adminService.pendingApprovals(),
    rows => { if (this.verified()) { this.approvals(rows); this.approvalsError(""); } },
    error => { this.approvalsError(this.message(error)); },
    () => this.verified()
  );
  refreshApprovals = (): Promise<void> => this.approvalPoll.refresh();
  reviewApproval = (request: ApprovalRequest): void => {
    if (!this.disabled()) { this.approvalTarget(request); this.actionError(""); }
  };
  closeApproval = (): void => { if (!this.busy()) this.approvalTarget(null); };
  approvePayment = async (): Promise<void> => {
    const target = this.approvalTarget();
    if (!target || this.disabled()) return;
    const generation = this.generation;
    this.approvalPoll.invalidate();
    await this.mutate(async () => {
      await withRetryKey("approve:" + target.transactionId, key => adminService.approve(target.transactionId, key));
      if (generation !== this.generation || !this.verified()) return;
      this.approvals.remove(item => item.transactionId === target.transactionId);
      this.approvalTarget(null);
    }, "Payment approved and settled. The administrator and approval time were recorded in the audit log.");
    if (generation === this.generation && this.verified()) await this.approvalPoll.refresh();
  };
  private clearApprovals(): void { this.approvalPoll.stop(); this.approvals([]); this.approvalTarget(null); }

  selectedUser = ko.observable<AdminUser | null>(null);
  userAccounts = ko.observableArray<UserAccounts>([]);
  detailLoading = ko.observable(false);
  desiredStatus = ko.observable<UserStatus>("ACTIVE");
  statusChoices = ko.pureComputed(() => {
    const current = this.selectedUser()?.status;
    return current === "SUSPENDED" ? ["SUSPENDED", "ACTIVE", "INACTIVE"]
      : current === "INACTIVE" ? ["INACTIVE", "ACTIVE", "SUSPENDED"] : ["ACTIVE", "LOCKED", "SUSPENDED", "INACTIVE"];
  });
  provisionName = ko.observable(""); provisionEmail = ko.observable("");
  provisionPhone = ko.observable(""); initialPassword = ko.observable("");
  creditAccountId = ko.observable(""); creditAmount = ko.observable(""); creditConfirmed = ko.observable(false);
  creditDraft = ko.observable<CreditDraft | null>(readCreditDraft());
  creditReceipt = ko.observable<CreditReceipt | null>(null);
  private detailGeneration = 0;
  creditAccountLabel = (account: UserAccounts): string => account.accountType + " •••• " + account.accountNumber.slice(-4) + " — " + account.status;
  exactMoney = (value: string): string => {
    if (!/^-?[0-9]+(\.[0-9]{1,2})?$/.test(value)) return "Amount unavailable";
    const [integer, fraction = ""] = value.split(".");
    const negative = integer.startsWith("-");
    const digits = negative ? integer.slice(1) : integer;
    const tail = digits.slice(-3), head = digits.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ",");
    return (negative ? "−" : "") + "₹" + (head ? head + "," : "") + tail + "." + fraction.padEnd(2, "0");
  };
  private clearDetails(): void {
    this.detailGeneration++; this.selectedUser(null); this.userAccounts([]); this.detailLoading(false);
    this.initialPassword(""); this.creditReceipt(null); this.creditAmount(""); this.creditConfirmed(false);
  }
  viewUser = async (user: AdminUser): Promise<void> => {
    if (this.disabled()) return;
    const generation = ++this.detailGeneration;
    this.selectedUser(null); this.userAccounts([]); this.creditAccountId(""); this.creditAmount(""); this.creditReceipt(null);
    this.creditConfirmed(false); this.detailLoading(true); this.actionError("");
    try {
      const [detail, accounts] = await Promise.all([adminService.user(user.userId), adminService.userAccounts(user.userId)]);
      if (generation !== this.detailGeneration || !this.verified()) return;
      this.selectedUser(detail); this.desiredStatus(detail.status); this.userAccounts(accounts);
      if (accounts.length) this.creditAccountId(String(accounts[0].accountId));
    } catch (error) { if (generation === this.detailGeneration) this.actionError(this.message(error)); }
    finally { if (generation === this.detailGeneration) this.detailLoading(false); }
  };
  provision = async (): Promise<void> => {
    const generation = this.generation;
    await this.mutate(async () => {
      const saved = await adminService.createUser({ name: this.provisionName(), email: this.provisionEmail(), phone: this.provisionPhone().trim(), initialPassword: this.initialPassword() });
      if (generation !== this.generation) return;
      this.users.push(saved); this.provisionName(""); this.provisionEmail(""); this.provisionPhone("");
    }, "Administrator created without a bank account. Share the initial password with the intended administrator through your normal secure process.");
    this.initialPassword("");
  };
  saveUserStatus = async (): Promise<void> => {
    const user = this.selectedUser();
    if (!user) return;
    const generation = this.generation;
    await this.mutate(async () => {
      const saved = await adminService.setUserStatus(user.userId, this.desiredStatus());
      if (generation !== this.generation) return;
      const previous = this.users().find(item => item.userId === saved.userId);
      if (previous) this.users.replace(previous, saved);
      this.selectedUser(saved); this.desiredStatus(saved.status);
    }, "User status updated. The server recorded this change in the audit log.");
  };
  submitCredit = async (): Promise<void> => {
    if (!this.verified() || this.loading() || this.busy() || this.detailLoading()) return;
    let operation = this.creditDraft();
    if (!operation) {
      const account = this.userAccounts().find(item => item.accountId === Number(this.creditAccountId()));
      const amount = this.creditAmount().trim();
      if (!account || account.status !== "ACTIVE" || !validCredit(amount) || !this.creditConfirmed()) {
        this.actionError("Choose an active account, enter a positive amount with up to two decimals, and confirm the simulated credit."); return;
      }
      operation = { accountId: account.accountId, amount, key: crypto.randomUUID() };
      saveCreditDraft(operation); this.creditDraft(operation);
    }
    const generation = this.generation;
    this.busy(true); this.actionError(""); this.notice("");
    try {
      const receipt = await adminService.credit(operation.accountId, operation.amount, operation.key);
      // Confirmed completion clears durable retry state even if this page was left meanwhile.
      if (readCreditDraft()?.key === operation.key) clearCreditDraft();
      if (generation !== this.generation || !this.verified()) return;
      this.creditDraft(null); this.creditReceipt(receipt); this.creditAmount(""); this.creditConfirmed(false);
      this.userAccounts(this.userAccounts().map(account => account.accountId === receipt.accountId ? { ...account, balance: receipt.balanceAfter } : account));
      this.accounts(this.accounts().map(account => account.accountId === receipt.accountId ? { ...account, balance: receipt.balanceAfter } : account));
      this.notice("Simulated bank interest credit confirmed and recorded in the audit log.");
    } catch (error) {
      if (generation === this.generation) {
        if (error instanceof ApiError && error.status === 400) { clearCreditDraft(); this.creditDraft(null); }
        this.actionError(this.message(error));
      }
    } finally { if (generation === this.generation) this.busy(false); }
  };
  users = ko.observableArray<AdminUser>([]);
  accounts = ko.observableArray<AdminAccount>([]);
  summary = ko.observable<TransactionSummary | null>(null);
  daily = ko.observableArray<DailyTransactionSummary>([]);
  loading = ko.observable(false);
  busy = ko.observable(false);
  reportsLoading = ko.observable(false);
  dailyLoaded = ko.observable(false);
  verified = ko.observable(false);
  usersError = ko.observable("");
  accountsError = ko.observable("");
  summaryError = ko.observable("");
  reportError = ko.observable("");
  actionError = ko.observable("");
  notice = ko.observable("");
  editingId = ko.observable<number | null>(null);
  balance = ko.observable("");
  accountType = ko.observable<AccountType>("SAVINGS");
  from = ko.observable(new Date().toISOString().slice(0, 10));
  to = ko.observable(new Date().toISOString().slice(0, 10));
  private generation = 0;
  private reportGeneration = 0;
  disabled = ko.pureComputed(() => this.loading() || this.busy() || !this.verified() || !!this.creditDraft());
  formatMoney = (amount: number | string): string => new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(Number(amount));

  private message(error: unknown): string {
    if (error instanceof ApiError && error.status === 401) {
      this.clearApprovals(); this.clearDetails(); this.creditDraft(null); clearCreditDraft();
      this.reportGeneration++;
      this.reportsLoading(false);
      this.verified(false); this.users([]); this.accounts([]); this.summary(null); this.daily([]);
      window.location.replace("/login?reason=session-expired");
      return "Please sign in to continue.";
    }
    if (error instanceof ApiError && error.status === 403) {
      this.clearApprovals(); this.clearDetails(); this.verified(false); this.users([]); this.accounts([]); this.summary(null); this.daily([]);
      return "Administrator access or a refreshed security token is required. Reload and try again.";
    }
    return error instanceof ApiError ? error.message : "We couldn’t complete this request. Please try again.";
  }

  load = async (): Promise<void> => {
    if (this.busy()) return;
    const generation = ++this.generation;
    this.clearApprovals(); this.loading(true); this.verified(false); this.clearDetails(); this.creditDraft(readCreditDraft());
    this.users([]); this.accounts([]); this.summary(null);
    this.usersError(""); this.accountsError(""); this.summaryError("");
    try {
      // This protected request establishes that the current server session is an administrator.
      const users = await adminService.users();
      if (generation !== this.generation) return;
      this.users(users); this.verified(true); apiClient.useAdminCsrf(true); this.approvalPoll.start();
    } catch (error) {
      if (generation === this.generation) { this.usersError(this.message(error)); this.loading(false); }
      return;
    }
    const [accounts, summary] = await Promise.allSettled([adminService.accounts(), adminReportService.summary()]);
    if (generation !== this.generation) return;
    if (accounts.status === "fulfilled") this.accounts(accounts.value);
    else this.accountsError(this.message(accounts.reason));
    if (summary.status === "fulfilled" && this.verified()) this.summary(summary.value);
    else if (summary.status === "rejected") this.summaryError(this.message(summary.reason));
    this.loading(false);
  };

  loadDaily = async (): Promise<void> => {
    if (this.reportsLoading() || !this.verified()) return;
    const generation = ++this.reportGeneration;
    this.reportsLoading(true); this.dailyLoaded(false); this.reportError(""); this.daily([]);
    try {
      const rows = await adminReportService.daily(this.from(), this.to());
      if (generation === this.reportGeneration && this.verified()) { this.daily(rows); this.dailyLoaded(true); }
    } catch (error) {
      if (generation === this.reportGeneration) this.reportError(this.message(error));
    } finally {
      if (generation === this.reportGeneration) this.reportsLoading(false);
    }
  };

  private async mutate(action: () => Promise<void>, success: string): Promise<void> {
    if (this.disabled()) return;
    const generation = this.generation;
    this.busy(true); this.actionError(""); this.notice("");
    try {
      await action();
      if (generation === this.generation) this.notice(success);
    } catch (error) {
      if (generation === this.generation) this.actionError(this.message(error));
    } finally {
      if (generation === this.generation) this.busy(false);
    }
  }

  toggleUser = (user: AdminUser): Promise<void> => {
    if (!["ACTIVE", "SUSPENDED"].includes(user.status)) return Promise.resolve();
    const generation = this.generation;
    return this.mutate(async () => {
      const saved = await adminService.setUserStatus(user.userId, user.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE");
      if (generation === this.generation) this.users.replace(user, saved);
    }, `User ${user.userId} status updated.`);
  };
  toggleAccount = (account: AdminAccount): Promise<void> => {
    if (!["ACTIVE", "BLOCKED"].includes(account.status)) return Promise.resolve();
    const generation = this.generation;
    return this.mutate(async () => {
      const saved = await adminService.setAccountStatus(account.accountId, account.status === "ACTIVE" ? "BLOCKED" : "ACTIVE");
      if (generation === this.generation) this.accounts.replace(account, { ...account, ...saved });
    }, `Account ${account.accountId} status updated.`);
  };
  editAccount = (account: AdminAccount): void => {
    if (this.disabled()) return;
    this.editingId(account.accountId); this.accountType(account.accountType);
    // Require an explicit decimal value; do not round-trip a JSON numeric balance into a money edit.
    this.balance(""); this.actionError(""); this.notice("");
    document.getElementById("admin-balance")?.focus();
  };
  cancelEdit = (): void => { if (!this.busy()) { this.editingId(null); this.balance(""); } };
  saveAccount = (): Promise<void> => {
    const id = this.editingId();
    if (id === null) return Promise.resolve();
    const generation = this.generation;
    return this.mutate(async () => {
      const saved = await adminService.updateAccount(id, this.balance().trim(), this.accountType());
      if (generation !== this.generation) return;
      const old = this.accounts().find(account => account.accountId === id);
      if (old) this.accounts.replace(old, saved);
      this.editingId(null); this.balance("");
    }, `Account ${id} balance and type updated.`);
  };
  connected(): void { document.title = "Administration | SafePay"; void this.load(); }
  disconnected(): void {
    this.clearApprovals(); this.generation++; this.reportGeneration++; this.verified(false); this.clearDetails();
    this.busy(false); this.loading(false); this.reportsLoading(false); this.dailyLoaded(false);
    this.editingId(null); this.balance(""); this.users([]); this.accounts([]); this.summary(null); this.daily([]);
  }
}
export = AdminViewModel;
