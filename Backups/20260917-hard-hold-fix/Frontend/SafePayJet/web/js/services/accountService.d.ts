import { Account } from "./types";
export declare const accountService: {
    list(): Promise<Account[]>;
    getCurrent(accountId?: number): Promise<Account>;
};
