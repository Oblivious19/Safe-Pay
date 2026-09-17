export type UserRole = "CUSTOMER" | "ADMIN";
export type UserStatus = "ACTIVE" | "LOCKED" | "SUSPENDED" | "INACTIVE";
export type AccountStatus = "ACTIVE" | "BLOCKED" | "CLOSED";
export type AccountType = "SAVINGS" | "CURRENT";
// The current HTTP/JPA contract still includes legacy values, not future states.
export type TransactionState = "CREATED" | "AUTHORIZED" | "RISK_ASSESSED" | "PROTECTED" | "HARD_HOLD" | "CANCELLED" | "SETTLED";
export type RiskTier = "LOW" | "MEDIUM" | "HIGH" | "VERY_HIGH" | "HARD_HOLD";
export interface LoginRequest { email: string; password: string; }
export interface PhonePasswordRequest { phone: string; password: string; }
export interface RegistrationRequest extends LoginRequest { name: string; phone: string; }
export interface LoginResponse { userId: number; name: string; email: string; role: UserRole; status: UserStatus; }
export interface MessageResponse { message: string; }
export interface RegistrationResponse extends MessageResponse { userId: number; email: string; }
export interface CustomerProfile {
  userId: number; name: string; email: string; phone: string;
  status: UserStatus; createdAt: string; updatedAt: string;
}
export interface ProfileUpdate { name: string; email: string; phone: string; password?: string; }
export interface Account {
  accountId: number; userId: number; accountNumber: string; accountType: AccountType;
  balance: number; status: AccountStatus; createdAt: string; updatedAt: string;
}
export interface TransactionRequest {
  fromAccountId: number; beneficiaryId: number;
  /** Decimal text is sent unchanged to Jackson BigDecimal; do not do money arithmetic in JS. */
  amount: string; purpose?: string | null;
}
export interface PaymentTransaction {
  transactionId: number; transactionRef: string; amount: number; purpose: string;
  fromAccountId: number; beneficiaryId: number; beneficiaryName: string;
  beneficiaryBankAccountNumber: string; beneficiaryIfsc: string;
  state: TransactionState; riskTier: RiskTier; riskReason: string;
  protectionSeconds: number; protectionExpiresAt: string; createdAt: string;
  settledAt: string; cancelledAt: string; verifiedAt?: string;
  protectionRemainingMillis?: number | null; canCancel?: boolean;
}
export interface TransactionSummary {
  totalTransactions: number; settledTransactions: number; protectedTransactions: number;
  cancelledTransactions: number; rejectedTransactions: number; hardHolds: number;
  highRiskTransactions: number; totalAmount: number; settledAmount: number;
}
export interface DailyTransactionSummary { date: string; summary: TransactionSummary; }
