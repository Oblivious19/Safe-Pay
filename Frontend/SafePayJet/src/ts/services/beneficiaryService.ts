import { apiClient } from "./apiClient";
import { ApiError, resourceId } from "./apiError";

export interface Beneficiary {
  beneficiaryId: number; beneficiaryName: string; bankAccountNumber: string;
  ifsc: string; bankName: string; status: string; createdAt: string; accountId: number;
}
export interface BeneficiaryInput { accountId?: number; beneficiaryName: string; bankAccountNumber: string; ifsc: string; externalConfirmed?: boolean; }
export function validateBeneficiary(input: BeneficiaryInput): Partial<Record<keyof BeneficiaryInput, string>> {
  const errors: Partial<Record<keyof BeneficiaryInput, string>> = {};
  if (!input.beneficiaryName.trim() || input.beneficiaryName.trim().length > 100)
    errors.beneficiaryName = "Enter a name between 1 and 100 characters.";
  if (!/^[0-9]{1,30}$/.test(input.bankAccountNumber.trim()))
    errors.bankAccountNumber = "Enter a bank account number with 1–30 digits.";
  if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(input.ifsc.trim().toUpperCase()))
    errors.ifsc = "Invalid IFSC code.";
  return errors;
}
function path(id: number): string {
  if (!Number.isSafeInteger(id) || id <= 0) throw new ApiError(400, "Select a beneficiary.", "validation");
  return "/api/beneficiaries/" + id;
}
export const beneficiaryService = {
  list(accountId?: number, includeInactive = false): Promise<Beneficiary[]> {
    const query = new URLSearchParams();
    if(accountId !== undefined) query.set("accountId", String(resourceId(accountId)));
    if(includeInactive) query.set("includeInactive", "true");
    return apiClient.request("/api/beneficiaries" + (query.size ? "?"+query.toString() : ""));
  },
  get(id: number): Promise<Beneficiary> {
    return apiClient.request(path(id));
  },
  create(input: BeneficiaryInput): Promise<Beneficiary> {
    const errors = validateBeneficiary(input);
    if (Object.keys(errors).length) throw new ApiError(400, Object.values(errors)[0]!, "validation");
    const body = { ...(input.accountId !== undefined ? {accountId: resourceId(input.accountId)} : {}), beneficiaryName: input.beneficiaryName.trim(), bankAccountNumber: input.bankAccountNumber.trim(), ifsc: input.ifsc.trim().toUpperCase(), externalConfirmed: input.externalConfirmed === true };
    return apiClient.request("/api/beneficiaries", { method: "POST", csrf: true, body });
  },
  deactivate(id: number): Promise<void> {
    return apiClient.request(path(id), { method: "DELETE", csrf: true });
  },
  reactivate(id: number): Promise<Beneficiary> { return apiClient.request(path(id)+"/status",{method:"PATCH",csrf:true,body:{status:"ACTIVE"}}); }
};
