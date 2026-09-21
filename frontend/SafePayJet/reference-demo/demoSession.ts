import { ApiError } from "./apiError";
import {
  Account, DailyTransactionSummary, HeldPayment, HoldDecision, HoldVerification, LoginResponse,
  PaymentTransaction, RiskTier, TransactionRequest, TransactionState, TransactionSummary,
  VerificationChallenge
} from "./types";

export type DemoId = "anika" | "kabir" | "admin";
export interface DemoPayee {
  beneficiaryId: number; beneficiaryName: string; bankAccountNumber: string;
  ifsc: string; status: string; createdAt: string;
}

interface DemoUser {
  id: DemoId;
  userId: number;
  accountId: number;
  name: string;
  email: string;
  phone: string;
  password: string;
  pin?: string;
  role: "CUSTOMER" | "ADMIN";
  balance: string;
}

const USERS: DemoUser[] = [
  { id: "anika", userId: 1, accountId: 2001, name: "Anika Sharma", email: "anika.demo@safepay.test", phone: "9810010001", password: "DemoPay@123", pin: "135790", role: "CUSTOMER", balance: "400000.00" },
  { id: "kabir", userId: 2, accountId: 2002, name: "Kabir Mehra", email: "kabir.demo@safepay.test", phone: "9820020002", password: "DemoPay@123", pin: "246810", role: "CUSTOMER", balance: "8000.00" },
  { id: "admin", userId: 9, accountId: 2009, name: "Demo Admin", email: "admin.demo@safepay.test", phone: "9890090009", password: "DemoAdmin@123", role: "ADMIN", balance: "0.00" }
];

const KEY = "safepay.demo";
const HOLD_KEY = "safepay.demo.holds";
/** Simulated step-up rules. A real deployment reads these from configuration. */
const OTP_TTL_SECONDS = 600;
const OTP_MAX_ATTEMPTS = 3;
/**
 * One held payment on the bank's side of the simulation. It lives outside the per-user
 * ledger so the customer session and the admin session look at the same queue.
 */
interface DemoHold {
  transactionId: number; transactionRef: string;
  ownerId: DemoId; customerName: string; customerEmail: string;
  amount: number; purpose: string;
  beneficiaryName: string; beneficiaryBankAccountNumber: string;
  riskTier: RiskTier; riskReason: string; createdAt: string;
  verification: HoldVerification; code: string; codeExpiresAt: string;
  verificationSentAt: string; verifiedAt: string; attempts: number;
  decision: HoldDecision; decidedAt: string; decidedBy: string; decisionNote: string;
}
/** Bump when seeded demo history must be merged into an already-saved ledger. */
const BOOK = 3;
interface DemoLedger {
  id: DemoId;
  book?: number;
  account?: Account;
  payees: DemoPayee[];
  payments: PaymentTransaction[];
  nextPayee: number;
  nextTx: number;
}
let activeId: DemoId | undefined;
let account: Account | undefined;
let payees: DemoPayee[] = [];
let payments: PaymentTransaction[] = [];
const idempotency = new Map<string, PaymentTransaction>();
let nextPayee = 501;
let nextTx = 9001;

function memory(): { getItem(key: string): string | null; setItem(key: string, value: string): void; removeItem(key: string): void } {
  if (typeof sessionStorage !== "undefined") return sessionStorage;
  const store: Record<string, string> = ((globalThis as { __safepayDemoStore?: Record<string, string> }).__safepayDemoStore
    ||= {});
  return {
    getItem: (key) => store[key] ?? null,
    setItem: (key, value) => { store[key] = value; },
    removeItem: (key) => { delete store[key]; }
  };
}

/**
 * The hold queue belongs to the simulated bank, not to a signed-in user, so it uses
 * localStorage: it must outlive a logout and be visible to an admin in another tab.
 */
function vault(): { getItem(key: string): string | null; setItem(key: string, value: string): void } {
  if (typeof localStorage !== "undefined") return localStorage;
  const store: Record<string, string> = ((globalThis as { __safepayHoldStore?: Record<string, string> }).__safepayHoldStore
    ||= {});
  return { getItem: (key) => store[key] ?? null, setItem: (key, value) => { store[key] = value; } };
}

function loadHolds(): DemoHold[] {
  try {
    const rows = JSON.parse(vault().getItem(HOLD_KEY) || "[]");
    return Array.isArray(rows) ? (rows as DemoHold[]) : [];
  } catch { return []; }
}

function saveHolds(rows: DemoHold[]): void {
  vault().setItem(HOLD_KEY, JSON.stringify(rows));
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}

function maskEmail(email: string): string {
  const [name, domain] = email.split("@");
  if (!domain) return email;
  const head = name.slice(0, 2);
  return `${head}${"•".repeat(Math.max(1, name.length - 2))}@${domain}`;
}

function sixDigits(): string {
  const random = typeof crypto !== "undefined" && crypto.getRandomValues
    ? crypto.getRandomValues(new Uint32Array(1))[0] / 4294967296
    : Math.random();
  return String(100000 + Math.floor(random * 900000));
}

function userById(id: DemoId): DemoUser {
  return USERS.find((user) => user.id === id)!;
}

