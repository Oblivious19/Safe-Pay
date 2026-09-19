/* A short-lived visual handoff marker only. It never stores credentials,
   account data, or payment details. */
const DASHBOARD_ENTRY_LOADER_KEY = "safepay-dashboard-entry-loader";

export const requestDashboardEntryLoader = (): void => {
  try {
    window.sessionStorage?.setItem(DASHBOARD_ENTRY_LOADER_KEY, "1");
  } catch {
    // Browsers that block storage simply skip this optional animation.
  }
};
