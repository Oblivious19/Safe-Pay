import * as ko from "knockout";
import app from "../appController";
import * as AccUtils from "../accUtils";
import { users } from "../services/api";
import { CustomerProfile } from "../services/types";
import { endExpiredSession } from "../services/customerSession";

class ProfileViewModel {
  name = ko.observable("");
  email = ko.observable("");
  phone = ko.observable("");
  password = ko.observable("");
  status = ko.observable("");
  loading = ko.observable(false);
  saving = ko.observable(false);
  error = ko.observable("");
  success = ko.observable("");
  private active = false;
  private revision = 0;
  private expired(error: unknown): boolean {
    return endExpiredSession(error, () => {
      this.active = false; this.revision++;
      this.name(""); this.email(""); this.phone(""); this.password(""); this.status("");
      this.error("Your session has expired. Please sign in again.");
    });
  }
  private show(profile: CustomerProfile): void {
    this.name(profile.name); this.email(profile.email); this.phone(profile.phone || ""); this.status(profile.status);
  }
  load = async (): Promise<void> => {
    const revision = ++this.revision;
    this.loading(true); this.error("");
    try {
      const profile = await users.getCurrent();
      if (this.active && revision === this.revision) this.show(profile);
    } catch (error) {
      if (this.active && revision === this.revision && !this.expired(error)) this.error(error instanceof Error ? error.message : "Could not load your profile.");
    } finally { if (this.active && revision === this.revision) this.loading(false); }
  };
  save = async (): Promise<void> => {
    if (this.saving() || this.loading()) return;
    const revision = ++this.revision;
    this.saving(true); this.error(""); this.success("");
    const password = this.password(); this.password("");
    try {
      const profile = await users.updateCurrent({ name: this.name().trim(), email: this.email().trim(),
        phone: this.phone().trim(), ...(password ? { password } : {}) });
      if (!this.active || revision !== this.revision) return;
      this.show(profile); this.success("Your profile has been updated.");
      await app.loadProfile();
    } catch (error) {
      if (this.active && revision === this.revision && !this.expired(error)) this.error(error instanceof Error ? error.message : "Could not update your profile.");
    } finally { if (this.active && revision === this.revision) this.saving(false); }
  };
  connected(): void {
    this.active = true; this.saving(false); AccUtils.announce("Profile page loaded."); document.title = "My Profile | SafePay"; void this.load();
  }
  disconnected(): void { this.active = false; this.revision++; this.password(""); }
}
export = ProfileViewModel;