function isDemoId(value: unknown): value is DemoId {
  return value === "anika" || value === "kabir" || value === "admin";
}

function ledgerKey(id: DemoId): string {
  return `${KEY}.ledger.${id}`;
}

/**
 * The simulated ledger must survive reloads, otherwise balances and history reset. It is
 * also kept per user in the vault so signing out and back in — or handing the session to
 * an admin and returning — does not erase the payments an admin is reviewing.
 */
function save(): void {
  if (!activeId) return;
  const ledger = JSON.stringify({ id: activeId, book: BOOK, account, payees, payments, nextPayee, nextTx } as DemoLedger);
  memory().setItem(KEY, ledger);
  vault().setItem(ledgerKey(activeId), ledger);
}

function applyLedger(ledger: DemoLedger): void {
  activeId = ledger.id;
  idempotency.clear();
  if (ledger.account) account = ledger.account;
  if (Array.isArray(ledger.payees)) payees = ledger.payees;
  if (Array.isArray(ledger.payments)) payments = ledger.payments;
  if (Number.isFinite(ledger.nextPayee)) nextPayee = ledger.nextPayee;
  if (Number.isFinite(ledger.nextTx)) nextTx = ledger.nextTx;
}

function readLedger(source: string | null, id?: DemoId): DemoLedger | undefined {
  if (!source) return undefined;
  try {
    const ledger = JSON.parse(source) as DemoLedger;
    if (!isDemoId(ledger?.id) || (id && ledger.id !== id)) return undefined;
    return ledger;
  } catch { return undefined; }
}

function restore(): DemoId | undefined {
  if (activeId) return activeId;
  const saved = memory().getItem(KEY);
  if (!saved) return undefined;
  if (isDemoId(saved)) { hydrate(saved); return saved; }
  const ledger = readLedger(saved);
  if (!ledger) return undefined;
  // The vault copy is authoritative: an admin may have credited this account meanwhile.
  applyLedger(migrateBook(readLedger(vault().getItem(ledgerKey(ledger.id)), ledger.id) || ledger));
  return ledger.id;
}

function money(value: string): number {
  return Number(value);
}

function iso(daysAgo = 0, secondsAhead = 0): string {
  return new Date(Date.now() - daysAgo * 86400000 + secondsAhead * 1000).toISOString();
}

function hydrate(id: DemoId): void {
  const saved = readLedger(vault().getItem(ledgerKey(id)), id);
  if (saved) { applyLedger(migrateBook(saved)); return; }
  seed(id);
}

/** First sign-in for a simulated user: opening balance, a recipient or two, some history. */
function seed(id: DemoId): void {
  activeId = id;
  const user = userById(id);
  account = {
    accountId: user.accountId,
    userId: user.userId,
    accountNumber: "5" + user.phone,
    accountType: "SAVINGS",
    balance: money(user.balance),
    status: "ACTIVE",
    createdAt: iso(40),
    updatedAt: iso()
  };
  const suffix = user.phone.slice(6);
  payees = id === "admin" ? [] : [
    { beneficiaryId: 501, beneficiaryName: "Rohan Gupta", bankAccountNumber: "501234" + suffix, ifsc: "HDFC0001234", status: "ACTIVE", createdAt: iso(40) }
  ];
  if (id === "anika") {
    payees.push({ beneficiaryId: 502, beneficiaryName: "Campus Mess", bankAccountNumber: "601234" + suffix, ifsc: "SBIN0000123", status: "ACTIVE", createdAt: iso() });
  }
  idempotency.clear();
  nextPayee = 503;
  // Distinct ranges per user keep transaction ids unique across the shared hold queue.
  nextTx = id === "kabir" ? 7001 : id === "admin" ? 5001 : 9001;
  payments = payees.length ? seedHistory() : [];
  if (account) {
    const spent = payments.filter((row) => row.state === "SETTLED").reduce((total, row) => total + row.amount, 0);
    account.balance = Math.round((account.balance - spent) * 100) / 100;
  }
}

function makePayment(payee: DemoPayee, entry: {
  ref: string; amount: number; purpose: string; daysAgo: number;
  state: TransactionState; riskTier: RiskTier; riskReason: string;
  protectionSeconds?: number; verification?: HoldVerification;
}): PaymentTransaction {
  const createdAt = iso(entry.daysAgo);
  const protectionSeconds = entry.protectionSeconds || 0;
  return {
    transactionId: nextTx++,
    transactionRef: entry.ref,
    amount: entry.amount,
    purpose: entry.purpose,
    fromAccountId: account!.accountId,
    beneficiaryId: payee.beneficiaryId,
    beneficiaryName: payee.beneficiaryName,
    beneficiaryBankAccountNumber: payee.bankAccountNumber,
    beneficiaryIfsc: payee.ifsc,
    state: entry.state,
    riskTier: entry.riskTier,
    riskReason: entry.riskReason,
    protectionSeconds,
    protectionExpiresAt: protectionSeconds ? iso(0, protectionSeconds) : "",
    createdAt,
    settledAt: entry.state === "SETTLED" ? createdAt : "",
    cancelledAt: entry.state === "CANCELLED" || entry.state === "REJECTED" ? createdAt : "",
    verification: entry.verification
  };
}

