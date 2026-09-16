const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
test('page bindings parse and conditional comment containers are balanced', () => {
  const views = path.join(__dirname, '../src/ts/views');
  for (const name of fs.readdirSync(views).filter(name => name.endsWith('.html'))) {
    const html = fs.readFileSync(path.join(views, name), 'utf8');
    assert.doesNotMatch(html, /<\/[a-z][a-z0-9-]*\s+\S[^>]*>/i, name + ': closing tags cannot contain attributes');
    for (const [, binding] of html.matchAll(/data-bind="([^"]*)"/g)) {
      const decoded = binding.replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
      assert.doesNotThrow(() => new Function('$context', '$data', 'with($context){with($data){return ({' + decoded + '});}}'), name + ': ' + binding);
    }
    let depth = 0;
    for (const [, closing] of html.matchAll(/<!--\s*(\/?)ko\b[^]*?-->/g)) {
      depth += closing ? -1 : 1; assert.ok(depth >= 0, name + ' closes an unopened binding');
    }
    assert.equal(depth, 0, name + ' leaves a conditional binding open');
  }
});