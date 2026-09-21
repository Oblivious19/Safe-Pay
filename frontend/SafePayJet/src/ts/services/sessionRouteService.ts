import { session } from './authService';
export const publicRoutes = ['home','about','login','register'];
export const customerRoutes = ['dashboard','profile','beneficiaries','send-money','transactions','notifications'];
export function mayOpen(route: string): boolean {
  const root = route.split('/')[0], roles = session()?.authorities || [];
  return publicRoutes.includes(root) || (customerRoutes.includes(root) && roles.includes('CUSTOMER')) ||
    (root === 'admin' && roles.includes('SYSTEM_ADMIN')) || (root === 'risk' && roles.includes('RISK_OFFICER')) || (root === 'audit' && roles.includes('AUDITOR'));
}
export function landing(): string { const roles = session()?.authorities || []; return roles.includes('CUSTOMER') ? 'dashboard' : roles.includes('SYSTEM_ADMIN') ? 'admin/dashboard' : roles.includes('RISK_OFFICER') ? 'risk/reviews' : roles.includes('AUDITOR') ? 'audit/logs' : 'login'; }
let routeHandler: ((path: string, params: Record<string,string>) => void) | undefined;
export function configureNavigation(handler: typeof routeHandler): void { routeHandler = handler; }
export function navigate(route: string, params: Record<string,string> = {}): void {
  if (!mayOpen(route)) throw new Error('This workspace is not available to your current roles.');
  if (routeHandler) routeHandler(route, params);
  else location.assign('/' + route + (Object.keys(params).length ? '?' + new URLSearchParams(params).toString() : ''));
}