/** Settled history plus one row of each later outcome so the admin panel is never empty. */
function seedHistory(): PaymentTransaction[] {
  const payee = payees[0];
  const settled = [
    { amount: 1200, purpose: "Auto ride", daysAgo: 6, ref: "DEMO-SEED-1" },
    { amount: 2500, purpose: "Groceries", daysAgo: 4, ref: "DEMO-SEED-2" },
    { amount: 800, purpose: "Coffee run", daysAgo: 2, ref: "DEMO-SEED-3" }
  ].map((entry) => makePayment(payee, {
    ...entry, state: "SETTLED", riskTier: "LOW",
    riskReason: "Amount at most INR 10,000 (+0); Known device and context (+0); Total score 0: LOW"
  }));
  return [...showcaseHistory(payee), ...settled.reverse()];
}

function showcaseHistory(payee?: DemoPayee): PaymentTransaction[] {
  if (!payee || !account) return [];
  if (activeId === "anika") {
    return [
      makePayment(payee, {
        ref: "DEMO-SHOW-CANC", amount: 18500, purpose: "Laptop deposit", daysAgo: 3,
        state: "CANCELLED", riskTier: "MEDIUM",
        riskReason: "Amount above INR 10,000 (+2); Known device and context (+0); Total score 2: MEDIUM"
      }),
      makePayment(payee, {
        ref: "DEMO-SHOW-PROT", amount: 62000, purpose: "Rent top-up", daysAgo: 0,
        state: "PROTECTED", riskTier: "HIGH", protectionSeconds: 60,
        riskReason: "Amount above INR 50,000 (+4); Known device and context (+0); Total score 4: HIGH"
      }),
      makePayment(payee, {
        ref: "DEMO-SHOW-HOLD", amount: 125000, purpose: "Property token", daysAgo: 0,
        state: "HARD_HOLD", riskTier: "VERY_HIGH", verification: "NONE",
        riskReason: "Amount above INR 1,00,000 (+6); Known device and context (+0); Total score 6: VERY_HIGH"
      })
    ];
  }
  if (activeId === "kabir") {
    return [
      makePayment(payee, {
        ref: "DEMO-SHOW-REJ", amount: 2100, purpose: "Unknown payee", daysAgo: 5,
        state: "REJECTED", riskTier: "HIGH",
        riskReason: "Amount at most INR 10,000 (+0); Beneficiary added less than 24 hours ago (+2); No previous completed payment to this recipient (+1); Total score 3: HIGH"
      })
    ];
  }
  return [];
}

/** HIGH is 60 seconds. An earlier seed used 3600 and left a one-hour timer on the dashboard. */
function fixProtectionWindow(row: PaymentTransaction): PaymentTransaction {
  if (row.state !== "PROTECTED") return row;
  if ((row.protectionSeconds || 0) <= 60 && row.transactionRef !== "DEMO-SHOW-PROT") return row;
  if (row.transactionRef === "DEMO-SHOW-PROT" && (row.protectionSeconds || 0) > 60) {
    return { ...row, state: "CANCELLED", cancelledAt: iso(), protectionSeconds: 60, protectionExpiresAt: "" };
  }
  if ((row.protectionSeconds || 0) > 60) {
    const created = Date.parse(row.createdAt);
    const start = Number.isFinite(created) ? created : Date.now();
    return {
      ...row,
      protectionSeconds: 60,
      protectionExpiresAt: new Date(start + 60 * 1000).toISOString()
    };
  }
  return row;
}

/**
 * Older simulated ledgers only had three settled seeds. Fold in the missing showcase
 * rows so an administrator still sees every outcome without wiping customer history.
 */
function migrateBook(ledger: DemoLedger): DemoLedger {
  if ((ledger.book || 0) >= BOOK) return ledger;
  const keep = { activeId, account, payees, payments, nextPayee, nextTx };
  activeId = ledger.id;
  account = ledger.account;
  payees = ledger.payees || [];
  payments = ledger.payments || [];
  nextPayee = ledger.nextPayee || nextPayee;
  nextTx = Math.max(ledger.nextTx || 0, 19001);
  const extras = showcaseHistory(payees[0]);
  const refs = new Set(payments.map((row) => row.transactionRef));
  const add = extras.filter((row) => !refs.has(row.transactionRef));
  if (add.length && ledger.id === "anika" && account) {
    account.balance = round(account.balance + 150000);
  }
  payments = [...add, ...payments].map(fixProtectionWindow);
  const updated: DemoLedger = { id: ledger.id, book: BOOK, account, payees, payments, nextPayee, nextTx };
  vault().setItem(ledgerKey(ledger.id), JSON.stringify(updated));
  activeId = keep.activeId; account = keep.account; payees = keep.payees;
  payments = keep.payments; nextPayee = keep.nextPayee; nextTx = keep.nextTx;
  return updated;
}

export function matchDemo(input: { email?: string; phone?: string; password?: string; pin?: string }): DemoUser | undefined {
  return USERS.find((user) => {
    if (input.pin !== undefined) return user.phone === input.phone && user.pin === input.pin;
    if (input.phone !== undefined) return user.phone === input.phone && user.password === input.password;
    return user.email === input.email && user.password === input.password;
  });
}

