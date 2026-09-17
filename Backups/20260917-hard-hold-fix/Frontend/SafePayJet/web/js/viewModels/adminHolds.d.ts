import * as ko from "knockout";
import { HeldPayment } from "../services/types";
export declare class AdminHoldsModel {
    holds: ko.ObservableArray<HeldPayment>;
    selected: ko.Observable<HeldPayment | null>;
    confirmed: ko.Observable<boolean>;
    loading: ko.Observable<boolean>;
    busy: ko.Observable<boolean>;
    forbidden: ko.Observable<boolean>;
    error: ko.Observable<string>;
    notice: ko.Observable<string>;
    updated: ko.Observable<string>;
    private generation;
    private alive;
    private keys;
    private poll?;
    pending: ko.PureComputed<HeldPayment[]>;
    heldAmount: ko.PureComputed<number>;
    selectedReasons: ko.PureComputed<string[]>;
    money: (value: number) => string;
    when: (value: string) => string;
    masked: (value: string) => string;
    load: () => Promise<void>;
    select: (row: HeldPayment) => void;
    clear: () => void;
    approve: () => Promise<void>;
    private fail;
    disconnected(): void;
}
