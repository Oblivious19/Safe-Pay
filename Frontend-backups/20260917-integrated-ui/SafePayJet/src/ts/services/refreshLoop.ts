/** Refreshes server data only. Leaving a page or starting a mutation invalidates old reads. */
export class RefreshLoop<T> {
  private active = false;
  private revision = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor(private read: () => Promise<T>, private publish: (result: T) => void,
    private failed: (error: unknown) => void, private shouldPoll: () => boolean,
    private interval = 5000) {}

  start(): void { this.active = true; void this.refresh(); }
  stop(): void { this.active = false; this.invalidate(); }
  invalidate(): void {
    this.revision++;
    if (this.timer !== undefined) clearTimeout(this.timer);
    this.timer = undefined;
  }
  async refresh(): Promise<void> {
    if (!this.active) return;
    this.invalidate();
    const revision = this.revision;
    try {
      const result = await this.read();
      if (this.active && revision === this.revision) this.publish(result);
    } catch (error) {
      if (this.active && revision === this.revision) this.failed(error);
    } finally {
      if (this.active && revision === this.revision && this.shouldPoll()) {
        this.timer = setTimeout(() => { void this.refresh(); }, this.interval);
      }
    }
  }
}
