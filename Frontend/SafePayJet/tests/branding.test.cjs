const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const read=file=>fs.readFileSync(path.join(__dirname,'../src',file),'utf8');

test('all main brands share the new shield asset',()=>{
  for(const file of ['index.html','ts/views/home.html','ts/views/login.html','ts/views/register.html','ts/views/admin.html']) {
    const html=read(file);
    assert.match(html,/css\/images\/safepay-brand-shield\.png/);
    assert.doesNotMatch(html,/class="customer-logo"|SafePay \/ Administration/);
  }
  assert.ok(fs.existsSync(path.join(__dirname,'../src/css/images/safepay-brand-shield.png')));
});

test('footer has working information dialogs and no invented social destinations',()=>{
  const html=read('index.html');
  assert.match(html,/Every payment\. A little more peace of mind\./);
  assert.doesNotMatch(html,/Simulated payments · No real money is moved\./);
  assert.match(html,/<dialog id="footer-info"/);
  assert.match(html,/showFooterInfo\('about'\)/);
  assert.match(html,/showFooterInfo\('how'\)/);
  assert.match(html,/<form method="dialog">/);
  assert.doesNotMatch(html,/href="https:\/\/(twitter|x|instagram)\.com/);
  assert.match(read('ts/appController.ts'),/showModal\(\)/);
});

test('native selects reserve an arrow gutter and preserve forced-colour fallback',()=>{
  const css=read('css/brand-refinements.css');
  assert.match(css,/padding:11px 46px 11px 16px/);
  assert.match(css,/background-position:right 14px center/);
  assert.match(css,/@media\(forced-colors:active\)/);
  assert.match(css,/appearance:auto; background-image:none/);
  assert.match(css,/\.admin-page \.admin-table \{ display:table;/);
  assert.ok(read('index.html').indexOf('brand-refinements.css')>read('index.html').indexOf('ui-refinements.css'));
});
