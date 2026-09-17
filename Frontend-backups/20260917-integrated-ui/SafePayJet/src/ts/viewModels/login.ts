import * as ko from "knockout";
import { authService } from "../services/authService";
import { ApiError } from "../services/apiError";

class LoginViewModel {
  mode = ko.observable<"email" | "phone">("email");
  phone = ko.observable("");
  phoneError = ko.observable("");
  switchMode = (): void => {
    if (this.submitting()) return;
    this.mode(this.mode() === "email" ? "phone" : "email");
    this.password(""); this.showPassword(false); this.error("");
    this.emailError(""); this.phoneError(""); this.passwordError("");
  };
  email = ko.observable("");
  password = ko.observable("");
  showPassword = ko.observable(false);
  emailError = ko.observable("");
  passwordError = ko.observable("");
  error = ko.observable("");
  notice = ko.observable("");
  submitting = ko.observable(false);
  private generation = 0;

  togglePassword = (): void => { this.showPassword(!this.showPassword()); };

  login = async (): Promise<void> => {
    if (this.submitting()) return;
    this.emailError(""); this.phoneError(""); this.passwordError(""); this.error("");
    const email = this.email().trim();
    const phone = this.phone().trim();
    if (this.mode() === "phone") {
      if (!/^[0-9]{10}$/.test(phone)) this.phoneError("Enter your registered 10-digit mobile number");
    } else if (!email) this.emailError("Email is required");
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) this.emailError("Enter a valid email address");
    if (!this.password().trim()) this.passwordError("Password is required");
    if (this.emailError() || this.phoneError() || this.passwordError()) {
      document.getElementById(this.phoneError() ? "login-phone" : this.emailError() ? "login-email" : "login-password")?.focus();
      return;
    }
    const generation = ++this.generation;
    this.submitting(true);
    let navigating = false;
    try {
      // Preserve the password exactly; only email whitespace is normalised.
      const session = await authService.login(this.mode() === "phone" ? { phone, password: this.password() } : { email, password: this.password() });
      if (generation !== this.generation) return;
      this.password(""); this.showPassword(false);
      window.location.assign(session.role === "ADMIN" ? "/admin" : "/dashboard");
      navigating = true;
    } catch (error) {
      if (generation !== this.generation) return;
      this.error(error instanceof ApiError && error.status === 401 ? (this.mode() === "phone" ? "Invalid mobile number or password" : "Invalid email or password")
        : error instanceof ApiError && error.status === 403 ? error.message
        : "We couldn’t sign you in right now. Please try again shortly.");
    } finally {
      if (generation === this.generation) {
        this.password(""); this.showPassword(false);
        if (!navigating) this.submitting(false);
      }
    }
  };

  connected(): void {
    document.title = "Login | SafePay";
    if (new URLSearchParams(window.location.search).get("reason") === "session-expired") {
      this.error("Your session has expired or you’re not signed in. Please log in to continue.");
    }
    if (new URLSearchParams(window.location.search).get("registered") === "1") {
      this.notice("Your account has been created. Sign in to continue.");
    }
  }
  disconnected(): void { this.generation++; this.password(""); this.showPassword(false); this.submitting(false); }
}
export = LoginViewModel;
