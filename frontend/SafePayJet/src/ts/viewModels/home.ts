import * as ko from "knockout";
import { arcOffset as fluxArcOffset, formatCountdown, fluxLetters, phaseFor, PROTECTION_PHASES } from "../utils/protection";
import "ojs/ojbutton";
import "ojs/ojprogress-circle";

const WINDOW = 10;
type DemoMode = "live" | "cancelled" | "settling";
type RoadPath = "instant" | "pause" | "hold";
interface RoadStep { id: string; kicker: string; title: string; }

class HomeViewModel {
  unavailable = ko.observable(false);
  remaining = ko.observable(WINDOW);
  mode = ko.observable<DemoMode>("live");
  entered = ko.observable(false);
  scrollT = ko.observable(0);
  activeStep = ko.observable("confirm");
  path = ko.observable<RoadPath>("pause");
  playing = ko.observable(false);
  steps: RoadStep[] = [
    { id: "confirm", kicker: "01 / Confirm", title: "You send the instruction" },
    { id: "check", kicker: "02 / Check", title: "Rules look at the payment" },
    { id: "decide", kicker: "03 / Decide", title: "A path is chosen" },
    { id: "protect", kicker: "04 / Pause", title: "A moment to think" },
    { id: "done", kicker: "05 / Outcome", title: "It stays, or it goes" }
  ];
  private timer?: ReturnType<typeof setInterval>;
  private resetTimer?: ReturnType<typeof setTimeout>;
  private playTimer?: ReturnType<typeof setTimeout>;
  private enterFrame?: number;
  private reduced = false;

  progress = ko.pureComputed(() => this.mode() === "cancelled" ? 0 : Math.round(((WINDOW - this.remaining()) / WINDOW) * 100));
  clock = ko.pureComputed(() => this.mode() === "settling" ? "Settling" : formatCountdown(this.remaining()));
  phase = ko.pureComputed(() => this.mode() === "cancelled" ? "cancelled" : this.mode() === "settling" ? "settling" : phaseFor(this.progress(), PROTECTION_PHASES));
  letters = ko.pureComputed(() => fluxLetters(this.phase()));
  arcOffset = ko.pureComputed(() => fluxArcOffset(this.progress()));
  fluxPercent = ko.pureComputed(() => `${this.progress()}%`);
  copy = ko.pureComputed(() => {
    if (this.mode() === "cancelled") return "Cancelled. The amount never left the account.";
    if (this.mode() === "settling") return "The example window ended. A real demo payment waits for the server to release and settle it.";
    return "This payment is held before it settles. Tap cancel to keep the money.";
  });
  stepIndex = ko.pureComputed(() => Math.max(0, this.steps.findIndex((step) => step.id === this.activeStep())));
  roadFill = ko.pureComputed(() => `${((this.stepIndex() + 1) / this.steps.length) * 100}%`);
  introShift = ko.pureComputed(() => this.reduced ? null : `translate3d(0, ${-this.scrollT() * 56}px, 0)`);
  introFade = ko.pureComputed(() => this.reduced ? "1" : String(1 - this.scrollT() * 0.45));
  stageShift = ko.pureComputed(() => this.reduced ? null : `translate3d(0, ${this.scrollT() * 28}px, 0) scale(${1 - this.scrollT() * 0.08})`);
  storyKicker = ko.pureComputed(() => this.steps[this.stepIndex()]?.kicker ?? "");
  storyTitle = ko.pureComputed(() => this.steps[this.stepIndex()]?.title ?? "");
  storyBody = ko.pureComputed(() => {
    const step = this.activeStep();
    const path = this.path();
    if (step === "confirm") return "You review the recipient and amount, then confirm. SafePay now has the instruction — the money has not settled yet.";
    if (step === "check") return "The active amount policy determines the risk tier and protection. The payment records its policy version and explanation.";
    if (step === "decide") return "The checks pick one of three paths. Routine payments can settle in the same moment. Others get a short cancellable window. The most sensitive ones wait for extra review.";
    if (step === "protect") {
      if (path === "instant") return "This path skips the pause. The payment is released and settles without a countdown.";
      if (path === "hold") return "Email verification comes first, followed by Risk Officer review. There is no automatic release timer for this path.";
      return "A protection window opens. You can still cancel while it is running. When the window ends, the server determines release and simulated settlement.";
    }
    if (path === "instant") return "Settled. The payment went through as soon as the checks said it was routine.";
    if (path === "hold") return "Still held for extra review. Nothing moves until that review is complete.";
    return "Two endings: you cancel in time and the amount stays put, or the window ends and the payment settles.";
  });
  isComplete = (id: string): boolean => this.stepIndex() > this.steps.findIndex((step) => step.id === id);

  private clearDemo(): void {
    if (this.timer) clearInterval(this.timer);
    if (this.resetTimer) clearTimeout(this.resetTimer);
    this.timer = undefined; this.resetTimer = undefined;
  }
  private clear(): void {
    this.clearDemo();
    this.clearPlayOnly();
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
  private clearPlayOnly(): void {
    if (this.playTimer) clearTimeout(this.playTimer);
    this.playTimer = undefined;
  }
  cancelDemo = (): void => {
    if (this.mode() !== "live") return;
    this.clearDemo();
    this.mode("cancelled"); this.remaining(WINDOW);
    this.resetTimer = setTimeout(() => this.loop(), 2600);
  };
  selectStep = (id: string): void => {
    this.playing(false); this.clearPlayOnly();
    this.activeStep(id);
  };
  selectPath = (path: RoadPath): void => {
    this.path(path);
    if (this.activeStep() === "confirm" || this.activeStep() === "check") this.activeStep("decide");
  };
  playFlow = (): void => {
    if (this.playing()) return;
    this.playing(true);
    let index = 0;
    const tick = (): void => {
      if (!this.playing()) return;
      this.activeStep(this.steps[index].id);
      document.getElementById("road-" + this.steps[index].id)?.scrollIntoView({
        behavior: this.reduced ? "auto" : "smooth", block: "center"
      });
      index += 1;
      if (index >= this.steps.length) {
        this.playing(false);
        return;
      }
      this.playTimer = setTimeout(tick, 2200);
    };
    tick();
  };
  private onScroll = (): void => {
    const range = Math.max(240, window.innerHeight * 0.5);
    this.scrollT(Math.min(1, Math.max(0, window.scrollY / range)));
  };
  connected(): void {
    document.title = "SafePay — A little more peace of mind";
    this.unavailable(new URLSearchParams(window.location.search).get("reason") === "unavailable");
    this.reduced = !!window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    this.loop();
    this.onScroll();
    window.addEventListener("scroll", this.onScroll, { passive: true });
    if (this.reduced) this.entered(true);
    else this.enterFrame = requestAnimationFrame(() => {
      this.enterFrame = requestAnimationFrame(() => this.entered(true));
    });
  }
  disconnected(): void {
    this.clear();
    this.playing(false);
    if (this.enterFrame) cancelAnimationFrame(this.enterFrame);
    window.removeEventListener("scroll", this.onScroll);
  }
}
export = HomeViewModel;
