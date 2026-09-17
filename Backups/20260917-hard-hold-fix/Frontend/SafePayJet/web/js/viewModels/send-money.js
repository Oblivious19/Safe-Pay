var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
define(["require", "exports", "knockout", "../services/beneficiaryService", "../services/accountService", "../services/transactionService", "../services/apiError", "../utils/protection", "../utils/chime", "ojs/ojprogress-circle", "ojs/ojdialog", "ojs/ojbutton", "ojs/ojtrain", "ojs/ojinputtext", "ojs/ojavatar", "ojs/ojmessages"], function (require, exports, ko, beneficiaryService_1, accountService_1, transactionService_1, apiError_1, protection_1, chime_1) {
    "use strict";
    class SendMoneyViewModel {
        constructor(context) {
            var _a;
            this.context = context;
            this.step = ko.observable("beneficiary");
            this.beneficiaries = ko.observableArray([]);
            this.selected = ko.observable(null);
            this.accounts = ko.observableArray([]);
            this.selectedAccountId = ko.observable(null);
            this.accountOption = (a) => a.accountType + " •••• " + a.accountNumber.slice(-4) + " · " + a.status;
            this.allBeneficiaries = [];
            this.account = ko.observable(null);
            this.amount = ko.observable("");
            this.purpose = ko.observable("");
            this.amountError = ko.observable("");
            this.purposeError = ko.observable("");
            this.error = ko.observable("");
            this.notice = ko.observable("");
            this.loading = ko.observable(false);
            this.busy = ko.observable(false);
            this.paymentError = ko.observable("");
            this.result = ko.observable(null);
            this.attemptLocked = ko.observable(false);
            this.refreshing = ko.observable(false);
            this.cancelOpen = ko.observable(false);
            this.now = ko.observable(Date.now());
            this.processing = ko.observable(0);
            this.trainSteps = [
                { id: "beneficiary", label: "Recipient" },
                { id: "details", label: "Amount" },
                { id: "review", label: "Review" },
                { id: "result", label: "Status" }
            ];
            this.paymentMessages = ko.pureComputed(() => this.paymentError()
                ? [{ severity: "error", summary: this.paymentError(), autoTimeout: 0 }] : []);
            this.trainStep = ko.pureComputed(() => this.step());
            this.payPips = ko.pureComputed(() => {
                const reached = this.step() === "result" ? 2 : this.step() === "review" ? 1 : 0;
                return [0, 1, 2].map((index) => index <= reached);
            });
            this.lastPoll = 0;
            this.remaining = ko.pureComputed(() => (0, protection_1.remainingFor)(this.result(), this.now()));
            this.canCancel = ko.pureComputed(() => (0, protection_1.canCancelPayment)(this.result(), this.now()) && !this.busy() && !this.refreshing());
            this.resultTitle = ko.pureComputed(() => (0, protection_1.resultTitle)(this.result(), this.remaining()));
            this.countdown = ko.pureComputed(() => {
                const left = this.remaining();
                if (!Number.isFinite(left))
                    return "Expiry unavailable. Refresh for the latest status.";
                if (left > 0)
                    return `${(0, protection_1.formatCountdown)(left)} remaining`;
                return "Protection window ended. Waiting for the server’s final status.";
            });
            this.countdownClock = ko.pureComputed(() => (0, protection_1.formatCountdown)(this.remaining()) || "0:00");
            this.progressValue = ko.pureComputed(() => (0, protection_1.progressValue)(this.result(), this.now()));
            this.reasons = ko.pureComputed(() => { var _a; return (0, protection_1.explainReasons)((_a = this.result()) === null || _a === void 0 ? void 0 : _a.riskReason); });
            this.statusText = ko.pureComputed(() => { var _a; return (0, protection_1.statusLabel)(((_a = this.result()) === null || _a === void 0 ? void 0 : _a.state) || ""); });
            this.pauseLabel = ko.pureComputed(() => { var _a; return (0, protection_1.tierLabel)(((_a = this.result()) === null || _a === void 0 ? void 0 : _a.riskTier) || ""); });
            this.riskBadge = ko.pureComputed(() => { var _a; return (0, protection_1.tierRisk)(((_a = this.result()) === null || _a === void 0 ? void 0 : _a.riskTier) || ""); });
            this.amountSize = ko.pureComputed(() => Math.min(12, Math.max(1, this.amount().length)));
            this.isProtected = ko.pureComputed(() => { var _a; return ((_a = this.result()) === null || _a === void 0 ? void 0 : _a.state) === "PROTECTED"; });
            this.isSettling = ko.pureComputed(() => this.isProtected() && Number.isFinite(this.remaining()) && this.remaining() <= 0);
            this.isHold = ko.pureComputed(() => { var _a; return ((_a = this.result()) === null || _a === void 0 ? void 0 : _a.state) === "HARD_HOLD"; });
            this.needsCall = ko.pureComputed(() => (0, protection_1.needsVerification)(this.result()));
            this.isSettled = ko.pureComputed(() => { var _a; return ((_a = this.result()) === null || _a === void 0 ? void 0 : _a.state) === "SETTLED"; });
            this.isCancelled = ko.pureComputed(() => { var _a; return ((_a = this.result()) === null || _a === void 0 ? void 0 : _a.state) === "CANCELLED"; });
            this.showReasons = ko.pureComputed(() => {
                var _a;
                return this.reasons().length > 0
                    && !(this.isSettled() && !(((_a = this.result()) === null || _a === void 0 ? void 0 : _a.protectionSeconds) || 0));
            });
            this.riskTone = ko.pureComputed(() => this.result() ? (0, protection_1.riskClass)(this.result()) : "risk-neutral");
            this.fluxPhase = ko.pureComputed(() => this.isProtected()
                ? (0, protection_1.phaseFor)(this.progressValue(), protection_1.PROTECTION_PHASES)
                : (0, protection_1.phaseFor)(this.processing(), protection_1.CHECK_PHASES));
            this.fluxChars = ko.pureComputed(() => (0, protection_1.fluxLetters)(this.fluxPhase()));
            this.arcOffset = ko.pureComputed(() => (0, protection_1.arcOffset)(this.progressValue()));
            this.fluxPercent = ko.pureComputed(() => `${this.isProtected() ? this.progressValue() : this.processing()}%`);
            this.showProcessFlux = ko.pureComputed(() => this.busy() && this.attemptLocked() && !this.result());
            this.alive = true;
            this.canContinue = ko.pureComputed(() => { var _a; return !!this.selected() && ((_a = this.account()) === null || _a === void 0 ? void 0 : _a.status) === "ACTIVE" && !this.loading() && !this.busy() && !this.error(); });
            this.cannotContinue = ko.pureComputed(() => !this.canContinue());
            this.sheetOpen = ko.pureComputed(() => this.step() !== "beneficiary");
            this.canLeave = ko.pureComputed(() => this.step() === "result" && !!this.result() && !this.busy());
            this.sheetKind = ko.pureComputed(() => (0, protection_1.progressKind)(this.result(), this.showProcessFlux()));
            this.longTicks = ko.pureComputed(() => {
                const filled = this.progressValue();
                return Array.from({ length: 12 }, (_, index) => ((index + 1) * 100) / 12 <= filled + 0.5);
            });
            this.quickAmounts = ["500", "1000", "2000", "5000"];
            this.mask = (value) => value.length > 4 ? "•••• " + value.slice(-4) : "••••";
            this.money = (value) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
            this.amountPreview = ko.pureComputed(() => {
                const value = this.amount().trim();
                if (!/^\d{1,16}(\.\d{1,2})?$/.test(value))
                    return "";
                const parts = value.split(".");
                const integer = parts[0].replace(/^0+(?=\d)/, "");
                const tail = integer.slice(-3), head = integer.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ",");
                return "₹" + (head ? head + "," : "") + tail + "." + (parts[1] || "").padEnd(2, "0");
            });
            this.amountLong = ko.pureComputed(() => this.amount().trim().length > 8);
            this.amountIssue = ko.pureComputed(() => this.amount().trim() ? this.amountProblem() : "");
            this.amountMessage = ko.pureComputed(() => this.amountError() || this.amountIssue());
            this.amountReady = ko.pureComputed(() => !!this.amount().trim() && !this.amountProblem());
            this.isNew = (date) => { const age = Date.now() - new Date(date).getTime(); return age >= 0 && age < 86400000; };
            this.added = (date) => Number.isNaN(new Date(date).getTime()) ? "Added date unavailable" : "Added " + new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(date));
            this.initials = (name) => (name || "?").slice(0, 1).toUpperCase();
            this.load = () => __awaiter(this, void 0, void 0, function* () {
                if (this.loading() || this.attemptLocked() || this.result())
                    return;
                this.loading(true);
                this.error("");
                this.selected(null);
                this.beneficiaries([]);
                this.account(null);
                try {
                    const [beneficiaries, account] = yield Promise.all([beneficiaryService_1.beneficiaryService.list(), accountService_1.accountService.list()]);
                    if (!this.alive)
                        return;
                    this.allBeneficiaries = beneficiaries;
                    this.accounts(account);
                    const chosen = account.find(a => a.accountId === this.selectedAccountId()) || account.find(a => a.status === "ACTIVE") || account[0];
                    this.selectedAccountId((chosen === null || chosen === void 0 ? void 0 : chosen.accountId) || null);
                    this.selectAccount();
                    if (!chosen)
                        this.error("No account is linked to your profile yet.");
                }
                catch (error) {
                    if (!this.alive)
                        return;
                    if (error instanceof apiError_1.ApiError && error.status === 401) {
                        window.location.replace("/login?reason=session-expired");
                    }
                    this.error(error instanceof apiError_1.ApiError && [400, 401, 403, 404].includes(error.status) ? error.message : "We couldn’t load your payment details. Please try again.");
                }
                finally {
                    if (this.alive)
                        this.loading(false);
                }
            });
            this.selectAccount = () => {
                if (this.attemptLocked() || this.result())
                    return;
                const account = this.accounts().find(a => a.accountId === this.selectedAccountId());
                this.account(account || null);
                this.selected(null);
                this.beneficiaries(this.allBeneficiaries.filter(b => b.accountId === (account === null || account === void 0 ? void 0 : account.accountId) && b.status === "ACTIVE"));
            };
            this.choose = (beneficiary) => { if (!this.loading() && !this.busy())
                this.selected(beneficiary); };
            this.pick = (beneficiary) => {
                this.choose(beneficiary);
                void this.next();
            };
            this.useAmount = (value) => {
                if (this.busy())
                    return;
                this.amount(value);
                this.amountError("");
            };
            this.next = () => __awaiter(this, void 0, void 0, function* () {
                if (!this.canContinue())
                    return;
                this.busy(true);
                try {
                    yield this.context.router.go({ path: "send-money", params: { step: "details" } });
                }
                finally {
                    if (this.alive)
                        this.busy(false);
                }
            });
            this.back = () => __awaiter(this, void 0, void 0, function* () {
                if (this.canLeave()) {
                    yield this.leave();
                    return;
                }
                if (this.busy() || this.attemptLocked())
                    return;
                yield this.context.router.go(this.step() === "review"
                    ? { path: "send-money", params: { step: "details" } }
                    : this.step() === "details" ? { path: "send-money", params: { step: "beneficiary" } } : { path: "dashboard" });
            });
            this.leave = () => __awaiter(this, void 0, void 0, function* () {
                if (!this.canLeave())
                    return;
                this.stopPolling();
                this.result(null);
                this.attempt = undefined;
                this.attemptLocked(false);
                this.cancelKey = undefined;
                this.paymentError("");
                this.amount("");
                this.purpose("");
                this.selected(null);
                yield this.context.router.go({ path: "send-money", params: { step: "beneficiary" } });
            });
            this.continueDetails = () => __awaiter(this, void 0, void 0, function* () {
                var _a;
                if (this.busy() || this.loading())
                    return;
                this.notice("");
                const valid = this.validateDetails();
                if (!this.selected()) {
                    this.parametersChanged({ step: "beneficiary" });
                    return;
                }
                if (!valid) {
                    (_a = document.getElementById(this.amountError() ? "payment-amount" : "payment-purpose")) === null || _a === void 0 ? void 0 : _a.focus();
                    return;
                }
                if (!this.account() || this.error())
                    return;
                this.busy(true);
                try {
                    yield this.context.router.go({ path: "send-money", params: { step: "review" } });
                }
                finally {
                    if (this.alive)
                        this.busy(false);
                }
            });
            this.confirm = () => __awaiter(this, void 0, void 0, function* () {
                if (this.busy() || this.loading() || this.result() || this.step() !== "review")
                    return;
                this.paymentError("");
                if (!this.attempt) {
                    if (!this.validateDetails() || !this.selected() || !this.account() || this.account().status !== "ACTIVE" || this.selected().accountId !== this.account().accountId) {
                        this.paymentError(this.amountError() || this.purposeError() || "Choose a beneficiary and source account before paying.");
                        return;
                    }
                    if (new TextEncoder().encode(this.purpose()).length > 255) {
                        this.paymentError("Keep your note to 255 UTF-8 bytes or fewer.");
                        return;
                    }
                    this.attempt = { key: (0, transactionService_1.newIdempotencyKey)(), body: {
                            fromAccountId: this.account().accountId, beneficiaryId: this.selected().beneficiaryId,
                            amount: this.amount().trim(), purpose: this.purpose()
                        } };
                }
                this.busy(true);
                this.attemptLocked(true);
                this.startProcessFlux();
                (0, chime_1.armAudio)();
                try {
                    const response = yield transactionService_1.transactionService.create(this.attempt.body, this.attempt.key);
                    if (!this.alive)
                        return;
                    this.acceptResult(response);
                    yield this.context.router.go({ path: "send-money", params: { step: "result" } });
                    this.startPolling();
                }
                catch (error) {
                    if (this.alive) {
                        if (error instanceof apiError_1.ApiError && [400, 403, 404].includes(error.status)) {
                            this.attempt = undefined;
                            this.attemptLocked(false);
                        }
                        this.showPaymentError(error);
                    }
                }
                finally {
                    this.stopProcessFlux();
                    if (this.alive)
                        this.busy(false);
                }
            });
            this.refreshResult = () => __awaiter(this, void 0, void 0, function* () {
                const current = this.result();
                if (!current || this.refreshing() || this.busy())
                    return;
                this.refreshing(true);
                try {
                    const response = yield transactionService_1.transactionService.get(current.transactionId);
                    if (this.alive) {
                        this.acceptResult(response);
                        this.paymentError("");
                        if (!this.timer)
                            this.startPolling();
                    }
                }
                catch (error) {
                    if (this.alive) {
                        this.stopPolling();
                        this.showPaymentError(error);
                    }
                }
                finally {
                    if (this.alive)
                        this.refreshing(false);
                }
            });
            this.requestCancel = () => { if (this.canCancel()) {
                (0, chime_1.armAudio)();
                this.cancelOpen(true);
            } };
            this.closeCancel = () => { this.cancelOpen(false); };
            this.confirmCancel = () => __awaiter(this, void 0, void 0, function* () {
                (0, chime_1.armAudio)();
                this.cancelOpen(false);
                yield this.cancelPayment();
            });
            this.cancelPayment = () => __awaiter(this, void 0, void 0, function* () {
                if (!this.canCancel())
                    return;
                (0, chime_1.armAudio)();
                this.busy(true);
                this.paymentError("");
                try {
                    const current = yield transactionService_1.transactionService.get(this.result().transactionId);
                    if (!this.alive)
                        return;
                    this.acceptResult(current);
                    if (!(0, protection_1.canCancelPayment)(current, Date.now()))
                        return;
                    this.cancelKey || (this.cancelKey = (0, transactionService_1.newIdempotencyKey)());
                    const response = yield transactionService_1.transactionService.cancel(current.transactionId, this.cancelKey);
                    if (this.alive)
                        this.acceptResult(response);
                }
                catch (error) {
                    if (this.alive)
                        this.showPaymentError(error);
                }
                finally {
                    if (this.alive)
                        this.busy(false);
                }
            });
            this.initialStep = (_a = context.params) === null || _a === void 0 ? void 0 : _a.step;
        }
        parametersChanged(params) {
            if (this.result()) {
                this.step("result");
                if (params.step !== "result")
                    void this.context.router.go({ path: "send-money", params: { step: "result" } });
                return;
            }
            if (this.attemptLocked()) {
                this.step("review");
                if (params.step !== "review")
                    void this.context.router.go({ path: "send-money", params: { step: "review" } });
                return;
            }
            let target = "beneficiary";
            if (this.selected() && this.account()) {
                if (params.step === "details")
                    target = "details";
                if (params.step === "review")
                    target = this.validateDetails() ? "review" : "details";
            }
            this.step(target);
            if (params.step !== this.step())
                void this.context.router.go({ path: "send-money", params: { step: this.step() } });
            this.notice("");
        }
        amountProblem() {
            var _a;
            const value = this.amount().trim();
            if (!value)
                return "Enter an amount.";
            if (!/^\d{1,16}(\.\d{1,2})?$/.test(value) || !/[1-9]/.test(value)) {
                return "Enter a positive amount in rupees, digits only, up to 2 decimal places.";
            }
            const amount = Number(value);
            const balance = (_a = this.account()) === null || _a === void 0 ? void 0 : _a.balance;
            if (typeof balance === "number" && amount > balance) {
                return `That is more than the ${this.money(balance)} account balance.`;
            }
            return "";
        }
        validateDetails() {
            this.amountError(this.amountProblem());
            this.purposeError("");
            if (this.purpose().length > 255)
                this.purposeError("Keep your note to 255 characters or fewer.");
            return !this.amountError() && !this.purposeError();
        }
        showPaymentError(error) {
            if (error instanceof apiError_1.ApiError && error.status === 401) {
                this.result(null);
                this.stopPolling();
                window.location.replace("/login?reason=session-expired");
            }
            this.paymentError(error instanceof apiError_1.ApiError && [400, 403, 404, 409].includes(error.status)
                ? error.message : "We couldn’t confirm the payment status. Check Transactions before starting another payment. You can retry this unchanged request here safely.");
        }
        acceptResult(value) {
            if (!value || !Number.isSafeInteger(value.transactionId) || value.transactionId <= 0 || typeof value.state !== "string" || !Number.isFinite(value.amount)) {
                throw new apiError_1.ApiError(200, "Invalid payment response", "response");
            }
            this.result(value);
            this.now(Date.now());
            if (!(0, protection_1.isInFlight)(value.state))
                this.stopPolling();
            (0, chime_1.chimeForPayment)(value);
        }
        startProcessFlux() {
            this.stopProcessFlux();
            this.processing(8);
            this.processTimer = setInterval(() => {
                const next = this.processing() + 6;
                this.processing(next >= 88 ? 88 : next);
            }, 140);
        }
        stopProcessFlux() {
            if (this.processTimer)
                clearInterval(this.processTimer);
            this.processTimer = undefined;
            this.processing(this.result() ? 100 : 0);
        }
        startPolling() {
            var _a;
            this.stopPolling();
            this.lastPoll = Date.now();
            if (!(0, protection_1.isInFlight)((_a = this.result()) === null || _a === void 0 ? void 0 : _a.state))
                return;
            this.timer = setInterval(() => {
                this.now(Date.now());
                if (Date.now() - this.lastPoll >= 3000) {
                    this.lastPoll = Date.now();
                    void this.refreshResult();
                }
            }, 1000);
        }
        stopPolling() { if (this.timer)
            clearInterval(this.timer); this.timer = undefined; }
        connected() {
            document.title = "Send Money | SafePay";
            this.parametersChanged({ step: this.initialStep });
            void this.load();
        }
        disconnected() { this.alive = false; this.stopPolling(); this.stopProcessFlux(); this.result(null); this.attempt = undefined; this.selected(null); this.account(null); this.beneficiaries([]); this.amount(""); this.purpose(""); }
    }
    return SendMoneyViewModel;
});
//# sourceMappingURL=send-money.js.map