export function beginDemo(id: DemoId): void {
  hydrate(id);
  save();
}

export function verifyDemoPin(pin: string): boolean {
  const id = restore();
  if (!id) return false;
  const expected = userById(id).pin;
  return !!expected && expected === pin;
}

export function demoRequiresPin(): boolean {
  const id = restore();
  return !!id && !!userById(id).pin;
}

/** Simulated users have published PINs, so the sheet can remind you which one to type. */
export function demoPinHint(): string {
  const id = restore();
  return id ? userById(id).pin || "" : "";
}

export function endDemo(): void {
  activeId = undefined;
  account = undefined;
  payees = [];
  payments = [];
  idempotency.clear();
  memory().removeItem(KEY);
}

export function isDemoActive(): boolean {
  return restore() !== undefined;
}

export function demoLogin(user: DemoUser): LoginResponse {
  return { userId: user.userId, name: user.name, email: user.email, role: user.role, status: "ACTIVE" };
}

export function demoProfile(): { name: string; email: string } {
  const user = userById(restore()!);
  return { name: user.name, email: user.email };
}

export function demoPhone(): string {
  const id = restore();
  if (!id || id === "admin") return "";
  const phone = userById(id).phone;
  return phone.length > 4 ? "•••• ••" + phone.slice(-4) : phone;
}

export function demoAccount(): Account {
  settleExpired();
  return { ...account! };
}

export function demoPayees(): DemoPayee[] {
  return payees.filter((payee) => payee.status === "ACTIVE").map((payee) => ({ ...payee }));
}

export function demoPayee(id: number): DemoPayee {
  const payee = payees.find((item) => item.beneficiaryId === id);
  if (!payee || payee.status !== "ACTIVE") throw new Error("missing");
  return { ...payee };
}

export function demoAddPayee(input: { beneficiaryName: string; bankAccountNumber: string; ifsc: string }): DemoPayee {
  const payee: DemoPayee = {
    beneficiaryId: nextPayee++,
    beneficiaryName: input.beneficiaryName.trim(),
    bankAccountNumber: input.bankAccountNumber.trim(),
    ifsc: input.ifsc.trim().toUpperCase(),
    status: "ACTIVE",
    createdAt: iso()
  };
  payees.push(payee);
  save();
  return { ...payee };
}

export function demoRemovePayee(id: number): void {
  const payee = payees.find((item) => item.beneficiaryId === id);
  if (payee) { payee.status = "INACTIVE"; save(); }
}

function settleExpired(): void {
  const now = Date.now();
  let changed = false;
  payments = payments.map((row) => {
    if (row.state !== "PROTECTED") return row;
    const expiry = Date.parse(row.protectionExpiresAt);
    if (!Number.isFinite(expiry) || expiry > now) return row;
    if (account) account.balance = round(account.balance - row.amount);
    changed = true;
    return { ...row, state: "SETTLED" as TransactionState, settledAt: new Date().toISOString() };
  });
  if (changed) save();
  syncHolds();
}

/**
 * Pull decisions made on the bank side back into this user's ledger. A hold never
 * releases on a timer: it moves only when the queue records a verified release or a
 * rejection, and the money leaves the account at that moment.
 */
function syncHolds(): void {
  if (!activeId) return;
  const holds = loadHolds();
  if (!holds.length) return;
  let changed = false;
  payments = payments.map((row) => {
    if (row.state !== "HARD_HOLD") return row;
    const hold = holds.find((item) => item.transactionId === row.transactionId && item.ownerId === activeId);
    if (!hold) return row;
    if (hold.decision === "RELEASED") {
      if (account) account.balance = round(account.balance - row.amount);
      changed = true;
      return {
        ...row, state: "SETTLED" as TransactionState, settledAt: hold.decidedAt,
        verification: hold.verification, verifiedAt: hold.verifiedAt
      };
    }
    if (hold.decision === "REJECTED") {
      changed = true;
      return { ...row, state: "REJECTED" as TransactionState, cancelledAt: hold.decidedAt, verification: hold.verification };
    }
    if (row.verification === hold.verification && row.verificationSentAt === hold.verificationSentAt) return row;
    changed = true;
    return {
      ...row, verification: hold.verification, verificationSentAt: hold.verificationSentAt,
      verifiedAt: hold.verifiedAt, verificationSentTo: maskEmail(hold.customerEmail)
    };
  });
  if (changed) save();
}

