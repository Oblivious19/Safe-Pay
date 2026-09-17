var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
define(["require", "exports", "knockout", "../services/adminHoldService", "../services/apiError", "../services/transactionService", "../utils/protection"], function (require, exports, ko, adminHoldService_1, apiError_1, transactionService_1, protection_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.AdminHoldsModel = void 0;
    class AdminHoldsModel {
        constructor() {
            this.holds = ko.observableArray([]);
            this.selected = ko.observable(null);
            this.confirmed = ko.observable(false);
            this.loading = ko.observable(false);
            this.busy = ko.observable(false);
            this.forbidden = ko.observable(false);
            this.error = ko.observable("");
            this.notice = ko.observable("");
            this.updated = ko.observable("");
            this.generation = 0;
            this.alive = true;
            this.keys = new Map();
            this.pending = ko.pureComputed(() => this.holds());
            this.heldAmount = ko.pureComputed(() => this.holds().reduce((sum, row) => sum + row.amount, 0));
            this.selectedReasons = ko.pureComputed(() => { var _a; return (0, protection_1.explainReasons)((_a = this.selected()) === null || _a === void 0 ? void 0 : _a.riskReason); });
            this.money = (value) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(value);
            this.when = (value) => value ? new Date(value).toLocaleString("en-IN") : "—";
            this.masked = (value) => value ? "•••• " + value.slice(-4) : "—";
            this.load = () => __awaiter(this, void 0, void 0, function* () {
                if (this.loading() || this.busy())
                    return;
                const generation = ++this.generation;
                this.loading(true);
                this.error("");
                try {
                    const rows = yield adminHoldService_1.adminHoldService.list();
                    if (!this.alive || generation !== this.generation)
                        return;
                    this.holds(rows.slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
                    this.forbidden(false);
                    this.updated(new Date().toLocaleTimeString("en-IN"));
                    if (this.selected()) {
                        const previous = this.selected();
                        const current = rows.find(r => r.transactionId === previous.transactionId) || null;
                        this.selected(current);
                        if (!current || current.amount !== previous.amount)
                            this.confirmed(false);
                    }
                    if (!this.poll)
                        this.poll = setInterval(() => { if (!document.hidden)
                            void this.load(); }, 5000);
                }
                catch (e) {
                    if (this.alive && generation === this.generation) {
                        this.holds([]);
                        this.selected(null);
                        this.fail(e);
                    }
                }
                finally {
                    if (this.alive && generation === this.generation)
                        this.loading(false);
                }
            });
            this.select = (row) => { if (this.busy())
                return; this.selected(row); this.confirmed(false); this.error(""); this.notice(""); };
            this.clear = () => { if (!this.busy()) {
                this.selected(null);
                this.confirmed(false);
            } };
            this.approve = () => __awaiter(this, void 0, void 0, function* () {
                const row = this.selected();
                if (!row || !this.confirmed() || this.busy() || this.forbidden())
                    return;
                let key = this.keys.get(row.transactionId);
                if (!key) {
                    key = (0, transactionService_1.newIdempotencyKey)();
                    this.keys.set(row.transactionId, key);
                }
                const generation = this.generation;
                this.busy(true);
                this.error("");
                try {
                    yield adminHoldService_1.adminHoldService.approve(row.transactionId, key);
                    if (!this.alive || generation !== this.generation)
                        return;
                    this.holds(this.holds().filter(r => r.transactionId !== row.transactionId));
                    this.selected(null);
                    this.confirmed(false);
                    this.notice("Payment approved and settled by the backend.");
                }
                catch (e) {
                    if (this.alive && generation === this.generation)
                        this.fail(e);
                }
                finally {
                    if (this.alive)
                        this.busy(false);
                }
            });
        }
        fail(e) {
            if (e instanceof apiError_1.ApiError && e.status === 401) {
                window.location.replace("/admin/login");
                return;
            }
            if (e instanceof apiError_1.ApiError && e.status === 403)
                this.forbidden(true);
            this.error(e instanceof apiError_1.ApiError ? e.message : "Approval could not be confirmed. Refresh the queue before retrying the same request.");
        }
        disconnected() { this.alive = false; this.generation++; if (this.poll)
            clearInterval(this.poll); this.holds([]); this.selected(null); }
    }
    exports.AdminHoldsModel = AdminHoldsModel;
});
//# sourceMappingURL=adminHolds.js.map