var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
define(["require", "exports", "knockout", "../services/adminUserService", "../services/transactionService", "../services/apiError"], function (require, exports, ko, adminUserService_1, transactionService_1, apiError_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.AdminUsersModel = void 0;
    class AdminUsersModel {
        constructor() {
            this.users = ko.observableArray([]);
            this.selected = ko.observable(null);
            this.accounts = ko.observableArray([]);
            this.selectedAccountId = ko.observable(null);
            this.accountLabel = (a) => a.accountType + " •••• " + a.accountNumber.slice(-4) + " · " + a.status;
            this.account = ko.observable(null);
            this.receipt = ko.observable(null);
            this.amount = ko.observable("");
            this.confirmed = ko.observable(false);
            this.pending = ko.observable(false);
            this.loading = ko.observable(false);
            this.busy = ko.observable(false);
            this.forbidden = ko.observable(false);
            this.error = ko.observable("");
            this.noAccount = ko.observable(false);
            this.generation = 0;
            this.money = (value) => {
                const [whole, fraction = ""] = value.split('.');
                return "₹" + BigInt(whole).toLocaleString("en-IN") + "." + fraction.padEnd(2, "0");
            };
            this.masked = (number) => "•••• " + number.slice(-4);
            this.load = () => __awaiter(this, void 0, void 0, function* () {
                if (this.loading() || this.pending())
                    return;
                const generation = ++this.generation;
                this.loading(true);
                this.error("");
                try {
                    const users = yield adminUserService_1.adminUserService.users();
                    if (generation === this.generation)
                        this.users(users);
                }
                catch (e) {
                    if (generation === this.generation)
                        this.fail(e);
                }
                finally {
                    if (generation === this.generation)
                        this.loading(false);
                }
            });
            this.select = (user) => __awaiter(this, void 0, void 0, function* () {
                var _a;
                if (this.pending() || this.busy())
                    return;
                const generation = ++this.generation;
                this.selected(user);
                this.account(null);
                this.receipt(null);
                this.noAccount(false);
                this.error("");
                this.amount("");
                this.confirmed(false);
                this.loading(true);
                try {
                    const accounts = yield adminUserService_1.adminUserService.accounts(user.userId);
                    if (generation === this.generation) {
                        this.accounts(accounts);
                        this.selectedAccountId(((_a = accounts[0]) === null || _a === void 0 ? void 0 : _a.accountId) || null);
                        this.selectAccount();
                        this.noAccount(!accounts.length);
                    }
                }
                catch (e) {
                    if (generation === this.generation) {
                        if (e instanceof apiError_1.ApiError && e.status === 404)
                            this.noAccount(true);
                        else
                            this.fail(e);
                    }
                }
                finally {
                    if (generation === this.generation)
                        this.loading(false);
                }
            });
            this.selectAccount = () => { if (this.pending() || this.busy())
                return; this.account(this.accounts().find(a => a.accountId === this.selectedAccountId()) || null); this.receipt(null); this.amount(""); this.confirmed(false); };
            this.retry = () => __awaiter(this, void 0, void 0, function* () { if (this.operation && !this.busy())
                yield this.sendCredit(); });
            this.submit = () => __awaiter(this, void 0, void 0, function* () {
                if (this.busy() || this.forbidden() || this.pending())
                    return;
                if (!this.account() || this.account().status !== "ACTIVE" || !(0, adminUserService_1.validCredit)(this.amount()) || !this.confirmed()) {
                    this.error("Select an ACTIVE account, enter a positive amount (up to two decimals), and confirm the credit.");
                    return;
                }
                this.operation = { id: this.account().accountId, amount: this.amount(), key: (0, transactionService_1.newIdempotencyKey)() };
                yield this.sendCredit();
            });
        }
        fail(e) {
            if (e instanceof apiError_1.ApiError && e.status === 401)
                window.location.replace("/admin/login");
            else if (e instanceof apiError_1.ApiError && e.status === 403) {
                this.forbidden(true);
                this.users([]);
                this.account(null);
            }
            else
                this.error(e instanceof apiError_1.ApiError && [400, 404, 409].includes(e.status) ? e.message : "Unable to confirm the request. Check the account and audit log before making another credit.");
        }
        sendCredit() {
            return __awaiter(this, void 0, void 0, function* () {
                const operation = this.operation;
                const generation = this.generation;
                this.pending(true);
                this.busy(true);
                this.error("");
                try {
                    const receipt = yield adminUserService_1.adminUserService.credit(operation.id, operation.amount, operation.key);
                    if (generation !== this.generation)
                        return;
                    this.receipt(receipt);
                    this.account(Object.assign(Object.assign({}, this.account()), { balance: receipt.balanceAfter }));
                    this.operation = undefined;
                    this.pending(false);
                    this.amount("");
                    this.confirmed(false);
                }
                catch (e) {
                    if (generation !== this.generation)
                        return;
                    if (e instanceof apiError_1.ApiError && [400, 404, 409].includes(e.status)) {
                        this.pending(false);
                        this.operation = undefined;
                    }
                    this.fail(e);
                }
                finally {
                    if (generation === this.generation)
                        this.busy(false);
                }
            });
        }
        ;
        disconnected() { this.generation++; this.users([]); this.selected(null); this.account(null); this.receipt(null); }
    }
    exports.AdminUsersModel = AdminUsersModel;
});
//# sourceMappingURL=adminUsers.js.map