/** Mirrors PHASE1_SPEC section 2 bands. Device and context are known in this simulation. */
function decide(amountText: string, beneficiaryId: number): {
  state: TransactionState; riskTier: RiskTier; protectionSeconds: number; riskReason: string;
} {
  const amount = Number(amountText);
  const reasons: string[] = [];
  let score = 0;
  if (amount > 100000) { score += 6; reasons.push("Amount above INR 1,00,000 (+6)"); }
  else if (amount > 50000) { score += 4; reasons.push("Amount above INR 50,000 (+4)"); }
  else if (amount > 10000) { score += 2; reasons.push("Amount above INR 10,000 (+2)"); }
  else reasons.push("Amount at most INR 10,000 (+0)");

  const payee = payees.find((item) => item.beneficiaryId === beneficiaryId);
  if (payee && Date.now() - Date.parse(payee.createdAt) < 86400000) {
    score += 2; reasons.push("Beneficiary added less than 24 hours ago (+2)");
  }
  const settled = payments.filter((row) => row.state === "SETTLED");
  if (!settled.some((row) => row.beneficiaryId === beneficiaryId)) {
    score += 1; reasons.push("No previous completed payment to this recipient (+1)");
  }
  if (!settled.length) { score += 1; reasons.push("No recent payment history (+1)"); }
  else {
    const mean = settled.reduce((total, row) => total + row.amount, 0) / settled.length;
    if (amount > mean * 3) { score += 2; reasons.push("Much larger than your recent payments (+2)"); }
  }
  reasons.push("Known device and context (+0)");

  const tier: RiskTier = score >= 6 ? "VERY_HIGH" : score >= 4 ? "HIGH" : score >= 1 ? "MEDIUM" : "LOW";
  const seconds = tier === "MEDIUM" ? 10 : tier === "HIGH" ? 60 : 0;
  reasons.push(`Total score ${score}: ${tier}`);
  return {
    state: tier === "VERY_HIGH" ? "HARD_HOLD" : tier === "LOW" ? "SETTLED" : "PROTECTED",
    riskTier: tier,
    protectionSeconds: seconds,
    riskReason: reasons.join("; ")
  };
}

/** Funds already promised to in-flight payments cannot be spent again. */
export function demoAvailableBalance(): number {
  if (!account) return 0;
  const held = payments
    .filter((row) => row.state === "PROTECTED" || row.state === "HARD_HOLD")
    .reduce((total, row) => total + row.amount, 0);
  return Math.round((account.balance - held) * 100) / 100;
}

export function demoCreatePayment(input: TransactionRequest, key: string): PaymentTransaction {
  settleExpired();
  const existing = idempotency.get(key);
  if (existing) return { ...existing };
  const payee = demoPayee(input.beneficiaryId);
  const amount = Number(input.amount);
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new ApiError(400, "Enter a positive amount in rupees.", "validation");
  }
  if (account && amount > demoAvailableBalance()) {
    throw new ApiError(400, "This payment is more than the balance available in your account.", "validation");
  }
  const decision = decide(input.amount, input.beneficiaryId);
  const now = iso();
  if (decision.state === "SETTLED" && account) {
    account.balance = round(account.balance - amount);
  }
  const row: PaymentTransaction = {
    transactionId: nextTx++,
    transactionRef: "DEMO-" + Date.now(),
    amount,
    purpose: input.purpose || "",
    fromAccountId: input.fromAccountId,
    beneficiaryId: payee.beneficiaryId,
    beneficiaryName: payee.beneficiaryName,
    beneficiaryBankAccountNumber: payee.bankAccountNumber,
    beneficiaryIfsc: payee.ifsc,
    state: decision.state,
    riskTier: decision.riskTier,
    riskReason: decision.riskReason,
    protectionSeconds: decision.protectionSeconds,
    protectionExpiresAt: decision.protectionSeconds ? iso(0, decision.protectionSeconds) : "",
    createdAt: now,
    settledAt: decision.state === "SETTLED" ? now : "",
    cancelledAt: "",
    verification: decision.state === "HARD_HOLD" ? "NONE" : undefined
  };
  payments.unshift(row);
  idempotency.set(key, row);
  if (row.state === "HARD_HOLD") queueHold(row);
  save();
  return { ...row };
}

function holdFromPayment(row: PaymentTransaction, owner: DemoId): DemoHold {
  const user = userById(owner);
  return {
    transactionId: row.transactionId,
    transactionRef: row.transactionRef,
    ownerId: owner,
    customerName: user.name,
    customerEmail: user.email,
    amount: row.amount,
    purpose: row.purpose,
    beneficiaryName: row.beneficiaryName,
    beneficiaryBankAccountNumber: row.beneficiaryBankAccountNumber,
    riskTier: row.riskTier,
    riskReason: row.riskReason,
    createdAt: row.createdAt,
    verification: row.verification || "NONE",
    code: "",
    codeExpiresAt: "",
    verificationSentAt: row.verificationSentAt || "",
    verifiedAt: row.verifiedAt || "",
    attempts: 0,
    decision: "PENDING",
    decidedAt: "",
    decidedBy: "",
    decisionNote: ""
  };
}

/** A very high risk payment is parked on the bank side until someone verifies it. */
function queueHold(row: PaymentTransaction): void {
  const holds = loadHolds();
  if (holds.some((item) => item.transactionId === row.transactionId && item.ownerId === activeId)) return;
  holds.unshift(holdFromPayment(row, activeId!));
  saveHolds(holds);
}

