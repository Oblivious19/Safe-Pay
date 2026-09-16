export interface BeneficiaryInput { beneficiaryName: string; bankAccountNumber: string; ifsc: string; }
export function validateBeneficiary(input: BeneficiaryInput): Partial<Record<keyof BeneficiaryInput, string>> {
  const errors: Partial<Record<keyof BeneficiaryInput, string>> = {};
  if (!input.beneficiaryName.trim() || input.beneficiaryName.trim().length > 100)
    errors.beneficiaryName = "Enter a name between 1 and 100 characters.";
  if (!/^[0-9]{1,30}$/.test(input.bankAccountNumber.trim()))
    errors.bankAccountNumber = "Enter a bank account number with 1–30 digits.";
  if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(input.ifsc.trim().toUpperCase()))
    errors.ifsc = "Enter an 11-character IFSC: four letters, 0, then six letters or digits.";
  return errors;
}
