const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const css = fs.readFileSync(path.join(__dirname, '../src/css/responsive.css'), 'utf8');
test('responsive layer loads last and uses mobile-first breakpoints', () => {
  const html = fs.readFileSync(path.join(__dirname, '../src/index.html'), 'utf8');
  assert.ok(html.indexOf('css/responsive.css') > html.indexOf('css/send-money.css'));
  assert.match(css, /@media \(min-width: 600px\)/);
  assert.match(css, /@media \(min-width: 900px\)/);
  assert.doesNotMatch(css, /overflow(?:-x)?:\s*(?:hidden|clip)/);
});
test('mobile layouts stack and retain usable input and CTA sizing', () => {
  assert.match(css, /\.dashboard-top-grid, \.recipient-grid, \.form-grid, \.transaction-details dl \{ grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(css, /font-size: 16px/);
  assert.match(css, /min-height: 52px/);
  assert.match(css, /position: sticky; bottom: max\(12px, env\(safe-area-inset-bottom\)\)/);
});
test('long amounts wrap without ellipsis and tables have bounded scrolling', () => {
  assert.match(css, /\.balance-amount[^}]*overflow-wrap: anywhere/);
  assert.match(css, /\.customer-shell table[^}]*max-width: 100%; overflow-x: auto/);
  assert.doesNotMatch(css, /text-overflow:\s*ellipsis/);
  assert.match(css, /scroll-margin-block: 90px/);
});