/** Rebuild the bank queue from customer ledgers so an admin still sees holds after a session switch. */
function ensureHoldQueue(): DemoHold[] {
  const holds = loadHolds();
  let extra = false;
  for (const user of USERS) {
    if (user.role !== "CUSTOMER") continue;
    const list = user.id === activeId ? payments : (readOrSeed(user.id).payments || []);
    for (const row of list) {
      if (row.state !== "HARD_HOLD") continue;
      if (holds.some((item) => item.transactionId === row.transactionId && item.ownerId === user.id)) continue;
      // Append rebuilt rows — never unshift, or a seed hold can bury a live OTP row.
      holds.push(holdFromPayment(row, user.id));
      extra = true;
    }
  }
  if (extra) saveHolds(holds);
  return holds.slice().sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""));
}

function findHold(id: number, ownerId?: DemoId): { holds: DemoHold[]; hold: DemoHold } {
  const holds = loadHolds();
  const hold = holds.find((item) => item.transactionId === id && (!ownerId || item.ownerId === ownerId));
  if (!hold) throw new ApiError(404, "That held payment no longer exists.");
  return { holds, hold };
}

/** Issues a fresh code. In a real deployment this emails the code and returns nothing. */
function issueCode(hold: DemoHold, by: string): void {
  if (hold.decision !== "PENDING") {
    throw new ApiError(409, "This payment has already been decided.");
  }
  hold.code = sixDigits();
  hold.codeExpiresAt = iso(0, OTP_TTL_SECONDS);
  hold.verificationSentAt = iso();
  hold.verification = "OTP_SENT";
  hold.attempts = 0;
  hold.decisionNote = by ? `Code sent by ${by}` : hold.decisionNote;
}

export function demoSendHoldOtp(id: number): VerificationChallenge {
  const owner = restore();
  if (!owner) throw new ApiError(401, "Your session has ended. Please sign in again.");
  const { holds, hold } = findHold(id, owner);
  issueCode(hold, "");
  saveHolds(holds);
  syncHolds();
  return {
    transactionId: hold.transactionId,
    sentTo: maskEmail(hold.customerEmail),
    expiresAt: hold.codeExpiresAt,
    attemptsLeft: OTP_MAX_ATTEMPTS,
    simulatedCode: hold.code
  };
}

/**
 * Checks the code the customer read out during the simulated verification call. Success is
 * the only path out of HARD_HOLD into SETTLED; every failure leaves the money where it is.
 */
export function demoVerifyHoldOtp(id: number, code: string): PaymentTransaction {
  const owner = restore();
  if (!owner) throw new ApiError(401, "Your session has ended. Please sign in again.");
  const { holds, hold } = findHold(id, owner);
  if (hold.decision !== "PENDING") throw new ApiError(409, "This payment has already been decided.");
  if (hold.verification !== "OTP_SENT" || !hold.code) {
    throw new ApiError(409, "Ask for a verification code before entering one.");
  }
  if (Date.parse(hold.codeExpiresAt) <= Date.now()) {
    hold.verification = "FAILED"; hold.code = "";
    saveHolds(holds); syncHolds();
    throw new ApiError(410, "That code has expired. Ask for a new one.");
  }
  if (!/^\d{6}$/.test(code) || code !== hold.code) {
    hold.attempts += 1;
    const left = OTP_MAX_ATTEMPTS - hold.attempts;
    if (left <= 0) { hold.verification = "FAILED"; hold.code = ""; }
    saveHolds(holds); syncHolds();
    throw new ApiError(400, left > 0
      ? `That code is not right. ${left} ${left === 1 ? "attempt" : "attempts"} left.`
      : "Too many incorrect codes. Ask for a new one or contact support.");
  }
  hold.verification = "VERIFIED";
  hold.verifiedAt = iso();
  hold.code = "";
  hold.decision = "RELEASED";
  hold.decidedAt = hold.verifiedAt;
  hold.decidedBy = "Customer verification";
  hold.decisionNote = "Released after a successful verification call.";
  saveHolds(holds);
  syncHolds();
  save();
  return demoPayment(id);
}

/** Demo affordance only: stands in for opening the inbox the code was emailed to. */
export function demoHoldCode(id: number): string {
  try { return findHold(id, restore()).hold.code; } catch { return ""; }
}

export function demoHoldAttemptsLeft(id: number): number {
  try {
    const { hold } = findHold(id, restore());
    return Math.max(0, OTP_MAX_ATTEMPTS - hold.attempts);
  } catch { return OTP_MAX_ATTEMPTS; }
}

function publicHold(hold: DemoHold): HeldPayment {
  return {
    transactionId: hold.transactionId, transactionRef: hold.transactionRef,
    customerName: hold.customerName, customerEmail: hold.customerEmail,
    amount: hold.amount, purpose: hold.purpose,
    beneficiaryName: hold.beneficiaryName,
    beneficiaryBankAccountNumber: hold.beneficiaryBankAccountNumber,
    riskTier: hold.riskTier, riskReason: hold.riskReason, createdAt: hold.createdAt,
    verification: hold.verification, verificationSentAt: hold.verificationSentAt,
    verifiedAt: hold.verifiedAt,
    attemptsLeft: Math.max(0, OTP_MAX_ATTEMPTS - hold.attempts),
    decision: hold.decision, decidedAt: hold.decidedAt, decidedBy: hold.decidedBy,
    decisionNote: hold.decisionNote, simulatedCode: hold.code
  };
}

