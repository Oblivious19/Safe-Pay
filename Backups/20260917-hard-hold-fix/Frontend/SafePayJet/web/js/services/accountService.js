define(["require", "exports", "./apiClient", "./apiError"], function (require, exports, apiClient_1, apiError_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.accountService = void 0;
    exports.accountService = {
        list() { return apiClient_1.apiClient.request("/api/accounts"); },
        getCurrent(accountId) {
            return apiClient_1.apiClient.request(`/api/accounts/current${accountId === undefined ? "" : "?accountId=" + (0, apiError_1.resourceId)(accountId)}`);
        }
    };
});
//# sourceMappingURL=accountService.js.map