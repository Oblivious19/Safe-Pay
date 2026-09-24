import * as ko from "knockout";

/** Reveal newly opened content once; polling/countdown updates do not move focus. */
ko.bindingHandlers.reveal = {
  init(element: HTMLElement, valueAccessor: () => unknown) {
    let previous: unknown = false;
    let frame = 0;
    const watch = ko.computed(() => {
      const value = ko.unwrap(valueAccessor() as any);
      if (value && value !== previous) {
        cancelAnimationFrame(frame);
        frame = requestAnimationFrame(() => {
          if (!element.isConnected) return;
          for (let parent = element.parentElement; parent; parent = parent.parentElement) {
            if (parent instanceof HTMLDetailsElement) parent.open = true;
          }
          if (!element.getClientRects().length) return;
          const dialog = element.closest('.pay-sheet, .tx-drawer-body');
          if (element.classList.contains('pay-panel')) element.scrollTop = 0;
          const rect = element.getBoundingClientRect();
          const bounds = dialog?.getBoundingClientRect();
          if (rect.top < (bounds?.top ?? 110) || rect.bottom > (bounds?.bottom ?? window.innerHeight)) {
            const tall = rect.height > (bounds?.height ?? window.innerHeight) - 120;
            element.scrollIntoView({ block: tall ? 'start' : 'nearest', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
          }
          if (!element.hasAttribute('tabindex')) element.tabIndex = -1;
          element.focus({ preventScroll: true });
        });
      }
      previous = value;
    });
    ko.utils.domNodeDisposal.addDisposeCallback(element, () => { watch.dispose(); cancelAnimationFrame(frame); });
  }
};

/** Custom payment sheets need the same focus and scroll behavior as JET dialogs. */
ko.bindingHandlers.paymentModal = {
  init(element: HTMLElement, valueAccessor: () => { open: ko.Observable<boolean> | ko.Computed<boolean>; close: () => void }) {
    let prior: HTMLElement | null = null;
    let restoreOverflow = '';
    let opened = false;
    let frame = 0;
    const restore = () => {
      if (!opened) return;
      opened = false;
      document.body.style.overflow = restoreOverflow;
      for (const el of Array.from(document.querySelectorAll<HTMLElement>('[data-payment-inert]'))) {
        el.inert = false; el.removeAttribute('data-payment-inert');
      }
      if (prior?.isConnected) prior.focus({ preventScroll: true });
      else {
        const heading = document.querySelector<HTMLElement>('.send-people h1');
        if (heading) { heading.tabIndex = -1; heading.focus({ preventScroll: true }); }
      }
    };
    const watch = ko.computed(() => {
      if (ko.unwrap(valueAccessor().open)) {
        if (opened) return;
        opened = true;
        prior = document.activeElement as HTMLElement;
        restoreOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        for (const el of Array.from(document.querySelectorAll<HTMLElement>('.customer-header, .customer-footer, .send-people, .send-flow-header'))) {
          if (!el.inert) { el.inert = true; el.setAttribute('data-payment-inert', ''); }
        }
        frame = requestAnimationFrame(() => {
          const sheet = element.querySelector<HTMLElement>('.pay-sheet');
          if (sheet) { sheet.scrollTop = 0; sheet.focus({ preventScroll: true }); }
        });
      } else restore();
    });
    const keydown = (event: KeyboardEvent) => {
      if (!opened || !element.contains(event.target as Node)) return;
      if (event.key === 'Escape') { event.preventDefault(); valueAccessor().close(); }
      if (event.key !== 'Tab') return;
      const items = Array.from(element.querySelectorAll<HTMLElement>('.pay-sheet button:not(:disabled), .pay-sheet a[href], .pay-sheet input:not(:disabled), .pay-sheet select:not(:disabled), .pay-sheet summary, .pay-sheet [tabindex="0"]')).filter(el => el.getClientRects().length);
      const first = items[0], last = items[items.length - 1];
      if (!first) { event.preventDefault(); return; }
      if (event.shiftKey && (document.activeElement === first || !items.includes(document.activeElement as HTMLElement))) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    element.addEventListener('keydown', keydown);
    ko.utils.domNodeDisposal.addDisposeCallback(element, () => { watch.dispose(); cancelAnimationFrame(frame); element.removeEventListener('keydown', keydown); restore(); });
  }
};
