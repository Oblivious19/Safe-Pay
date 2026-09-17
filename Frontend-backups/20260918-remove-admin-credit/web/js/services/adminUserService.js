define(["require", "exports", "./apiClient", "./apiError"], function (require, exports, apiClient_1, apiError_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.adminUserService = void 0;
    exports.validCredit = validCredit;
    function validCredit(amount) { return /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,2})?$/.test(amount) && /[1-9]/.test(amount); }
    exports.adminUserService = {
        users: () => { apiClient_1.apiClient.useAdminCsrf(true); return apiClient_1.apiClient.request("/api/admin/users"); },
        accounts: (id) => { apiClient_1.apiClient.useAdminCsrf(true); return apiClient_1.apiClient.request(`/api/admin/users/${(0, apiError_1.resourceId)(id)}/accounts`); },
        credit: (id, amount, idempotencyKey) => {
            (0, apiError_1.resourceId)(id);
            (0, apiError_1.requireText)(idempotencyKey, "Idempotency key");
            if (!validCredit(amount))
                return Promise.reject(new apiError_1.ApiError(400, "Enter a positive amount with at most two decimals."));
            apiClient_1.apiClient.useAdminCsrf(true);
            return apiClient_1.apiClient.request(`/api/admin/accounts/${id}/interest-credits`, { method: "POST", csrf: true, idempotencyKey, body: { amount } });
        }
    };
});
//# sourceMappingURL=adminUserService.js.map