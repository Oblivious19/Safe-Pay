export interface AdminUser {
    userId: number;
    name: string;
    email: string;
    phone: string;
    role: string;
    status: string;
}
export interface CreditAccount {
    accountId: number;
    accountNumber: string;
    accountType: string;
    status: string;
    balance: string;
}
export interface CreditReceipt {
    accountId: number;
    amount: string;
    balanceBefore: string;
    balanceAfter: string;
    createdAt: string;
    description: string;
}
export declare function validCredit(amount: string): boolean;
export declare const adminUserService: {
    users: () => Promise<AdminUser[]>;
    accounts: (id: number) => Promise<CreditAccount[]>;
    credit: (id: number, amount: string, idempotencyKey: string) => Promise<CreditReceipt>;
};