export function demoHeldPayments(): HeldPayment[] {
  return ensureHoldQueue().map(publicHold);
}

function emptySummary(): TransactionSummary {
  return {
    totalTransactions: 0, settledTransactions: 0, protectedTransactions: 0,
    cancelledTransactions: 0, rejectedTransactions: 0, hardHolds: 0,
    highRiskTransactions: 0, totalAmount: 0, settledAmount: 0
  };
}

function allDemoPayments(): PaymentTransaction[] {
  return allDemoBooks().flatMap((book) => book.payments);
}

export interface AdminBookRow {
  customerName: string; customerEmail: string;
  transactionId: number; transactionRef: string; amount: number; purpose: string;
  beneficiaryName: string; beneficiaryBankAccountNumber: string;
  state: TransactionState; riskTier: RiskTier; riskReason: string;
  createdAt: string; settledAt: string; cancelledAt: string; protectionExpiresAt: string;
}

export interface AdminUserSnapshot {
  userId: number; name: string; email: string; role: string; status: string;
  accountNumber: string; balance: number; payments: number; held: number; protectedCount: number;
}

function allDemoBooks(): { user: DemoUser; payments: PaymentTransaction[]; account?: Account }[] {
  const books: { user: DemoUser; payments: PaymentTransaction[]; account?: Account }[] = [];
  for (const user of USERS) {
    if (user.role !== "CUSTOMER") continue;
    const ledger = user.id === activeId
      ? { payments, account } as DemoLedger
      : readOrSeed(user.id);
    books.push({ user, payments: ledger.payments || [], account: ledger.account });
  }
  return books;
}

/** Every simulated payment and customer balance, for the administrator's ledger view. */
export function demoAdminBook(): { payments: AdminBookRow[]; users: AdminUserSnapshot[] } {
  const rows: AdminBookRow[] = [];
  const users: AdminUserSnapshot[] = demoUsers().map((row) => {
    const user = USERS.find((item) => item.userId === row.userId)!;
    const ledger = user.role === "CUSTOMER"
      ? (user.id === activeId ? { account, payments } as DemoLedger : readOrSeed(user.id))
      : { account: undefined, payments: [] as PaymentTransaction[] };
    const list = ledger.payments || [];
    return {
      userId: row.userId, name: row.name, email: row.email, role: row.role, status: row.status,
      accountNumber: ledger.account?.accountNumber || "",
      balance: ledger.account ? Number(ledger.account.balance) : 0,
      payments: list.length,
      held: list.filter((item) => item.state === "HARD_HOLD").length,
      protectedCount: list.filter((item) => item.state === "PROTECTED").length
    };
  });
  for (const book of allDemoBooks()) {
    for (const row of book.payments) {
      rows.push({
        customerName: book.user.name, customerEmail: book.user.email,
        transactionId: row.transactionId, transactionRef: row.transactionRef,
        amount: row.amount, purpose: row.purpose, beneficiaryName: row.beneficiaryName,
        beneficiaryBankAccountNumber: row.beneficiaryBankAccountNumber,
        state: row.state, riskTier: row.riskTier, riskReason: row.riskReason,
        createdAt: row.createdAt, settledAt: row.settledAt, cancelledAt: row.cancelledAt,
        protectionExpiresAt: row.protectionExpiresAt
      });
    }
  }
  rows.sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""));
  return { payments: rows, users };
}

function summarise(rows: PaymentTransaction[]): TransactionSummary {
  const summary = emptySummary();
  for (const row of rows) {
    summary.totalTransactions += 1;
    summary.totalAmount = round(summary.totalAmount + (Number(row.amount) || 0));
    if (row.state === "SETTLED") {
      summary.settledTransactions += 1;
      summary.settledAmount = round(summary.settledAmount + (Number(row.amount) || 0));
    } else if (row.state === "PROTECTED") summary.protectedTransactions += 1;
    else if (row.state === "HARD_HOLD") summary.hardHolds += 1;
    else if (row.state === "CANCELLED") summary.cancelledTransactions += 1;
    else if (row.state === "REJECTED") summary.rejectedTransactions += 1;
    if (row.riskTier === "HIGH" || row.riskTier === "VERY_HIGH" || row.riskTier === "HARD_HOLD") {
      summary.highRiskTransactions += 1;
    }
  }
  return summary;
}

/** Same shape as GET /api/admin/reports/transactions/summary, built from the simulated ledgers. */
export function demoTransactionSummary(): TransactionSummary {
  return summarise(allDemoPayments());
}

/** Same shape as GET /api/admin/reports/transactions/daily, grouped by creation date. */
export function demoDailyReports(from: string, to: string): DailyTransactionSummary[] {
  const groups = new Map<string, PaymentTransaction[]>();
  for (const row of allDemoPayments()) {
    const day = (row.createdAt || "").slice(0, 10);
    if (!day || day < from || day > to) continue;
    const list = groups.get(day) || [];
    list.push(row);
    groups.set(day, list);
  }
  return [...groups.entries()]
    .sort((left, right) => left[0].localeCompare(right[0]))
    .map(([date, list]) => ({ date, summary: summarise(list) }));
}

