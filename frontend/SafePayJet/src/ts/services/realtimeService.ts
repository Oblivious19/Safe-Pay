import { Client } from '@stomp/stompjs';
import { API_ORIGIN } from './apiClient';
import { session, token } from './authService';
import * as ko from 'knockout';
export const realtimeStatus = ko.observable('Live updates reconnecting; REST refresh remains available.');
export function startRealtime(): () => void {
  let client: Client | null = null, signal: ReturnType<typeof setTimeout> | undefined, disposed = false;
  const hint = () => { if (signal) clearTimeout(signal); signal = setTimeout(() => window.dispatchEvent(new Event('safepay:refresh')), 200); };
  const connect = () => {
    const old = client; client = null; if (old) void old.deactivate();
    const roles=session()?.authorities || []; const destinations:string[]=[];
    if(roles.includes('CUSTOMER')) destinations.push('/user/queue/notifications','/user/queue/transactions');
    if(roles.includes('RISK_OFFICER')) destinations.push('/user/queue/risk-reviews');
    if(disposed || !destinations.length)return;
    const next = new Client({brokerURL: API_ORIGIN.replace(/^http/, 'ws') + '/ws', reconnectDelay: 5000, heartbeatIncoming: 10000, heartbeatOutgoing: 10000, debug: () => {}});
    client = next;
    next.beforeConnect = async () => { try { next.connectHeaders = {Authorization: 'Bearer ' + await token()}; } catch { void next.deactivate(); } };
    next.onConnect = () => { if (client !== next || disposed) { void next.deactivate(); return; } realtimeStatus('Live updates connected'); destinations.forEach(destination => next.subscribe(destination, hint)); hint(); };
    next.onWebSocketClose = () => { if (client === next) realtimeStatus('Live updates offline; automatic REST refresh is active.'); };
    next.onStompError = () => { realtimeStatus('Live updates unavailable; use REST refresh.'); };
    next.activate();
  };
  const subscription = session.subscribe(connect); connect();
  return () => { disposed = true; subscription.dispose(); if (signal) clearTimeout(signal); if (client) void client.deactivate(); };
}
