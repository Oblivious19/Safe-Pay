import * as ko from "knockout";
import { arcOffset as fluxArcOffset, formatCountdown, fluxLetters, phaseFor, PROTECTION_PHASES } from "../utils/protection";
import "ojs/ojbutton";
import "ojs/ojprogress-circle";

const WINDOW = 10;
const RING = 527.52;
type DemoMode = "live" | "cancelled" | "settling";
type RoadPath = "instant" | "pause" | "hold";
type StoryMode = "idle" | "live" | "cancelled" | "settled" | "held" | "skipped";
interface RoadStep { id: string; kicker: string; title: string; }
interface Slip {
  amount: string;
  payee: string;
  label: string;
  handle: string;
  note: string;
  from: string;
  why: string;
  holdMs: number;
}

const SLIPS: Record<RoadPath, Slip> = {
  instant: {
    amount: "₹180", payee: "Sandwich counter", label: "Everyday",
    handle: "5012 8834 1190", note: "Lunch sandwich",
    from: "Salary account · 4821", why: "Small, familiar payment", holdMs: 400
  },
  pause: {
    amount: "₹24,500", payee: "Anika Sharma", label: "New payee",
    handle: "0481 6629 7731", note: "Studio deposit",
    from: "Salary account · 4821", why: "New person, mid-size amount", holdMs: 8000
  },
  hold: {
    amount: "₹1,20,000", payee: "Meridian Supplies", label: "First-time vendor",
    handle: "2291 8700 4518", note: "First invoice",
    from: "Salary account · 4821", why: "Large, first-time payee", holdMs: 12000
  }
};

class HomeViewModel {
  unavailable = ko.observable(false);
  remaining = ko.observable(WINDOW);
  mode = ko.observable<DemoMode>("live");
  entered = ko.observable(false);
  scrollT = ko.observable(0);
  activeStep = ko.observable("confirm");
  path = ko.observable<RoadPath>("pause");
  cardProgress = ko.observable(0);
  storyRemaining = ko.observable(8);
  storyMode = ko.observable<StoryMode>("idle");
  steps: RoadStep[] = [
    { id: "confirm", kicker: "01 / Send", title: "You tap confirm" },
    { id: "check", kicker: "02 / Look", title: "We read what you sent" },
    { id: "decide", kicker: "03 / Path", title: "Three doors. One opens." },
    { id: "protect", kicker: "04 / Window", title: "Still yours for a few seconds" },
    { id: "done", kicker: "05 / End", title: "It lands, or it never left" }
  ];
  private timer?: ReturnType<typeof setInterval>;
  private resetTimer?: ReturnType<typeof setTimeout>;
  private storyTick?: ReturnType<typeof setInterval>;
  private enterFrame?: number;
  private reduced = false;
  private pointerX?: number;
  private swiped = false;

