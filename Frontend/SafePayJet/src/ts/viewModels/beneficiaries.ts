import * as ko from "knockout";
import app from "../appController";
import * as AccUtils from "../accUtils";
import { Account, addBeneficiary, Beneficiary, deleteBeneficiary, getAccounts, getBeneficiaries,
  getBeneficiary, updateBeneficiaryStatus, validateBeneficiary } from "../services/api";
import { endExpiredSession } from "../services/customerSession";

class BeneficiariesViewModel {
  signedInEmail = ko.pureComputed(() => app.profile()?.email || "");
  accounts = ko.observableArray<Account>([]);
  accountId = ko.observable("");
  beneficiaryName = ko.observable("");
  bankAccountNumber = ko.observable("");
  ifsc = ko.observable("");
  beneficiaries = ko.observableArray<Beneficiary>([]);
  loading = ko.observable(false);
  saving = ko.observable(false);
  error = ko.observable("");
  success = ko.observable("");
  fieldErrors = ko.observable<{ beneficiaryName?: string; bankAccountNumber?: string; ifsc?: string }>({});
  selected = ko.observable<Beneficiary | null>(null);
  busyId = ko.observable<number | null>(null);
  accountLabel = (account: Account): string => `${account.accountType} •••• ${account.accountNumber.slice(-4)} — ${account.status}`;
  private revision = 0;
  private connectedPage = false;
  private lifecycle = 0;
  private updatingSelection = false;
  constructor() {
    this.accountId.subscribe(() => {
      if (!this.updatingSelection && this.connectedPage) void this.loadBeneficiaries();
    });
  }
  private expired(error: unknown): boolean {
    return endExpiredSession(error, () => {
      this.connectedPage = false; this.lifecycle++; this.revision++;
      this.accounts([]); this.beneficiaries([]); this.selected(null);
      this.beneficiaryName(""); this.bankAccountNumber(""); this.ifsc("");
      this.error("Your session has expired. Please sign in again.");
    });
  }

  load = async (): Promise<void> => {
    if (this.saving() || this.busyId() !== null) return;
    await this.refreshAccounts();
  };
  private refreshAccounts = async (): Promise<void> => {
    const revision = ++this.revision;
    this.loading(true); this.error(""); this.beneficiaries([]); this.selected(null);
    try {
      const accounts = await getAccounts();
      if (!this.connectedPage || revision !== this.revision) return;
      this.updatingSelection = true;
      this.accounts(accounts);
      if (!accounts.some(account => account.accountId === Number(this.accountId()))) this.accountId(accounts.length ? String(accounts[0].accountId) : "");
      this.updatingSelection = false;
      await this.loadBeneficiaries();
    } catch (error) {
      if (this.connectedPage && revision === this.revision && !this.expired(error)) this.error(error instanceof Error ? error.message : "Could not load beneficiaries.");
    } finally { if (this.connectedPage && revision === this.revision) this.loading(false); }
  };
  private loadBeneficiaries = async (): Promise<void> => {
    const revision = ++this.revision;
    const account = this.accounts().find(item => item.accountId === Number(this.accountId()));
    this.beneficiaries([]); this.selected(null); this.error("");
    this.loading(!!account);
    if (!account) return;
    try {
      const beneficiaries = await getBeneficiaries(undefined, true, account.accountId);
      if (!this.connectedPage || revision !== this.revision) return;
      this.beneficiaries(beneficiaries.filter(beneficiary => beneficiary.accountId === account.accountId));
    } catch (error) {
      if (this.connectedPage && revision === this.revision && !this.expired(error)) this.error(error instanceof Error ? error.message : "Could not load beneficiaries for this account.");
    } finally { if (this.connectedPage && revision === this.revision) this.loading(false); }
  };

