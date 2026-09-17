var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
define(["require", "exports", "./apiClient", "./apiError"], function (require, exports, apiClient_1, apiError_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.adminHoldService = void 0;
    exports.adminHoldService = {
        list() {
            return __awaiter(this, void 0, void 0, function* () {
                apiClient_1.apiClient.useAdminCsrf(true);
                const rows = yield apiClient_1.apiClient.request("/api/admin/transactions/hard-holds");
                return rows.map(row => (Object.assign(Object.assign({}, row), { amount: Number(row.amount), customerEmail: "", riskTier: "VERY_HIGH", decision: "PENDING" })));
            });
        },
        approve(id, idempotencyKey) {
            (0, apiError_1.resourceId)(id);
            (0, apiError_1.requireText)(idempotencyKey, "Idempotency key");
            apiClient_1.apiClient.useAdminCsrf(true);
            return apiClient_1.apiClient.request(`/api/admin/transactions/${id}/approve`, { method: "POST", csrf: true, idempotencyKey });
        }
    };
});
//# sourceMappingURL=adminHoldService.js.map