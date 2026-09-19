import * as ko from "knockout";
import { authService } from "../services/authService";
import { ApiError } from "../services/apiError";
import { requestDashboardEntryLoader } from "../utils/entryLoader";
import "ojs/ojbutton";
import "ojs/ojinputtext";

class LoginViewModel {
  mode = ko.observable("phone");
  phone = ko.observable("");
  phoneError = ko.observable("");
  switchMode = (): void => {
    this.mode(this.mode() !== "email" ? "email" : "phone");
    this.password(""); this.error(""); this.showPassword(false);
  };
  email = ko.observable("");
  password = ko.observable("");
  showPassword = ko.observable(false);
  emailError = ko.observable("");
  passwordError = ko.observable("");
  error = ko.observable("");
  submitting = ko.observable(false);
  private generation = 0;

  togglePassword = (): void => { this.showPassword(!this.showPassword()); };

  login = async (): Promise<void> => {
    if (this.submitting()) return;
    this.emailError(""); this.passwordError(""); this.error("");
    this.phoneError("");
    const email = this.email().trim();
    if (this.mode() !== "email") {
      if (!/^[6-9][0-9]{9}$/.test(this.phone())) this.phoneError("Enter a valid 10-digit mobile number");
      if (this.mode() === "phone" && !this.password().trim()) this.passwordError("Password is required");
      if (this.phoneError()) {
        document.getElementById("login-phone")?.focus(); return;
      }
      if (this.passwordError()) { document.getElementById("login-password")?.focus(); return; }
    } else {
    if (!email) this.emailError("Email is required");
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) this.emailError("Enter a valid email address");
    if (!this.password().trim()) this.passwordError("Password is required");
    if (this.emailError() || this.passwordError()) {
      document.getElementById(this.emailError() ? "login-email" : "login-password")?.focus();
      return;
    }
    }
    const generation = ++this.generation;
    this.submitting(true);
    let navigating = false;
    try {
      // Preserve the password exactly; only email whitespace is normalised.
      const user = await authService.login(this.mode() === "phone" ? { phone: this.phone(), password: this.password() } : { email, password: this.password() });
      if (generation !== this.generation) return;
      this.password(""); this.showPassword(false);
      if (user.role !== "ADMIN") requestDashboardEntryLoader();
      window.location.assign(user.role === "ADMIN" ? "/admin/dashboard" : "/dashboard");
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
    if (new URLSearchParams(window.location.search).get("admin") === "1") {
      document.title = "Admin login | SafePay";
      this.error("Sign in with an administrator account to view reports.");
      return;
    }
    if (new URLSearchParams(window.location.search).get("reason") === "session-expired") {
      this.error("Your session has expired or you’re not signed in. Please log in to continue.");
    }
  }
  disconnected(): void { this.generation++; this.password(""); this.showPassword(false); this.submitting(false); }
}
export = LoginViewModel;