/** Reads another user's stored ledger without disturbing the signed-in session. */
function readOrSeed(id: DemoId): DemoLedger {
  const saved = readLedger(vault().getItem(ledgerKey(id)), id);
  if (saved) return migrateBook(saved);
  const keep = { activeId, account, payees, payments, nextPayee, nextTx };
  seed(id);
  const fresh: DemoLedger = { id, book: BOOK, account, payees, payments, nextPayee, nextTx };
  vault().setItem(ledgerKey(id), JSON.stringify(fresh));
  activeId = keep.activeId; account = keep.account; payees = keep.payees;
  payments = keep.payments; nextPayee = keep.nextPayee; nextTx = keep.nextTx;
  return fresh;
}

const decimals = (value: number): string => value.toFixed(2);

export function demoUsers(): { userId: number; name: string; email: string; phone: string; role: string; status: string }[] {
  return USERS.map(({ userId, name, email, phone, role }) => ({ userId, name, email, phone, role, status: "ACTIVE" }));
}

export function demoUserAccount(userId: number):
  { accountId: number; accountNumber: string; accountType: string; status: string; balance: string } {
  const user = USERS.find((item) => item.userId === userId);
  if (!user) throw new ApiError(404, "The requested resource was not found.");
  const ledger = readOrSeed(user.id);
  const row = ledger.account!;
  return {
    accountId: row.accountId, accountNumber: row.accountNumber, accountType: row.accountType,
    status: row.status, balance: decimals(row.balance)
  };
}

/**
 * Manual simulated credit. It is the demo stand-in for the bank interest endpoint, and the
 * quickest way to fund an account before testing a very high risk payment.
 */
export function demoCreditAccount(accountId: number, amount: string):
  { accountId: number; amount: string; balanceBefore: string; balanceAfter: string; createdAt: string; description: string } {
  const user = USERS.find((item) => item.accountId === accountId);
  if (!user) throw new ApiError(404, "The requested resource was not found.");
  const value = Number(amount);
  if (!Number.isFinite(value) || value <= 0) {
    throw new ApiError(400, "Enter a positive amount with at most two decimals.", "validation");
  }
  const ledger = readOrSeed(user.id);
  const before = ledger.account!.balance;
  const after = round(before + value);
  ledger.account!.balance = after;
  vault().setItem(ledgerKey(user.id), JSON.stringify(ledger));
  if (activeId === user.id && account) { account.balance = after; save(); }
  return {
    accountId, amount: decimals(value), balanceBefore: decimals(before), balanceAfter: decimals(after),
    createdAt: iso(), description: "Simulated bank interest credit"
  };
}

/** Admin can re-send a code when the customer never received or already used one. */
export function demoAdminSendOtp(id: number, actor: string): HeldPayment {
  const { holds, hold } = findHold(id);
  issueCode(hold, actor || "an administrator");
  saveHolds(holds);
  return publicHold(hold);
}

/**
 * Manual release stays gated on a successful verification: PHASE1_SPEC only allows
 * HARD_HOLD to settle after step-up authentication, so an admin cannot skip it.
 */
export function demoAdminRelease(id: number, actor: string): HeldPayment {
  const { holds, hold } = findHold(id);
  if (hold.decision !== "PENDING") throw new ApiError(409, "This payment has already been decided.");
  if (hold.verification !== "VERIFIED") {
    throw new ApiError(409, "This payment can only be released after the customer passes verification.");
  }
  hold.decision = "RELEASED";
  hold.decidedAt = iso();
  hold.decidedBy = actor || "Administrator";
  hold.decisionNote = "Released by an administrator after verification.";
  saveHolds(holds);
  return publicHold(hold);
}

export function demoAdminReject(id: number, actor: string, note: string): HeldPayment {
  const { holds, hold } = findHold(id);
  if (hold.decision !== "PENDING") throw new ApiError(409, "This payment has already been decided.");
  hold.decision = "REJECTED";
  hold.decidedAt = iso();
  hold.decidedBy = actor || "Administrator";
  hold.decisionNote = note.trim() || "Rejected by an administrator.";
  saveHolds(holds);
  return publicHold(hold);
}

export function demoPayments(): PaymentTransaction[] {
  settleExpired();
  return payments.map((row) => ({ ...row }));
}

export function demoPayment(id: number): PaymentTransaction {
  settleExpired();
  const row = payments.find((item) => item.transactionId === id);
  if (!row) throw new Error("missing");
  return { ...row };
}

export function demoCancelPayment(id: number): PaymentTransaction {
  settleExpired();
  const index = payments.findIndex((item) => item.transactionId === id);
  if (index < 0) throw new Error("state");
  const row = payments[index];
  if (row.state !== "PROTECTED") throw new Error("state");
  const expiry = Date.parse(row.protectionExpiresAt);
  if (!Number.isFinite(expiry) || expiry <= Date.now()) throw new Error("state");
  const cancelled: PaymentTransaction = { ...row, state: "CANCELLED", cancelledAt: iso() };
  payments[index] = cancelled;
  save();
  return { ...cancelled };
}

export function isUnreachable(error: unknown): boolean {
  return !!error && typeof error === "object" && "status" in error && (error as { status: number }).status === 0;
}
