const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');
const refreshModule = { exports: {} };
const refreshSource = fs.readFileSync(path.join(__dirname, '../src/ts/services/refreshLoop.ts'), 'utf8');
const refreshCode = ts.transpileModule(refreshSource, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
vm.runInNewContext('(function(module,exports){' + refreshCode + '\n})', { setTimeout, clearTimeout })(refreshModule, refreshModule.exports);

for (const screen of ['send-money', 'beneficiaries', 'transactions']) {
  test(`${screen} displays session email without using it as caller identity`, async () => {
    const profile = ko.observable({ email: 'current@example.test' });
    const calls = [], module = { exports: {} };
    const api = Object.fromEntries(['getAccounts', 'getBeneficiaries', 'getTransactions'].map(name =>
      [name, async (...args) => { calls.push(args); return []; }]));
    api.transactionService = { list: async (...args) => { calls.push(args); return []; } };
    api.apiClient = { sessionRevision: () => 0 };
    const source = fs.readFileSync(path.join(__dirname, '../src/ts/viewModels', screen + '.ts'), 'utf8');
    const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
    const imports = { knockout: ko, '../appController': { default: { profile } }, '../services/api': api,
      '../services/refreshLoop': refreshModule.exports, '../accUtils': { announce: () => {} },
      '../services/pendingPayment': { readPendingPayment: () => null } };
    vm.runInNewContext('(function(require,module,exports){' + code + '\n})', { document: { title: '' } })(p => imports[p], module, module.exports);
    const model = new module.exports();
    assert.equal(model.signedInEmail(), 'current@example.test');
    profile({ email: 'different@example.test' });
    assert.equal(model.signedInEmail(), 'different@example.test');
    profile(null); assert.equal(model.signedInEmail(), '');
    model.connected(); await model.load(); model.disconnected(); assert.ok(calls.length > 0);
    // The only additional list option is includeInactive=true, never a caller email or ID.
    assert.ok(calls.every(args => args[0] === undefined && (args.length < 2 || args[1] === true)));
    assert.doesNotMatch(source, /shreya@example|this\.email|userEmail/);
    const html = fs.readFileSync(path.join(__dirname, '../src/ts/views', screen + '.html'), 'utf8');
    assert.match(html, /Signed in as<input type="email" readonly/);
    assert.match(html, /value: signedInEmail/);
  });
}
