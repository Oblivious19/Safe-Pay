import * as ko from "knockout";
import "ojs/ojdialog";
import { beneficiaryService, Beneficiary, BeneficiaryInput, validateBeneficiary } from "../services/beneficiaryService";
import { accountService } from "../services/accountService";
import { Account } from "../services/types";
import { ApiError } from "../services/apiError";
import { Bank, BANK_OPTIONS } from "../constants/banks";

class BeneficiariesViewModel {
  accounts = ko.observableArray<Account>([]);
  selectedAccountId = ko.observable<number | null>(null);
  accountOption = (a: Account): string => a.accountType + " •••• " + a.accountNumber.slice(-4) + " · " + a.status;
  includeInactive = ko.observable(false);
  mode = ko.observable<"list" | "add" | "details">("list");
  beneficiaries = ko.observableArray<Beneficiary>([]);
  selected = ko.observable<Beneficiary | null>(null);
  beneficiaryName = ko.observable("");
  bankAccountNumber = ko.observable("");
  ifsc = ko.observable("");
  readonly bankOptions = BANK_OPTIONS;
  selectedBank = ko.observable<Bank | undefined>();
  clearBankError = (): void => {
    if (this.fieldErrors().bank && this.bankOptions.some(bank => bank.value === this.selectedBank())) {
      const errors = { ...this.fieldErrors() }; delete errors.bank; this.fieldErrors(errors);
    }
  };
  fieldErrors = ko.observable<Partial<Record<keyof BeneficiaryInput | "bank", string>>>({});
  loading = ko.observable(false);
  saving = ko.observable(false);
  error = ko.observable("");
  success = ko.observable("");
  confirmDeactivate = ko.observable(false);
  confirmExternal = ko.observable(false);
  externalReviewed = ko.observable(false);
  dismissExternal = (): void => { if (!this.saving()) this.clearExternal(); };
  beforeExternalClose = (event: Event): void => { if (this.saving() && this.confirmExternal()) event.preventDefault(); };
  private externalDraft = "";
  private draft = (): string => JSON.stringify([this.selectedAccountId(), this.beneficiaryName(), this.bankAccountNumber(), this.ifsc(), this.selectedBank()]);
  clearExternal = (): void => { this.confirmExternal(false); this.externalReviewed(false); this.externalDraft = ""; };
  private draftSubscriptions = [
    this.selectedAccountId.subscribe(() => this.clearExternal()),
    this.beneficiaryName.subscribe(() => this.clearExternal()),
    this.bankAccountNumber.subscribe(() => this.clearExternal()),
    this.ifsc.subscribe(() => this.clearExternal()),
    this.selectedBank.subscribe(() => this.clearExternal())
  ];
  private generation = 0;
  private alive = true;
  mask = (value: string): string => value.length > 4 ? "•••• " + value.slice(-4) : "••••";
  added = (value: string): string => {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Added date unavailable" : "Added " + new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(date);
  };
  isNew = (value: string): boolean => {
    const age = Date.now() - new Date(value).getTime();
    return age >= 0 && age < 86400000;
  };
  private handle(error: unknown): void {
    if (error instanceof ApiError && error.status === 401) {
      this.beneficiaries([]); this.selected(null); this.selectedBank(undefined);
      window.location.replace("/login?reason=session-expired");
      this.error("Your session has expired. Please log in again.");
    } else if (error instanceof ApiError && [400, 403, 404, 409].includes(error.status)) {
      this.error(error.message);
    } else this.error("We couldn’t complete this request. Please try again.");
  }
  load = async (): Promise<void> => {
    if (this.loading()) return;
    const generation = ++this.generation;
    this.loading(true); this.error(""); this.beneficiaries([]);
    try {
      const accounts=await accountService.list();
      if(!this.alive || generation!==this.generation)return;
      this.accounts(accounts);
      const chosen=accounts.find(a=>a.accountId===this.selectedAccountId()) || accounts.find(a=>a.status==="ACTIVE") || accounts[0];
      this.selectedAccountId(chosen?.accountId || null);
      if(!chosen){this.error("No account is linked to this profile yet.");return;}
      const list = await beneficiaryService.list(chosen.accountId,this.includeInactive());
      if (this.alive && generation === this.generation) this.beneficiaries(list);
    } catch (error) { if (this.alive && generation === this.generation) this.handle(error); }
    finally { if (this.alive && generation === this.generation) this.loading(false); }
  };
  openAdd = (): void => {
    if (this.loading() || this.saving()) return;
    this.error(""); this.success(""); this.fieldErrors({});
    this.beneficiaryName(""); this.bankAccountNumber(""); this.ifsc("");
    this.selectedBank(undefined);
    this.mode("add");
  };
  back = (): void => {
    if (this.saving() || this.loading()) return;
    this.mode("list"); this.selected(null); this.error(""); this.confirmDeactivate(false);
    this.beneficiaryName(""); this.bankAccountNumber(""); this.ifsc("");
    this.selectedBank(undefined);
  };
  save = async (externalConfirmed: unknown = false): Promise<void> => {
    if (this.saving() || this.loading()) return;
    if (this.confirmExternal() && (externalConfirmed !== true || !this.externalReviewed())) return;
    if(!this.selectedAccountId()){this.error("Select an account first.");return;}
    const draft = this.draft();
    const input: BeneficiaryInput = { accountId: this.selectedAccountId()!, beneficiaryName: this.beneficiaryName(), bankAccountNumber: this.bankAccountNumber(), ifsc: this.ifsc() };
    if (externalConfirmed === true && this.confirmExternal() && this.externalReviewed() && this.externalDraft === draft) input.externalConfirmed = true;
    const errors: Partial<Record<keyof BeneficiaryInput | "bank", string>> = validateBeneficiary(input);
    if (!this.bankOptions.some(bank => bank.value === this.selectedBank())) errors.bank = "Choose a bank.";
    this.fieldErrors(errors); this.error(""); this.success("");
    if (Object.keys(errors).length) {
      document.getElementById("recipient-" + Object.keys(errors)[0])?.focus();
      return;
    }
    this.saving(true);
    try {
      await beneficiaryService.create(input);
      if (!this.alive) return;
      this.beneficiaryName(""); this.bankAccountNumber(""); this.ifsc("");
      this.selectedBank(undefined);
      this.mode("list"); this.success("Beneficiary added successfully.");
      await this.load();
    } catch (error) {
      if (!this.alive) return;
      if (error instanceof ApiError && error.status === 409 && error.message.includes("not verified as a SafePay user") && draft === this.draft()) {
        this.externalDraft = draft; this.externalReviewed(false); this.confirmExternal(true);
      } else { this.clearExternal(); this.handle(error); }
    }
    finally { if (this.alive) this.saving(false); }
  };
  select = async (beneficiary: Beneficiary): Promise<void> => {
    if (this.loading() || this.saving()) return;
    const generation = ++this.generation;
    this.mode("details"); this.selected(null); this.error(""); this.success(""); this.loading(true);
    this.confirmDeactivate(false);
    try {
      const detail = await beneficiaryService.get(beneficiary.beneficiaryId);
      if (this.alive && generation === this.generation) this.selected(detail);
    } catch (error) { if (this.alive && generation === this.generation) this.handle(error); }
    finally { if (this.alive && generation === this.generation) this.loading(false); }
  };
  deactivate = async (): Promise<void> => {
    if (!this.selected() || !this.confirmDeactivate() || this.saving()) return;
    this.saving(true); this.error("");
    try {
      await beneficiaryService.deactivate(this.selected()!.beneficiaryId);
      if (!this.alive) return;
      this.selected(null); this.confirmDeactivate(false); this.mode("list");
      this.success("Beneficiary deactivated. They no longer appear in your saved list.");
      await this.load();
    } catch (error) { if (this.alive) this.handle(error); }
    finally { if (this.alive) this.saving(false); }
  };
  changeAccount = (): void => {this.selectedBank(undefined);this.selected(null);this.mode("list");void this.load();};
  reactivate = async (): Promise<void> => {
    if(!this.selected() || this.saving())return;this.saving(true);this.error("");
    try{await beneficiaryService.reactivate(this.selected()!.beneficiaryId);if(this.alive){this.selected(null);this.mode("list");this.success("Beneficiary reactivated.");await this.load();}}
    catch(e){if(this.alive)this.handle(e);}finally{if(this.alive)this.saving(false);}
  };
  connected(): void {
    this.alive = true; document.title = "Beneficiaries | SafePay";
    if (new URLSearchParams(window.location.search).get("action") === "add") this.openAdd();
    void this.load();
  }
  disconnected(): void { this.alive = false; this.generation++; this.clearExternal(); this.draftSubscriptions.forEach(s => s.dispose()); this.selectedBank(undefined); this.selected(null); this.beneficiaries([]); }
}
export = BeneficiariesViewModel;
