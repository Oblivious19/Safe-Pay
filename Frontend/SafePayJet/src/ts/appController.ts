import * as ko from "knockout";
import CoreRouter = require("ojs/ojcorerouter");
import ModuleRouterAdapter = require("ojs/ojmodulerouter-adapter");
import KnockoutRouterAdapter = require("ojs/ojknockoutrouteradapter");
import UrlPathAdapter = require("ojs/ojurlpathparamadapter");
import { authService } from "./services/authService";
import { profileService, CustomerIdentity } from "./services/profileService";
import { checkSessionRoute } from "./services/sessionRouteService";
import Context = require("ojs/ojcontext");
import { armAudio } from "./utils/chime";
import "ojs/ojknockout";
import "ojs/ojmodule-element";
import "./utils/uiAttention";

interface RouteDetail { label: string; }
class RootViewModel {
  manner = ko.observable("polite");
  message = ko.observable<string>();
  copyrightYear = new Date().getFullYear();
  footerSection = ko.observable<"about" | "how">("about");
  showFooterInfo = (section: "about" | "how"): void => {
    this.footerSection(section);
    (document.getElementById("footer-info") as HTMLDialogElement | null)?.showModal();
  };
  loggingOut = ko.observable(false);
  logoutError = ko.observable("");
  profile = ko.observable<CustomerIdentity | null>(null);
  profileLoading = ko.observable(false);
  // Keep the thin bar for ordinary in-app navigation.
  routeBusy = ko.observable(true);
  // Full-page landing/dashboard entry (including login handoff), not feature tabs.
  entryLoaderBusy = ko.observable(false);
  private profileGeneration = 0;
  private consumeDashboardEntryLoader = (): boolean => {
    try {
      if (window.sessionStorage?.getItem("safepay-dashboard-entry-loader") !== "1") return false;
      window.sessionStorage.removeItem("safepay-dashboard-entry-loader");
      return true;
    } catch {
      return false;
    }
  };
  loadProfile = async (): Promise<void> => {
    const generation = ++this.profileGeneration;
    this.profile(null); this.profileLoading(true);
    try {
      const profile = await profileService.getCurrent();
      if (generation === this.profileGeneration) this.profile(profile);
    } catch {
      // Never show a cached identity when the current session cannot be verified.
      if (generation === this.profileGeneration) this.profile(null);
    } finally {
      if (generation === this.profileGeneration) this.profileLoading(false);
    }
  };
  logout = async (): Promise<void> => {
    if (this.loggingOut()) return;
    this.loggingOut(true); this.logoutError("");
    try {
      await authService.logout();
      this.profileGeneration++; this.profile(null);
      window.location.replace("/login");
    } catch {
      this.logoutError("We couldn’t sign you out. Please try again.");
      this.loggingOut(false);
    }
  };
  navItems = [
    { path: "dashboard", label: "Dashboard", iconClass: "oj-ux-ico-home customer-nav-icon" },
    { path: "send-money", label: "Send Money", iconClass: "oj-ux-ico-send customer-nav-icon" },
    { path: "beneficiaries", label: "Beneficiaries", iconClass: "oj-ux-ico-contact-group customer-nav-icon" },
    { path: "transactions", label: "Transactions", iconClass: "oj-ux-ico-list customer-nav-icon" },
    { path: "profile", label: "Profile", iconClass: "oj-ux-ico-contact customer-nav-icon" }
  ];
  moduleAdapter: ModuleRouterAdapter<RouteDetail>;
  selection: KnockoutRouterAdapter<RouteDetail>;
  constructor() {
    // Keep existing saved JET links working during the clean-route transition.
    if(window.location.pathname === "/admin" || window.location.pathname === "/admin/") window.history.replaceState(null,"","/admin/dashboard");
    const legacy = new URLSearchParams(window.location.search).get("ojr");
    if (legacy && ["/login", "/dashboard", ...this.navItems.map(item => "/" + item.path)].includes(legacy)) {
      window.history.replaceState(null, "", legacy);
    }
    document.getElementById("globalBody")!.addEventListener("announce", ((event: CustomEvent) => {
      this.message(event.detail.message); this.manner(event.detail.manner);
    }) as EventListener);
    const router = new CoreRouter([{ path: "", redirect: "home" },
      { path: "home", detail: { label: "Welcome to SafePay" } },
      { path: "login", detail: { label: "Login" } },
      { path: "admin/{page}", detail: { label: "Administration" } },
      { path: "register/{step}", detail: { label: "Create account" } },
      ...this.navItems.map(item => ({ path: item.path === "send-money" ? "send-money/{step}" : item.path, detail: { label: item.label } }))],
      { urlAdapter: new UrlPathAdapter("/") });
    this.moduleAdapter = new ModuleRouterAdapter(router);
    this.selection = new KnockoutRouterAdapter(router);
    router.beforeStateChange.subscribe(() => this.routeBusy(true));
    const entryStarted = Date.now();
    let entryTimer: ReturnType<typeof setTimeout> | undefined;
    const stopEntryLoader = (): void => {
      const finish = (): void => {
        this.entryLoaderBusy(false);
        document.documentElement?.removeAttribute("data-safepay-entry-loader");
      };
      if (!this.entryLoaderBusy()) { finish(); return; }
      if (entryTimer !== undefined) return;
      const remaining = Math.max(0, 2500 - (Date.now() - entryStarted));
      if (remaining) entryTimer = setTimeout(finish, remaining);
      else finish();
    };
    router.currentState.subscribe(update => {
      // JET BehaviorSubject immediately emits {complete} without a state.
      // That is not a completed route: hiding bootstrap here caused the startup flash.
      if (!update.state) return;
      this.routeBusy(false);
      stopEntryLoader();
    });
    // State changes normally settle both indicators above. This fallback also
    // clears the bootstrap scene if a router update rejects before it
    // publishes a current state, so it can never block a feature page.
    const syncRoute = (): void => {
      void router.sync()
        .catch(() => undefined)
        .finally(() => {
          this.routeBusy(false);
          stopEntryLoader();
        });
    };
    // Do not instantiate private customer modules before session verification finishes.
    const path = window.location.pathname || "/login";
    const customerPage = this.navItems.some(item => path === "/" + item.path || path.startsWith("/" + item.path + "/"));
    const publicPage = path === "/" || path === "/home";
    const dashboardEntry = path === "/dashboard" || path === "/dashboard/";
    if (dashboardEntry) this.consumeDashboardEntryLoader();
    this.entryLoaderBusy(publicPage || dashboardEntry);
    if (publicPage) {
      // Resolve the first destination once, beneath the same entry scene.
      // A full browser redirect here replayed bootstrap and the logo a second time.
      void checkSessionRoute().then(session => {
        if (session.kind === "customer") {
          this.profile(session.profile);
          window.history.replaceState(null, "", "/dashboard");
        } else if (session.kind === "admin") {
          window.history.replaceState(null, "", "/admin/dashboard");
        }
        syncRoute();
      }).catch(() => syncRoute());
    } else if (customerPage) {
      void checkSessionRoute().then(session => {
        if (session.kind === "admin") { window.location.replace("/admin/dashboard"); return; }
        if (session.kind === "customer") {
          this.profile(session.profile);
        } else {
          window.location.replace(session.kind === "guest" ? "/home" : "/home?reason=unavailable"); return;
        }
        syncRoute();
      });
    } else syncRoute();
    Context.getPageContext().getBusyContext().applicationBootstrapComplete();
    // First tap anywhere unlocks Web Audio so later receipt/call tones are allowed.
    const unlock = (): void => { armAudio(); };
    window.addEventListener("pointerdown", unlock, true);
    window.addEventListener("keydown", unlock, true);
  }
}
export default new RootViewModel();
