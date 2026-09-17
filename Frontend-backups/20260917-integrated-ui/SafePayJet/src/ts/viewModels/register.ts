import * as ko from "knockout";
import { authService } from "../services/authService";
import { ApiError } from "../services/apiError";

class RegisterViewModel {
  name = ko.observable("");
  email = ko.observable("");
  phone = ko.observable("");
  password = ko.observable("");
  confirmPassword = ko.observable("");
  error = ko.observable("");
  submitting = ko.observable(false);
  private generation = 0;

  register = async (): Promise<void> => {
    if (this.submitting()) return;
    this.error("");
    const name = this.name().trim(), email = this.email().trim(), phone = this.phone().trim();
    const password = this.password();
    if (!name || name.length > 100) this.error("Enter your name using 1 to 100 characters.");
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 150) this.error("Enter a valid email address.");
    else if (!/^[6-9][0-9]{9}$/.test(phone)) this.error("Enter a 10-digit Indian mobile number.");
    else if (!password.trim() || new TextEncoder().encode(password).length > 72) this.error("Enter a password of up to 72 UTF-8 bytes.");
    else if (password !== this.confirmPassword()) this.error("Passwords must match.");
    if (this.error()) return;
    const generation = ++this.generation;
    this.submitting(true);
    let navigating = false;
    try {
      await authService.register({ name, email, phone, password });
      if (generation !== this.generation) return;
      window.location.assign("/login?registered=1");
      navigating = true;
    } catch (error) {
      if (generation !== this.generation) return;
      this.error(error instanceof ApiError && error.status >= 400 && error.status < 500
        ? error.message : "We couldn’t create your account. Please try again.");
    } finally {
      if (generation === this.generation) {
        this.password(""); this.confirmPassword("");
        if (!navigating) this.submitting(false);
      }
    }
  };
  connected(): void { document.title = "Create account | SafePay"; }
  disconnected(): void { this.generation++; this.password(""); this.confirmPassword(""); this.submitting(false); }
}
export = RegisterViewModel;
