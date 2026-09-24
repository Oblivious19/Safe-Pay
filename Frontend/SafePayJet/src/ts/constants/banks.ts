/** Form-only simulation options. Not persisted and never added to an API payload. */
export enum Bank {
  AXIS = "AXIS",
  BANK_OF_BARODA = "BANK_OF_BARODA",
  BANK_OF_INDIA = "BANK_OF_INDIA",
  CANARA = "CANARA",
  CENTRAL_BANK_OF_INDIA = "CENTRAL_BANK_OF_INDIA",
  HDFC = "HDFC",
  ICICI = "ICICI",
  IDFC_FIRST = "IDFC_FIRST",
  INDIAN_BANK = "INDIAN_BANK",
  INDUSIND = "INDUSIND",
  KOTAK_MAHINDRA = "KOTAK_MAHINDRA",
  PUNJAB_NATIONAL = "PUNJAB_NATIONAL",
  STATE_BANK_OF_INDIA = "STATE_BANK_OF_INDIA",
  UNION_BANK_OF_INDIA = "UNION_BANK_OF_INDIA",
  YES_BANK = "YES_BANK"
}

export const BANK_OPTIONS: ReadonlyArray<{ value: Bank; label: string }> = [
  { value: Bank.AXIS, label: "Axis Bank" },
  { value: Bank.BANK_OF_BARODA, label: "Bank of Baroda" },
  { value: Bank.BANK_OF_INDIA, label: "Bank of India" },
  { value: Bank.CANARA, label: "Canara Bank" },
  { value: Bank.CENTRAL_BANK_OF_INDIA, label: "Central Bank of India" },
  { value: Bank.HDFC, label: "HDFC Bank" },
  { value: Bank.ICICI, label: "ICICI Bank" },
  { value: Bank.IDFC_FIRST, label: "IDFC FIRST Bank" },
  { value: Bank.INDIAN_BANK, label: "Indian Bank" },
  { value: Bank.INDUSIND, label: "IndusInd Bank" },
  { value: Bank.KOTAK_MAHINDRA, label: "Kotak Mahindra Bank" },
  { value: Bank.PUNJAB_NATIONAL, label: "Punjab National Bank" },
  { value: Bank.STATE_BANK_OF_INDIA, label: "State Bank of India" },
  { value: Bank.UNION_BANK_OF_INDIA, label: "Union Bank of India" },
  { value: Bank.YES_BANK, label: "Yes Bank" }
];
