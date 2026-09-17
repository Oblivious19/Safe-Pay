import { HeldPayment, PaymentTransaction } from "./types";
export declare const adminHoldService: {
    list(): Promise<HeldPayment[]>;
    approve(id: number, idempotencyKey: string): Promise<PaymentTransaction>;
};
