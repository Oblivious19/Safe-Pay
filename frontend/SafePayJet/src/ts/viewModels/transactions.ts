import * as ko from 'knockout';
import { PageModel, confirmAction } from '../pageModel';
import { transactions, perform, recovery, paymentBusy } from '../services/transactionService';
import { verify, verificationPending, verificationTransactionId, reconcileVerification } from '../services/otpService';
import { refreshLoop } from '../services/refreshLoop';
import { Transaction, TransactionSummary, RouteParams, Challenge, AuditEvent, RiskExplanation } from '../services/types';
import { descriptions, description, states, label, knownState } from '../utils/paymentState';
import { dateFilter } from '../utils/format';
import { errorText, ApiError } from '../services/apiError';
class Transactions extends PageModel {
  rows = ko.observableArray<TransactionSummary>([]); loaded = ko.observable(false); page = ko.observable(0); totalPages = ko.observable(0); total = ko.observable('0'); sourceAccountId = ko.observable(''); filterState = ko.observable(''); from = ko.observable(''); to = ko.observable(''); states = states; label = label; description = description;
  detail = ko.observable<Transaction | null>(null); detailError = ko.observable(''); detailId = ko.observable(''); stale = ko.observable(true); risk = ko.observable<RiskExplanation | null>(null); audit = ko.observableArray<AuditEvent>([]); auditPage = ko.observable(0); auditPages = ko.observable(0); evidenceError = ko.observable(''); challenge = ko.observable<Challenge | null>(null); otp = ko.observable(''); otpError = ko.observable(''); otpExisting = ko.observable(false); remaining = ko.observable<number | null>(null); cooldown = ko.observable(0); otpRemaining = ko.observable(0); recoveryText = ko.observable(''); canReplay = ko.observable(false); verificationUnknown = ko.observable(false);
  private stop?: () => void; private ticker?: ReturnType<typeof setInterval>; private epoch = 0; private detailEpoch = 0; private observation = 0; private otpObservation = 0; private lastChallengeState = ''; private listReading = false; private listAgain = false; private detailReading = false; private detailAgain = false; private expiryObserved = '';
  pageCount = ko.pureComputed(() => Math.max(1, this.totalPages()));
  protectionLabel = ko.pureComputed(() => this.remaining() === null ? '' : Math.ceil(this.remaining()! / 1000) + ' seconds of protection remaining');
  canCancel = ko.pureComputed(() => !!this.detail() && knownState(this.detail()!.state) && !!this.detail()?.canCancel && !this.stale() && !this.busy() && !this.recoveryText() && !this.verificationUnknown() && (this.detail()?.state !== 'PROTECTED' || (this.remaining() !== null && this.remaining()! > 0)));
  canAuthorize = ko.pureComputed(() => this.detail()?.state === 'CREATED' && !this.stale() && !this.busy() && !this.recoveryText() && !this.verificationUnknown());
  known = ko.pureComputed(() => !!this.detail() && knownState(this.detail()!.state));
  constructor(context: any = {}) { super(); const params={...Object.fromEntries(new URLSearchParams(location.search)),...context.params}; this.detailId(params.transactionId || ''); this.sourceAccountId.subscribe(() => { this.epoch++; this.rows([]); this.loaded(false); }); }
  private routeChanged = (event: Event) => { const p=(event as CustomEvent).detail; if(p.transactionId){this.detailEpoch++;this.detail(null);this.detailId(p.transactionId);this.challenge(null);this.risk(null);this.audit([]);this.auditPage(0);void this.refreshDetail();} };
  connected(): void { document.title='Transactions | SafePay'; window.addEventListener('safepay:route-params',this.routeChanged); this.stop = refreshLoop(async () => { if (this.busy()) return; await this.load(); if (this.detailId()) await this.refreshDetail(); this.syncRecovery(); }); this.ticker = setInterval(() => this.tick(), 250); }
  disconnected(): void { window.removeEventListener('safepay:route-params',this.routeChanged); super.disconnected(); this.stop?.(); if (this.ticker) clearInterval(this.ticker); this.epoch++; this.detailEpoch++; this.otp(''); }
  syncRecovery = () => { try { const value = recovery(); this.canReplay(value.status === 'pending'); this.recoveryText(value.status === 'empty' ? '' : value.status === 'pending' ? 'Unconfirmed ' + value.attempt.intent.operation + ' operation from ' + this.date(new Date(value.attempt.createdAt).toISOString()) + '. Check its original result using the saved request and key.' : 'Recovery data is ' + value.status + '. Review payment history and seek reconciliation; a replacement payment is blocked.'); this.verificationUnknown(verificationPending()); } catch (error) { this.recoveryText(errorText(error)); } };
  load = async () => {
    if (this.listReading) { this.listAgain = true; return; }
    this.listReading = true; const epoch = ++this.epoch;
    try { const from = dateFilter(this.from()), to = dateFilter(this.to()); if (from && to && from >= to) throw new Error('From must be before To (exclusive).'); const value = await transactions.list({page:this.page(), size:10, sourceAccountId:this.sourceAccountId(), state:this.filterState(), from, to}); if (this.alive && epoch === this.epoch) { this.rows(value.items); this.totalPages(value.totalPages); this.total(value.totalElements); this.loaded(true); this.error(''); } }
    catch(error) { if (this.alive && epoch === this.epoch) this.error(errorText(error)); }
    finally { this.listReading = false; if (this.listAgain && this.alive) { this.listAgain = false; void this.load(); } }
  };
  filter = () => { this.page(0); this.rows([]); this.loaded(false); this.epoch++; void this.load(); };
  prev = () => { if (this.page() > 0) { this.epoch++; this.page(this.page()-1); void this.load(); } };
  next = () => { if (this.page()+1 < this.totalPages()) { this.epoch++; this.page(this.page()+1); void this.load(); } };
  open = (row: TransactionSummary) => { if(this.busy())return; this.detailEpoch++;this.detail(null);this.detailId(row.transactionId);this.challenge(null);this.risk(null);this.audit([]);this.auditPage(0);history.replaceState(null,'','/transactions?transactionId='+row.transactionId);void this.refreshDetail(); };
  closeDetail = () => {this.detailEpoch++;this.detailId('');this.detail(null);this.challenge(null);this.otp('');history.replaceState(null,'','/transactions');};
  refreshDetail = async () => {
    const id = this.detailId(); if (!id) return;
    if (this.detailReading) { this.detailEpoch++; this.detailAgain = true; return; }
    this.detailReading = true; const epoch = ++this.detailEpoch;
    try { const value = await transactions.get(id); if (!this.alive || epoch !== this.detailEpoch) return;
      if (value.state !== this.lastChallengeState || value.riskAssessedAt !== this.detail()?.riskAssessedAt) { this.challenge(null); this.otp(''); this.otpExisting(false); this.lastChallengeState = value.state; }
      this.detail(value); this.observation = performance.now(); this.stale(false); this.detailError(''); reconcileVerification(id, value.state); this.verificationUnknown(verificationPending()); this.tick();
    } catch(error) { if (this.alive && epoch === this.detailEpoch) { this.stale(true); this.detailError(errorText(error)); } }
    finally { this.detailReading = false; if (this.detailAgain && this.alive) { this.detailAgain = false; void this.refreshDetail(); } }
  };
  private tick(): void {
    const d = this.detail(); this.remaining(d?.state === 'PROTECTED' && typeof d.protectionRemainingMillis === 'number' ? Math.max(0, d.protectionRemainingMillis - (performance.now() - this.observation)) : null);
    const expiryKey = d ? d.transactionId + '/' + d.protectedUntil : '';
    if (this.remaining() === 0 && expiryKey !== this.expiryObserved) { this.expiryObserved = expiryKey; void this.refreshDetail(); }
    const c = this.challenge(); if (c) {
      // Replayed challenge JSON has its original serverTime. A fresh detail GET prevents restarting it.
      const challengeNow = Date.parse(c.serverTime) + performance.now() - this.otpObservation;
      const detailNow = d?.serverTime ? Date.parse(d.serverTime) + performance.now() - this.observation : challengeNow;
      const serverNow = Math.max(challengeNow, detailNow);
      this.cooldown(Math.max(0,Math.ceil((Date.parse(c.resendAvailableAt)-serverNow)/1000))); this.otpRemaining(Math.max(0,Math.ceil((Date.parse(c.expiresAt)-serverNow)/1000)));
    }
  }
  authorize = async () => { if (!this.canAuthorize()) return; const id=this.detailId(); if (await confirmAction('Authorize this instruction?', 'Authorize ' + this.money(this.detail()!.amount) + ' to ' + this.detail()!.beneficiaryName + '. SafePay will assess risk and may reserve funds.', 'Authorize payment')) {if(this.alive&&this.detailId()===id)await this.mutate('AUTHORIZE');} };
  cancel = async () => { if (!this.canCancel()) return; const id=this.detailId(); if (await confirmAction('Cancel this payment?', 'The backend will recheck eligibility. A protection deadline or settlement race may prevent cancellation.', 'Cancel payment', true)) {if(this.alive&&this.detailId()===id)await this.mutate('CANCEL');} };
  private async mutate(operation: 'AUTHORIZE' | 'CANCEL'): Promise<void> {
    await this.run(async () => { this.stale(true); try { await perform<Transaction>(operation === 'AUTHORIZE' ? {operation, transactionId:this.detailId(), payload:{confirmed:true}} : {operation,transactionId:this.detailId()}); } finally { await this.refreshDetail(); this.syncRecovery(); } });
  }
  replay = () => this.run(async () => {
    try { const saved = recovery(); if (saved.status !== 'pending') return; const result = await perform<Transaction | Challenge>(); if (!this.alive) return;
      if (saved.attempt.intent.operation.startsWith('OTP_')) { const challenge = result as Challenge; this.detailId(challenge.transactionId); await this.refreshDetail(); this.setChallenge(challenge); }
      else this.navigate('transactions', {transactionId: (result as Transaction).transactionId});
    } finally { this.syncRecovery(); }
  });
  issue = () => this.challengeRequest('OTP_ISSUE');
  resend = async () => { if (this.cooldown() > 0 || this.challenge()?.remainingIssues === 0) return; if (await confirmAction('Send a replacement code?', 'The previous code will stop working. The server enforces cooldown and issue limits.', 'Send new code')) await this.challengeRequest('OTP_RESEND'); };
  private setChallenge(value: Challenge): void { this.challenge(value); this.otpObservation = performance.now(); this.otp(''); this.otpError(''); this.otpExisting(true); this.tick(); }
  private challengeRequest(operation: 'OTP_ISSUE' | 'OTP_RESEND') { return this.run(async () => {
    if (this.stale() || this.detail()?.state !== 'VERIFICATION_REQUIRED' || verificationPending()) throw new Error('Refresh the payment and resolve verification before requesting a code.');
    try { const value = await perform<Challenge>({operation, transactionId:this.detailId()}); if (this.alive) this.setChallenge(value); }
    catch(error) { if (error instanceof ApiError && error.code === 'OTP_ALREADY_ISSUED') this.otpExisting(true); throw error; }
    finally { await this.refreshDetail(); this.syncRecovery(); }
  }); }
  verify = () => this.verifyCode(false);
  replayVerification = () => this.verifyCode(true);
  openVerification = () => { const id = verificationTransactionId(); if (id) this.navigate('transactions', {transactionId:id}); };
  private verifyCode(replay: boolean) { return this.run(async () => {
    if (this.stale() || this.detail()?.state !== 'VERIFICATION_REQUIRED') throw new Error('Refresh the payment before verification.');
    const c = this.challenge(); if (!c && !replay) throw new Error('Request a code before verifying.');
    const code = this.otp(); this.otp('');
    try { await verify(this.detailId(), c?.challengeId || '', code, replay); this.message('Verification accepted. Refreshing the payment outcome.'); }
    finally { await this.refreshDetail(); this.syncRecovery(); }
  }); }
  evidence = () => this.run(async () => { this.evidenceError(''); const id=this.detailId(); try { const events = await transactions.audit(id, this.auditPage()); if (!this.alive || this.detailId()!==id) return; this.audit(events.items); this.auditPages(events.totalPages); if (this.detail()?.riskTier) { const value = await transactions.risk(id); if (this.alive&&this.detailId()===id) this.risk(value); } } catch (e) { this.evidenceError(errorText(e)); } });
  auditPrev = () => { if (this.auditPage()>0) { this.auditPage(this.auditPage()-1); void this.evidence(); } };
  auditNext = () => { if (this.auditPage()+1<this.auditPages()) { this.auditPage(this.auditPage()+1); void this.evidence(); } };
}
export = Transactions;