  progress = ko.pureComputed(() => this.mode() === "cancelled" ? 0 : Math.round(((WINDOW - this.remaining()) / WINDOW) * 100));
  clock = ko.pureComputed(() => this.mode() === "settling" ? "Settling" : formatCountdown(this.remaining()));
  phase = ko.pureComputed(() => this.mode() === "cancelled" ? "cancelled" : this.mode() === "settling" ? "settling" : phaseFor(this.progress(), PROTECTION_PHASES));
  letters = ko.pureComputed(() => fluxLetters(this.phase()));
  arcOffset = ko.pureComputed(() => fluxArcOffset(this.progress()));
  fluxPercent = ko.pureComputed(() => `${this.progress()}%`);
  copy = ko.pureComputed(() => {
    if (this.mode() === "cancelled") return "Cancelled. The amount never left the account.";
    if (this.mode() === "settling") return "The window ended. This payment would now settle.";
    return "This payment is held before it settles. Tap cancel to keep the money.";
  });
  stepIndex = ko.pureComputed(() => Math.max(0, this.steps.findIndex((step) => step.id === this.activeStep())));
  introShift = ko.pureComputed(() => this.reduced ? null : `translate3d(0, ${-this.scrollT() * 56}px, 0)`);
  introFade = ko.pureComputed(() => this.reduced ? "1" : String(1 - this.scrollT() * 0.45));
  stageShift = ko.pureComputed(() => this.reduced ? null : `translate3d(0, ${this.scrollT() * 28}px, 0) scale(${1 - this.scrollT() * 0.08})`);
  storyKicker = ko.pureComputed(() => this.steps[this.stepIndex()]?.kicker ?? "");
  storyTitle = ko.pureComputed(() => this.steps[this.stepIndex()]?.title ?? "");
  slip = ko.pureComputed(() => SLIPS[this.path()]);
  slipAmount = ko.pureComputed(() => this.slip().amount);
  slipPayee = ko.pureComputed(() => this.slip().payee);
  slipLabel = ko.pureComputed(() => this.slip().label);
  slipHandle = ko.pureComputed(() => this.slip().handle);
  slipNote = ko.pureComputed(() => this.slip().note);
  slipFrom = ko.pureComputed(() => this.slip().from);
  slipWhy = ko.pureComputed(() => this.slip().why);
  lookRows = ko.pureComputed(() => [
    { label: "Amount", value: this.slipAmount(), tone: "ok" },
    { label: "Payee", value: this.slipPayee(), tone: "ok" },
    { label: "Why it paused", value: this.slipWhy(), tone: this.path() === "instant" ? "ok" : "watch" }
  ]);
  pathRows = [
    { id: "instant" as RoadPath, title: "Lands now", detail: "Everyday amounts skip the wait" },
    { id: "pause" as RoadPath, title: "Short pause", detail: "A few seconds you can still undo" },
    { id: "hold" as RoadPath, title: "Extra review", detail: "Held. No automatic release" }
  ];
  slipState = ko.pureComputed(() => {
    const mode = this.storyMode();
    if (mode === "live") return "Window open — still cancellable";
    if (mode === "cancelled") return "Reversed. Nothing left.";
    if (mode === "settled") return "Settled. Too late to undo.";
    if (mode === "held") return "Held. Waiting for a person.";
    if (mode === "skipped") return "Payment done";
    return "Not settled yet";
  });
  storyClock = ko.pureComputed(() => formatCountdown(this.storyRemaining()));
  canCancelStory = ko.pureComputed(() => this.storyMode() === "live" && this.path() === "pause");
  storyTone = ko.pureComputed(() => `is-${this.storyMode()}`);
  endKind = ko.pureComputed(() => {
    const mode = this.storyMode();
    if (mode === "cancelled") return "reversed";
    if (mode === "held") return "held";
    return "settled";
  });
  endLabel = ko.pureComputed(() => {
    const kind = this.endKind();
    if (kind === "reversed") return "Reversed";
    if (kind === "held") return "Held";
    return "Settled";
  });
  ringOffset = ko.pureComputed(() => RING * (this.cardProgress() / 100));
  atStart = ko.pureComputed(() => this.stepIndex() <= 0);
  atEnd = ko.pureComputed(() => this.stepIndex() >= this.steps.length - 1);
  storyBody = ko.pureComputed(() => {
    const step = this.activeStep();
    const path = this.path();
    const mode = this.storyMode();
    if (step === "confirm") return "Pick someone, pick an amount, hit confirm. SafePay now has the instruction — the rupees have not left yet.";
    if (step === "check") return "Transparent, rule-based checks look at the amount, the recipient, and the situation. Every reason is kept so the pause can be explained in plain language.";
    if (step === "decide") return "Everyday payments can land in the same breath. Unusual ones get a short window you can still cancel. The most sensitive ones wait for a person.";
    if (step === "protect") {
      if (path === "instant") return "This path skips the pause. The payment is released and settles without a countdown.";
      if (path === "hold") return "There is no timer here. The payment stays held until it is reviewed — it will not auto-release on a zero countdown.";
      return "The window is open. Cancel and the amount never leaves. Wait it out, and the payment would settle.";
    }
    if (mode === "cancelled") return "Cancelled in time. The amount never left the account.";
    if (mode === "settled" && path === "pause") return "The window closed. You didn’t pull it back, so this payment would now settle.";
    if (path === "instant" || mode === "settled") return "Settled. The payment went through as soon as the checks said it was routine.";
    if (path === "hold" || mode === "held") return "Still held for extra review. Nothing moves until that review is complete.";
    return "Two endings: you cancel in time and the amount stays put, or the window ends and the payment settles.";
  });
  isComplete = (id: string): boolean => this.stepIndex() > this.steps.findIndex((step) => step.id === id);
  segmentFill = (id: string): string => {
    const index = this.steps.findIndex((step) => step.id === id);
    return index <= this.stepIndex() ? "scaleX(1)" : "scaleX(0)";
  };

