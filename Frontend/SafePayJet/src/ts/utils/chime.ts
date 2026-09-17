/**
 * UI tones generated in the browser. There is no audio file to host, and nothing
 * is sent anywhere.
 *
 * Browsers only unlock Web Audio inside a tap. Payment PIN keys, Confirm, Answer
 * and Hang up call armAudio() in that same tick so later tones (the delayed ring,
 * the receipt after the network returns) can still play.
 */
type Tone = "paid" | "ok" | "off" | "ring" | "answer" | "end";

let audio: AudioContext | undefined;
let ringing: ReturnType<typeof setInterval> | undefined;
let ringOn = false;
let armed = false;

function ctor(): (new () => AudioContext) | undefined {
  return window.AudioContext
    || (window as Window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
}

function context(): AudioContext | undefined {
  const Audio = ctor();
  if (!Audio) return undefined;
  if (!audio) audio = new Audio();
  return audio;
}

function silentTick(ctx: AudioContext): void {
  try {
    const buffer = ctx.createBuffer(1, 1, ctx.sampleRate || 22050);
    const src = ctx.createBufferSource();
    src.buffer = buffer;
    src.connect(ctx.destination);
    src.start(0);
  } catch { /* Some test/jsdom contexts have a stub AudioContext. */ }
}

function unlock(): Promise<AudioContext | undefined> {
  const ctx = context();
  if (!ctx) return Promise.resolve(undefined);
  silentTick(ctx);
  if (ctx.state === "running") return Promise.resolve(ctx);
  return Promise.resolve(ctx.resume()).then(() => ctx).catch(() => ctx);
}

/**
 * Must run inside a click/tap. Creates the audio graph and resumes it so later
 * async chimes are allowed.
 */
export function armAudio(): void {
  armed = true;
  void unlock();
}

function listenForUnlock(): void {
  const once = (): void => {
    armAudio();
    window.removeEventListener("pointerdown", once, true);
    window.removeEventListener("keydown", once, true);
    window.removeEventListener("touchstart", once, true);
  };
  window.addEventListener("pointerdown", once, true);
  window.addEventListener("keydown", once, true);
  window.addEventListener("touchstart", once, true);
}

try { listenForUnlock(); } catch { /* Node tests have no window listeners. */ }

/** A triangle fundamental plus a quiet octave, so the note has body instead of a thin beep. */
function voice(
  ctx: AudioContext, freq: number, when: number, length: number, volume: number, type: OscillatorType = "triangle"
): void {
  const master = ctx.createGain();
  const fund = ctx.createOscillator();
  const over = ctx.createOscillator();
  const overGain = ctx.createGain();
  fund.type = type;
  over.type = "sine";
  fund.frequency.value = freq;
  over.frequency.value = freq * 2;
  overGain.gain.value = 0.22;
  master.gain.setValueAtTime(0.0001, when);
  master.gain.exponentialRampToValueAtTime(volume, when + 0.04);
  master.gain.exponentialRampToValueAtTime(volume * 0.7, when + Math.max(0.08, length * 0.45));
  master.gain.exponentialRampToValueAtTime(0.0001, when + length);
  fund.connect(master);
  over.connect(overGain);
  overGain.connect(master);
  master.connect(ctx.destination);
  fund.start(when);
  over.start(when);
  fund.stop(when + length + 0.05);
  over.stop(when + length + 0.05);
}

function play(
  notes: { freq: number; at: number; length: number; volume: number; type?: OscillatorType }[],
  allowed?: () => boolean
): void {
  void unlock().then((ctx) => {
    if (!ctx || ctx.state === "suspended") return;
    if (allowed && !allowed()) return;
    const now = ctx.currentTime;
    for (const note of notes) voice(ctx, note.freq, now + note.at, note.length, note.volume, note.type);
  });
}

const heardKeys = new Set<string>();

function keyFor(tx: { transactionId: number; state: string }): string {
  return tx.transactionId + ":" + tx.state;
}

/** Seed already-settled rows so opening Transactions does not replay old receipts. */
export function rememberSettled(tx: { transactionId: number; state: string } | null | undefined): void {
  try {
    if (tx && tx.state === "SETTLED") heardKeys.add(keyFor(tx));
  } catch { /* Bookkeeping only. */ }
}

/** Receipt / placement tone for a payment the customer just authorised. */
export function chimeForPayment(tx: { transactionId: number; state: string } | null | undefined): void {
  try {
    if (!tx || !Number.isSafeInteger(tx.transactionId) || tx.transactionId <= 0 || !tx.state) return;
    const key = keyFor(tx);
    if (heardKeys.has(key)) return;
    heardKeys.add(key);
    if (tx.state === "SETTLED") playChime("ok");
    else if (tx.state === "PROTECTED" || tx.state === "HARD_HOLD") playChime("paid");
    else if (tx.state === "CANCELLED" || tx.state === "REJECTED") playChime("off");
  } catch { /* Autoplay can be blocked; the visual result still stands. */ }
}

/** The receipt chime: only when this payment has actually settled, and only once. */
export function chimeIfSettled(tx: { transactionId: number; state: string } | null | undefined): void {
  if (!tx || tx.state !== "SETTLED") return;
  chimeForPayment(tx);
}

/** Confirmation after a payment is placed, settled, cancelled, or verified. */
export function playChime(kind: Exclude<Tone, "ring">): void {
  try {
    if (!armed) armAudio();
    if (kind === "ok") {
      play([
        { freq: 392, at: 0, length: 0.24, volume: 0.16 },
        { freq: 523, at: 0.14, length: 0.26, volume: 0.18 },
        { freq: 659, at: 0.3, length: 0.28, volume: 0.2 },
        { freq: 784, at: 0.48, length: 0.34, volume: 0.22 },
        { freq: 1046, at: 0.7, length: 0.95, volume: 0.24 }
      ]);
      return;
    }
    if (kind === "answer") {
      play([
        { freq: 523, at: 0, length: 0.16, volume: 0.2 },
        { freq: 659, at: 0.12, length: 0.2, volume: 0.22 },
        { freq: 784, at: 0.28, length: 0.42, volume: 0.24 }
      ]);
      return;
    }
    if (kind === "end" || kind === "off") {
      play([
        { freq: 392, at: 0, length: 0.18, volume: 0.2 },
        { freq: 330, at: 0.16, length: 0.22, volume: 0.18 },
        { freq: 262, at: 0.36, length: 0.4, volume: 0.16 }
      ]);
      return;
    }
    play([
      { freq: 523, at: 0, length: 0.24, volume: 0.18 },
      { freq: 659, at: 0.16, length: 0.26, volume: 0.2 },
      { freq: 784, at: 0.34, length: 0.3, volume: 0.22 },
      { freq: 1046, at: 0.56, length: 0.75, volume: 0.24 }
    ]);
  } catch {
    /* Autoplay can be blocked; the visual result still stands. */
  }
}

/** Repeating two-tone ring while the simulated verification call is incoming. */
export function startRing(): void {
  stopRing();
  ringOn = true;
  const pulse = (): void => {
    play([
      { freq: 440, at: 0, length: 0.42, volume: 0.22, type: "sine" },
      { freq: 523, at: 0, length: 0.42, volume: 0.18, type: "sine" },
      { freq: 440, at: 0.55, length: 0.42, volume: 0.22, type: "sine" },
      { freq: 523, at: 0.55, length: 0.42, volume: 0.18, type: "sine" }
    ], () => ringOn);
  };
  try { pulse(); ringing = setInterval(pulse, 1600); } catch { /* same as playChime */ }
}

export function stopRing(): void {
  ringOn = false;
  if (ringing) clearInterval(ringing);
  ringing = undefined;
}
