/** Serial reads only; financial commands never enter this loop. */
export function refreshLoop(read: () => Promise<void>, interval = 12000): () => void {
  let stopped = false, running = false, timer: ReturnType<typeof setTimeout> | undefined;
  const run = async () => {
    if (stopped || running) return;
    running = true; if (timer) clearTimeout(timer);
    try { if (!document.hidden) await read(); } catch { /* The caller owns visible error state. */ }
    finally { running = false; if (!stopped) timer = setTimeout(run, interval); }
  };
  const wake = () => { void run(); };
  window.addEventListener('focus', wake); window.addEventListener('safepay:refresh', wake); window.addEventListener('safepay:financial-change', wake);
  void run();
  return () => { stopped = true; if (timer) clearTimeout(timer); window.removeEventListener('focus', wake); window.removeEventListener('safepay:refresh', wake); window.removeEventListener('safepay:financial-change', wake); };
}