  save = async (): Promise<void> => {
    if (this.saving() || this.loading() || this.busyId() !== null) return;
    if (!this.accounts().some(account => account.accountId === Number(this.accountId()))) {
      this.error("Choose an account for this beneficiary."); return;
    }
    const fieldErrors = validateBeneficiary({ beneficiaryName: this.beneficiaryName(), bankAccountNumber: this.bankAccountNumber(), ifsc: this.ifsc() });
    this.fieldErrors(fieldErrors); this.error("");
    if (Object.keys(fieldErrors).length) { document.getElementById("beneficiary-" + Object.keys(fieldErrors)[0])?.focus(); return; }
    const lifecycle = this.lifecycle;
    const accountId = this.accountId();
    this.revision++; this.saving(true); this.error(""); this.success("");
    try {
      await addBeneficiary(Number(accountId), "", {
        beneficiaryName: this.beneficiaryName().trim(),
        bankAccountNumber: this.bankAccountNumber().trim(),
        ifsc: this.ifsc().trim().toUpperCase()
      });
      if (!this.connectedPage || lifecycle !== this.lifecycle || accountId !== this.accountId()) return;
      this.beneficiaryName(""); this.bankAccountNumber(""); this.ifsc("");
      this.success("Beneficiary added successfully.");
      await this.loadBeneficiaries();
    } catch (error) {
      if (this.connectedPage && lifecycle === this.lifecycle && !this.expired(error) && accountId === this.accountId()) this.error(error instanceof Error ? error.message : "Could not add beneficiary.");
    } finally { if (this.connectedPage && lifecycle === this.lifecycle) this.saving(false); }
  };

  details = async (beneficiary: Beneficiary): Promise<void> => {
    if (this.busyId() !== null || this.saving() || this.loading() || beneficiary.accountId !== Number(this.accountId())) return;
    const lifecycle = this.lifecycle;
    const revision = this.revision;
    this.busyId(beneficiary.beneficiaryId); this.error(""); this.selected(null);
    try {
      const result = await getBeneficiary(beneficiary.beneficiaryId);
      if (this.connectedPage && lifecycle === this.lifecycle && revision === this.revision && result.accountId === Number(this.accountId())) this.selected(result);
    } catch (error) {
      if (this.connectedPage && lifecycle === this.lifecycle && !this.expired(error) && revision === this.revision) this.error(error instanceof Error ? error.message : "Could not load beneficiary details.");
    } finally { if (this.connectedPage && lifecycle === this.lifecycle) this.busyId(null); }
  };
  closeDetails = (): void => { this.selected(null); };
  remove = (beneficiary: Beneficiary): Promise<void> => this.change(beneficiary, false);
  reactivate = (beneficiary: Beneficiary): Promise<void> => this.change(beneficiary, true);
  private async change(beneficiary: Beneficiary, reactivate: boolean): Promise<void> {
    if (this.busyId() !== null || this.saving() || this.loading() || beneficiary.accountId !== Number(this.accountId())) return;
    const lifecycle = this.lifecycle;
    const accountId = this.accountId();
    this.revision++; this.busyId(beneficiary.beneficiaryId); this.error(""); this.success("");
    try {
      if (reactivate) await updateBeneficiaryStatus(beneficiary.beneficiaryId, "ACTIVE");
      else await deleteBeneficiary(beneficiary.beneficiaryId);
      if (!this.connectedPage || lifecycle !== this.lifecycle || accountId !== this.accountId()) return;
      this.selected(null);
      this.success(reactivate ? "Beneficiary reactivated." : "Beneficiary removed from the active list. You can reactivate it below.");
      await this.loadBeneficiaries();
    } catch (error) {
      if (this.connectedPage && lifecycle === this.lifecycle && !this.expired(error) && accountId === this.accountId()) this.error(error instanceof Error ? error.message : "Could not update beneficiary.");
    } finally { if (this.connectedPage && lifecycle === this.lifecycle) this.busyId(null); }
  }
  connected(): void { this.connectedPage = true; this.lifecycle++; this.saving(false); this.busyId(null); AccUtils.announce("Beneficiaries page loaded."); document.title = "Beneficiaries | SafePay"; void this.load(); }
  disconnected(): void { this.connectedPage = false; this.lifecycle++; this.revision++; this.selected(null); }
}
export = BeneficiariesViewModel;