  private clearDemo(): void {
    if (this.timer) clearInterval(this.timer);
    if (this.resetTimer) clearTimeout(this.resetTimer);
    this.timer = undefined; this.resetTimer = undefined;
  }
  private clearStoryClock(): void {
    if (this.storyTick) clearInterval(this.storyTick);
    this.storyTick = undefined;
  }
  private clear(): void {
    this.clearDemo();
    this.clearStoryClock();
  }
  private startStoryClock(): void {
    this.clearStoryClock();
    const total = this.slip().holdMs;
    const seconds = this.holdSeconds();
    this.storyMode("live");
    this.storyRemaining(seconds);
    this.cardProgress(0);
    const start = Date.now();
    this.storyTick = setInterval(() => {
      if (this.storyMode() !== "live" || this.activeStep() !== "protect" || this.path() !== "pause") {
        this.clearStoryClock();
        return;
      }
      const t = Math.min(1, (Date.now() - start) / total);
      this.cardProgress(t * 100);
      this.storyRemaining(Math.max(0, Math.ceil(seconds * (1 - t))));
      if (t >= 1) {
        this.clearStoryClock();
        this.storyRemaining(0);
        this.finishPlay("settled");
      }
    }, 100);
  }
  private loop(): void {
    this.clearDemo();
    this.mode("live"); this.remaining(WINDOW);
    this.timer = setInterval(() => {
      if (this.mode() !== "live") return;
      const next = this.remaining() - 1;
      if (next <= 0) {
        this.remaining(0); this.mode("settling");
        this.resetTimer = setTimeout(() => this.loop(), 1800);
      } else this.remaining(next);
    }, 1000);
  }
  private holdSeconds(): number {
    return Math.max(1, Math.round(this.slip().holdMs / 1000));
  }
  private finishPlay(mode: StoryMode): void {
    this.clearStoryClock();
    this.storyMode(mode);
    this.cardProgress(100);
    this.activeStep("done");
  }
  private syncCard(): void {
    this.clearStoryClock();
    const step = this.activeStep();
    const path = this.path();
    if (step === "protect") {
      if (path === "pause") {
        this.startStoryClock();
        return;
      }
      this.cardProgress(0);
      this.storyMode(path === "hold" ? "held" : "skipped");
      return;
    }
    if (step === "done") {
      if (this.storyMode() === "idle" || this.storyMode() === "skipped" || this.storyMode() === "live") {
        this.storyMode(path === "hold" ? "held" : "settled");
      }
      this.cardProgress(100);
      return;
    }
    this.storyMode("idle");
    this.cardProgress(0);
  }
  cancelDemo = (): void => {
    if (this.mode() !== "live") return;
    this.clearDemo();
    this.mode("cancelled"); this.remaining(WINDOW);
    this.resetTimer = setTimeout(() => this.loop(), 2600);
  };
  cancelStory = (): void => {
    if (this.storyMode() !== "live") return;
    this.finishPlay("cancelled");
  };
  selectStep = (id: string): void => {
    this.activeStep(id);
    this.syncCard();
  };
  selectPath = (path: RoadPath): void => {
    this.path(path);
    if (this.activeStep() === "done") {
      this.storyMode("idle");
      this.activeStep("confirm");
    }
    this.syncCard();
  };
  nextCard = (): void => {
    if (this.atEnd()) {
      this.cardProgress(100);
      return;
    }
    this.activeStep(this.steps[this.stepIndex() + 1].id);
    this.syncCard();
  };
  prevCard = (): void => {
    if (this.atStart()) return;
    this.storyMode("idle");
    this.activeStep(this.steps[this.stepIndex() - 1].id);
    this.syncCard();
  };
  redoStory = (): void => {
    this.clearStoryClock();
    this.storyMode("idle");
    this.cardProgress(0);
    this.storyRemaining(this.holdSeconds());
    this.activeStep("confirm");
  };
  tapReel = (_data: unknown, event: MouseEvent | PointerEvent): boolean => {
    if (this.swiped) { this.swiped = false; return true; }
    const node = event.target as HTMLElement | null;
    if (node?.closest("button, a, [role='radio']")) return true;
    const host = (event.currentTarget as HTMLElement | null);
    if (!host) return true;
    const x = (("clientX" in event ? event.clientX : 0) - host.getBoundingClientRect().left) / host.clientWidth;
    if (x < 0.33) this.prevCard();
    else this.nextCard();
    return true;
  };
  onPointerDown = (_data: unknown, event: PointerEvent): boolean => {
    if ((event.target as HTMLElement | null)?.closest("button, a, [role='radio']")) return true;
    this.pointerX = event.clientX;
    return true;
  };
  onPointerUp = (_data: unknown, event: PointerEvent): boolean => {
    if (this.pointerX == null) return true;
    const dx = event.clientX - this.pointerX;
    this.pointerX = undefined;
    if (Math.abs(dx) < 48) return true;
    this.swiped = true;
    if (dx < 0) this.nextCard();
    else this.prevCard();
    return true;
  };
  private onScroll = (): void => {
    const range = Math.max(240, window.innerHeight * 0.5);
    this.scrollT(Math.min(1, Math.max(0, window.scrollY / range)));
  };
  private onKeys = (event: KeyboardEvent): void => {
    const host = document.getElementById("home-reel");
    if (!host || (document.activeElement !== host && !host.contains(document.activeElement))) return;
    if (event.key === "ArrowRight") { event.preventDefault(); this.nextCard(); }
    if (event.key === "ArrowLeft") { event.preventDefault(); this.prevCard(); }
  };
  connected(): void {
    document.title = "SafePay — A little more peace of mind";
    this.unavailable(new URLSearchParams(window.location.search).get("reason") === "unavailable");
    this.reduced = !!window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    this.loop();
    this.onScroll();
    window.addEventListener("scroll", this.onScroll, { passive: true });
    window.addEventListener("keydown", this.onKeys);
    if (this.reduced) this.entered(true);
    else this.enterFrame = requestAnimationFrame(() => {
      this.enterFrame = requestAnimationFrame(() => this.entered(true));
    });
  }
  disconnected(): void {
    this.clear();
    if (this.enterFrame) cancelAnimationFrame(this.enterFrame);
    window.removeEventListener("scroll", this.onScroll);
    window.removeEventListener("keydown", this.onKeys);
  }
}
export = HomeViewModel;
