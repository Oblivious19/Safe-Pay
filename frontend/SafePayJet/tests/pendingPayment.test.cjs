"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { PendingPaymentStore, PENDING_STORAGE_KEY, PENDING_TTL_MS } = require("../.test-build/pendingPayment.js");
const KEY = "12345678-1234-4234-8234-123456789abc";
const NOW = 1000000;
const draft = () => ({ operation: "CREATE", payload: { sourceAccountId: "9007199254740993", beneficiaryId: "700", amount: "8000.00" } });
function storage() {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
}
const store = (disk, now = NOW) => new PendingPaymentStore(disk, () => now, () => KEY);

test("reload preserves exact identifiers, amount, frozen payload and original key", () => {
  const disk = storage();
  const input = draft();
  const saved = store(disk).begin("7", input);
  input.payload.amount = "9000.00";
  const reloaded = store(disk, NOW + 100).read("7");
  assert.equal(reloaded.status, "pending");
  assert.deepEqual(reloaded.attempt, saved);
  assert.equal(saved.intent.payload.sourceAccountId, "9007199254740993");
  assert.equal(saved.intent.payload.amount, "8000.00");
  assert.ok(Object.isFrozen(reloaded.attempt.intent.payload));
});
test("unresolved attempt cannot be overwritten with a new or changed request", () => {
  const disk = storage();
  store(disk).begin("7", draft());
  assert.throws(() => store(disk).begin("7", draft()), /Resolve/);
  assert.equal(store(disk).read("7").attempt.idempotencyKey, KEY);
});
test("different identity cannot read the previous customer's metadata", () => {
  const disk = storage();
  store(disk).begin("7", draft());
  assert.deepEqual(store(disk).read("8"), { status: "empty" });
  assert.equal(disk.getItem(PENDING_STORAGE_KEY), null);
});
test("expired or backward-clock attempt blocks replacement and never extends expiry", () => {
  const disk = storage();
  store(disk).begin("7", draft());
  for (const time of [NOW - 1, NOW + PENDING_TTL_MS, NOW + PENDING_TTL_MS + 1]) {
    const current = store(disk, time);
    assert.equal(current.read("7").status, "expired");
    assert.throws(() => current.begin("7", draft()), /Resolve/);
  }
  assert.equal(JSON.parse(disk.getItem(PENDING_STORAGE_KEY)).expiresAt, NOW + PENDING_TTL_MS);
});
test("corrupt or unsupported records fail closed without silently erasing the blocker", () => {
  const disk = storage();
  disk.setItem(PENDING_STORAGE_KEY, "not json");
  assert.equal(store(disk).read("7").status, "invalid");
  assert.throws(() => store(disk).begin("7", draft()), /Resolve/);
  disk.setItem(PENDING_STORAGE_KEY, JSON.stringify({ version: 2 }));
  assert.equal(store(disk).read("7").status, "invalid");
});
test("storage failures cannot masquerade as an empty successful recovery store", () => {
  const blocked = { getItem() { throw Error("blocked"); }, setItem() {}, removeItem() {} };
  assert.equal(store(blocked).read("7").status, "unavailable");
  assert.throws(() => store(blocked).begin("7", draft()), /Resolve/);
  const quota = storage();
  quota.setItem = () => { throw Error("quota"); };
  assert.throws(() => store(quota).begin("7", draft()), /storage is unavailable/);
});
test("secrets, unexpected fields and OTP verification cannot be persisted", () => {
  for (const extra of ["password", "otp", "accessToken", "arbitrary"]) {
    const input = draft();
    input.payload[extra] = "do not store";
    const disk = storage();
    assert.throws(() => store(disk).begin("7", input), /Unexpected/);
    assert.equal(disk.getItem(PENDING_STORAGE_KEY), null);
  }
  assert.throws(() => store(storage()).begin("7", { operation: "OTP_VERIFY", transactionId: "1001" }), /cannot be persisted/);
});
test("money and IDs stay within the existing backend format without Number coercion", () => {
  for (const amount of ["0.99", "8000.001", "8e3", "8,000.00", "8000", "-1.00", "10000000000000000.00"]) {
    const input = draft(); input.payload.amount = amount;
    assert.throws(() => store(storage()).begin("7", input));
  }
  const input = draft(); input.payload.sourceAccountId = "9223372036854775808";
  assert.throws(() => store(storage()).begin("7", input), /identifier/);
});
test("category applies strictly above one lakh and Others requires the original purpose", () => {
  const input = draft(); input.payload.amount = "100000.00";
  store(storage()).begin("7", input);
  input.payload.category = "MEDICAL";
  assert.throws(() => store(storage()).begin("7", input), /omit category/);
  input.payload.amount = "100000.01";
  store(storage()).begin("7", input);
  input.payload.category = "OTHERS";
  assert.throws(() => store(storage()).begin("7", input), /purpose/);
  input.payload.purpose = "Equipment";
  store(storage()).begin("7", input);
  input.payload.purpose = "x".repeat(141);
  assert.throws(() => store(storage()).begin("7", input), /normalized/);
});
test("supported non-create operations retain their target and exact allowed payload", () => {
  for (const operation of ["AUTHORIZE", "CANCEL", "OTP_ISSUE", "OTP_RESEND"]) {
    const input = { operation, transactionId: "1001", ...(operation === "AUTHORIZE" ? { payload: { confirmed: true } } : {}) };
    assert.deepEqual(store(storage()).begin("7", input).intent, input);
  }
  assert.throws(() => store(storage()).begin("7", { operation: "AUTHORIZE", transactionId: "1001", payload: { confirmed: false } }));
});
test("only the matching reconciled operation can be cleared, including expired metadata", () => {
  const disk = storage(); store(disk).begin("7", draft());
  assert.throws(() => store(disk).clearAfterReconciliation("7", "wrong"), /does not match/);
  store(disk, NOW + PENDING_TTL_MS).clearAfterReconciliation("7", KEY);
  assert.equal(store(disk).read("7").status, "empty");
});
test("logout clears only SafePay recovery metadata", () => {
  const disk = storage(); disk.setItem("unrelated", "keep");
  store(disk).begin("7", draft()); store(disk).clearForLogout();
  assert.equal(disk.getItem(PENDING_STORAGE_KEY), null);
  assert.equal(disk.getItem("unrelated"), "keep");
});
