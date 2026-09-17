import * as ko from "knockout";
import CoreRouter = require("ojs/ojcorerouter");
import ModuleRouterAdapter = require("ojs/ojmodulerouter-adapter");
import KnockoutRouterAdapter = require("ojs/ojknockoutrouteradapter");
import UrlPathAdapter = require("ojs/ojurlpathadapter");
import { authService } from "./services/authService";
import { CustomerIdentity } from "./services/profileService";
import { sessionService } from "./services/sessionService";
import { ApiError } from "./services/apiError";
import { UserRole } from "./services/types";
import Context = require("ojs/ojcontext");
import "ojs/ojknockout";
import "ojs/ojmodule-element";

interface RouteDetail { label: string; }
class RootViewModel {
  manner = ko.observable("polite");
  message = ko.observable<string>();
  loggingOut = ko.observable(false);
  logoutError = ko.observable("");
  profile = ko.observable<CustomerIdentity | null>(null);
  profileLoading = ko.observable(false);
  sessionRole = ko.observable<UserRole | null>(null);
  sessionError = ko.observable("");
  private profileGeneration = 0;
  loadProfile = async (): Promise<void> => {
    const generation = ++this.profileGeneration;
    this.profile(null); this.sessionRole(null); this.sessionError(""); this.profileLoading(true);
    try {
      const session = await sessionService.restore(this.selection.path() === "admin");
      if (generation !== this.profileGeneration) return;
      this.profile(session.profile); this.sessionRole(session.role);
      const path = this.selection.path();
      if (session.role === "ADMIN" && path !== "admin" && !["login", "register"].includes(path)) window.location.replace("/admin");
      else if (session.role === "CUSTOMER" && path === "admin") window.location.replace("/dashboard");
    } catch (error) {
      // Never show a cached identity when the current session cannot be verified.
      if (generation === this.profileGeneration) {
        this.profile(null); this.sessionRole(null);
        if (error instanceof ApiError && error.status === 401) window.location.replace("/login?reason=session-expired");
        else this.sessionError("We couldn’t verify your session. Refresh the page to try again.");
      }
    } finally {
      if (generation === this.profileGeneration) this.profileLoading(false);
    }
  };
  logout = async (): Promise<void> => {
    if (this.loggingOut()) return;
    this.loggingOut(true); this.logoutError("");
    try {
      await authService.logout();
      this.profileGeneration++; this.profile(null); this.sessionRole(null);
      window.location.replace("/login");
    } catch {
      this.logoutError("We couldn’t sign you out. Please try again.");
      this.loggingOut(false);
    }
  };
  customerNavItems = [
    { path: "dashboard", label: "Dashboard", icon: "⌂" },
    { path: "send-money", label: "Send Money", icon: "↗" },
    { path: "beneficiaries", label: "Beneficiaries", icon: "♧" },
    { path: "transactions", label: "Transactions", icon: "≡" },
    { path: "profile", label: "Profile", icon: "○" }
  ];
  navItems = ko.pureComputed(() => this.sessionRole() === "ADMIN"
    ? [{ path: "admin", label: "Administration", icon: "▦" }] : this.customerNavItems);
  moduleAdapter: ModuleRouterAdapter<RouteDetail>;
  selection: KnockoutRouterAdapter<RouteDetail>;
  constructor() {
    // Keep existing saved JET links working during the clean-route transition.
    const legacy = new URLSearchParams(window.location.search).get("ojr");
    if (legacy && ["/login", "/register", "/admin", ...this.customerNavItems.map(item => "/" + item.path)].includes(legacy)) {
      window.history.replaceState(null, "", legacy);
    }
    document.getElementById("globalBody")!.addEventListener("announce", ((event: CustomEvent) => {
      this.message(event.detail.message); this.manner(event.detail.manner);
    }) as EventListener);
    const router = new CoreRouter([{ path: "", redirect: "login" },
      { path: "login", detail: { label: "Login" } },
      { path: "register", detail: { label: "Create account" } },
      { path: "admin", detail: { label: "Administration" } },
      ...this.customerNavItems.map(item => ({ path: item.path, detail: { label: item.label } }))],
      { urlAdapter: new UrlPathAdapter("/") });
    this.moduleAdapter = new ModuleRouterAdapter(router);
    this.selection = new KnockoutRouterAdapter(router);
    void router.sync().then(() => {
      if (!["login", "register"].includes(this.selection.path())) void this.loadProfile();
    });
    Context.getPageContext().getBusyContext().applicationBootstrapComplete();
  }
}
export default new RootViewModel();
