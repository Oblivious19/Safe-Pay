const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('transactions displays session email without using it as caller identity', () => {
  const html = fs.readFileSync(path.join(__dirname, '../src/ts/views/transactions.html'), 'utf8');
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/viewModels/transactions.ts'), 'utf8');
  assert.match(html, /Signed in as/);
  assert.match(html, /text: signedInEmail/);
  assert.match(source, /signedInEmail/);
  assert.doesNotMatch(source, /shreya@example|this\.email\s*=|userEmail/);
});
