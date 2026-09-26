# SafePay branding update

The shared shield is `src/css/images/safepay-brand-shield.png`. Header, authentication pages, administration, entry loader, favicon and footer use this transparent PNG. The existing landing-page hero illustration remains unchanged.

The footer follows the supplied dark-green reference. About SafePay and How it works open accessible native dialogs so they also work for signed-in users without navigating away. Home follows the existing session-aware home route. Social icons intentionally have no links until verified Twitter/X and Instagram profile URLs are supplied.

Shared spacing and table styles are in `src/css/brand-refinements.css`, loaded after the existing theme. Customer dashboard amounts use the same direction semantics as Transactions: explicit credits are incoming; legacy records remain outgoing; only settled payments receive a sign and direction colour. No banking or risk logic changed.

## Verification

- TypeScript type checking and the OJET development build passed.
- All 209 frontend tests passed, including dashboard-direction, shared-branding, admin-table and unified payment-gauge regressions.
- Desktop (1280px) and mobile (390px) browser checks used an isolated sample-data preview, not the backend. Verified logo loading, footer dialogs and Escape/focus return, all native dropdowns, admin directory/account selection, held-payment review opening, panel removal and bounded table scrolling. No actual payments or account changes were made.
- Both requested admin panels and the unused daily-value calculation/styles were removed. High-risk totals remain in the collapsible overview metrics.
- Follow-up: held-payment review typography is now 22px for its reference, 28px for the amount, 15px for section labels and 14px for body text (with compact mobile sizing). All payment-risk results use the same semicircle. Timed pauses follow the existing server deadline; administrator review uses an indeterminate arc with no countdown; final results are static. Medium, high, very high and instant-settlement states were checked in the isolated browser preview. The full-page brand entry loader remains unchanged.

## Asset preparation details

Mode: built-in image-generation tool, background-extraction edit of the user-supplied SafePay logo. Transparent alpha was verified. As an image edit, this is a prepared rendering of the supplied emblem, not a pixel-identical crop.

Final prompt:

> Edit the supplied SafePay logo image into a production website asset: extract ONLY the complete shield emblem on the left onto a genuinely transparent alpha background. Remove the green rectangular background and all SafePay lettering/dot to the right. Preserve the existing shield silhouette, proportions, perspective, silver-green rim, emerald metallic material, dark rupee symbol, bright lime S ribbon, highlights and fine edges faithfully; no redesign, no extra elements, no halo or cast shadow outside the shield. Center the isolated full shield, tightly framed with a small transparent margin, on a square canvas. This is a clean background extraction for a small navigation/footer logo, not a new logo concept.
