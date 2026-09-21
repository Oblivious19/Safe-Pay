/** Local demonstration only: not an identity or ownership security boundary. */
export const demoOtpConfig = { code: "482619", lifetimeMs: 180000, resendMs: 30000, maxSends: 3 };
export class DemoOtpProvider {
  private expires = 0;
  private nextSend = 0;
  private sends = 0;
  private attempts = 0;
  constructor(private now = () => Date.now(), private config = demoOtpConfig) {}
  send(): void {
    if (this.sends >= this.config.maxSends) throw new Error("Demo resend limit reached. Start again later.");
    if (this.now() < this.nextSend) throw new Error("Please wait 30 seconds before resending.");
    this.sends++; this.attempts = 0;
    this.expires = this.now() + this.config.lifetimeMs;
    this.nextSend = this.now() + this.config.resendMs;
  }
  verify(code: string): void {
    if (!this.expires || this.now() >= this.expires) throw new Error("Demo code expired. Request another code.");
    if (++this.attempts > 5) throw new Error("Too many demo attempts. Request another code.");
    if (!/^[0-9]{6}$/.test(code) || code !== this.config.code) throw new Error("Invalid demo code. Please try again.");
    this.expires = 0;
  }
  clear(): void { this.expires = 0; }
}
export class DemoKycProvider {
  verify(pan: string): string {
    if (!/^[A-Z]{5}[0-9]{4}[A-Z]$/.test(pan)) throw new Error("Enter a PAN with 5 letters, 4 digits and 1 letter.");
    return "KYC Verified (Simulated)";
  }
}
