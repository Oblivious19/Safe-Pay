const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ko = require('knockout');
const requirejs = require('requirejs');

// Load the evaluator used by JET itself. JavaScript Function() accepts browser
// globals that are intentionally absent from root.ts's CSP evaluation scope.
requirejs.config({
  baseUrl: path.resolve(__dirname, '..'),
  paths: { ojs: 'node_modules/@oracle/oraclejet/dist/js/libs/oj/debug' },
  nodeRequire: require
});
const evaluatorReady = new Promise((resolve, reject) => {
  requirejs(['ojs/ojcspexpressionevaluator-internal'], module => {
    resolve(new module.CspExpressionEvaluatorInternal({ globalScope: { oj: {} } }));
  }, reject);
});
const accounts = [7, 23].map(accountId => ({ accountId, accountNumber: '50000000000' + accountId,
  accountType: 'SAVINGS', status: 'ACTIVE' }));
const beneficiaries = [70, 230].map(beneficiaryId => ({ beneficiaryId,
  beneficiaryName: 'Recipient ' + beneficiaryId, bankAccountNumber: '0012345678' }));
function context() {
  const label = account => account.accountType + ' ' + account.accountNumber;
  const model = {
    accounts: ko.observableArray([]), userAccounts: ko.observableArray([]), beneficiaries: ko.observableArray([]),
    accountId: ko.observable(''), selectedAccountId: ko.observable(''), creditAccountId: ko.observable(''), beneficiaryId: ko.observable(''),
    accountLabel: label, accountOption: label, creditAccountLabel: label,
    saving: ko.observable(false), busyId: ko.observable(null), sending: ko.observable(false),
    reviewDraft: ko.observable(null), attemptLocked: ko.observable(false), cancelling: ko.observable(false),
    loading: ko.observable(false), disabled: ko.observable(false)
  };
  return [model, { $data: model, $parent: model }];
}
function selectorBindings(screen) {
  const html = fs.readFileSync(path.join(__dirname, '../src/ts/views', screen + '.html'), 'utf8');
  return [...html.matchAll(/<select\b[^>]*\bdata-bind="([^"]+)"/g)]
    .map(match => match[1]).filter(binding => binding.includes('optionsValue:'));
}
function optionRows(binding) {
  return ko.unwrap(binding.options).map(item => ({ value: binding.optionsValue(item), text: binding.optionsText(item) }));
}

test('JET evaluator reproduces the old account and beneficiary String callback failures', async () => {
  const evaluator = await evaluatorReady;
  for (const [field, item] of [['accountId', accounts[0]], ['beneficiaryId', beneficiaries[0]]]) {
    const callback = evaluator.createEvaluator('function(a) { return String(a.' + field + '); }').evaluate(context());
    assert.throws(() => callback(item), /Variable String is undefined/);
  }
});
for (const [screen, expectedCount] of [['dashboard', 1], ['beneficiaries', 1], ['send-money', 2], ['admin', 1]]) {
  test(`${screen}: actual selector bindings support empty, single and multiple options in JET CSP scope`, async () => {
    const evaluator = await evaluatorReady, bindings = selectorBindings(screen);
    assert.equal(bindings.length, expectedCount);
    for (const expression of bindings) {
      const binding = evaluator.createEvaluator('({' + expression + '})').evaluate(context());
      const isBeneficiary = expression.includes('options: beneficiaries');
      const items = isBeneficiary ? beneficiaries : accounts;
      const field = isBeneficiary ? 'beneficiaryId' : 'accountId';
      assert.deepEqual(optionRows(binding), []);
      binding.options([items[0]]);
      assert.deepEqual(optionRows(binding).map(row => row.value), [String(items[0][field])]);
      binding.options(items);
      const rows = optionRows(binding);
      assert.deepEqual(rows.map(row => row.value), items.map(item => String(item[field])));
      assert.ok(rows.every(row => typeof row.value === 'string' && row.text.length > 0));
      binding.value(rows[1].value);
      assert.equal(binding.value(), String(items[1][field]));
      if (isBeneficiary) assert.equal(rows[0].text, 'Recipient 70 — 0012345678');
      if (expression.includes('optionsCaption:')) assert.match(binding.optionsCaption, /^Select /);
    }
  });
}
