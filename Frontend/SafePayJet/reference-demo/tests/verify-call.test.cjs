const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');

class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
const held = {
  transactionId: 321, transactionRef: 'DEMO-321', amount: 150000, beneficiaryName: 'Rohan Gupta',
  state: 'HARD_HOLD', riskTier: 'VERY_HIGH', verification: 'NONE', verificationSentTo: 'an•••@safepay.test'
};
const settled = { ...held, state: 'SETTLED', verification: 'VERIFIED' };

function fixture(service) {
  const calls = [], settledRows = [], redirects = [], sounds = [], module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/viewModels/verifyCall.ts'), 'utf8');
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 }
  }).outputText;
  const imports = {
    knockout: ko,
    '../services/apiError': { ApiError },
    '../services/types': {},
    '../services/demoSession': { demoHoldCode: () => '' },
    '../utils/chime': {
      armAudio() {},
      playChime: kind => sounds.push(['chime', kind]),
      chimeForPayment: tx => { if (tx && tx.state === 'SETTLED') sounds.push(['chime', 'ok']); },
      chimeIfSettled: tx => { if (tx && tx.state === 'SETTLED') sounds.push(['chime', 'ok']); },
      rememberSettled() {},
      startRing: () => sounds.push(['ring']),
      stopRing: () => sounds.push(['stop'])
    },
    '../services/transactionService': {
      newIdempotencyKey: () => 'call-key',
      transactionService: {
        requestVerification: async (id) => { calls.push(['send', id]); return service.send(); },
        verifyHold: async (id, code, key) => { calls.push(['verify', id, code, key]); return service.verify(code); }
      }
    }
  };
  const ticks = [];
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', {
    setInterval: (fn) => { ticks.push(fn); return ticks.length; }, clearInterval: () => {},
    window: { location: { replace: (url) => redirects.push(url) } }
  })(p => imports[p], module, module.exports);
  const call = new module.exports.VerifyCallModel(row => settledRows.push(row));
  return { call, calls, settledRows, redirects, sounds, tick: () => ticks.forEach(fn => fn()) };
}

// The entry auto-submits from a subscription, so let its promise chain finish.
const flush = async () => { for (let i = 0; i < 5; i++) await new Promise(resolve => setImmediate(resolve)); };

const challenge = (code = '424242', seconds = 600) => ({
  transactionId: 321, sentTo: 'an•••@safepay.test', attemptsLeft: 3,
  expiresAt: new Date(Date.now() + seconds * 1000).toISOString(), simulatedCode: code
});

test('the call rings before it asks for anything', () => {
  const f = fixture({ send: () => challenge() });
  f.call.ring(held);
  assert.equal(f.call.open(), true);
  assert.equal(f.call.phase(), 'ringing');
  assert.equal(f.call.ringing(), true);
  assert.match(f.call.script(), /Incoming verification call/);
  assert.equal(f.call.payee(), 'Rohan Gupta');
  assert.equal(f.calls.length, 0);
  assert.deepEqual(f.sounds, [['ring']]);
});

test('a settled payment is never offered another call', () => {
  const f = fixture({ send: () => challenge() });
  f.call.ring(settled);
  assert.equal(f.call.open(), false);
  assert.deepEqual(f.sounds, []);
});

test('answering asks the bank for a code and reads out where it went', async () => {
  const f = fixture({ send: () => challenge() });
  f.call.ring(held);
  await f.call.accept();
  assert.deepEqual(f.calls, [['send', 321]]);
  assert.equal(f.call.phase(), 'connected');
  assert.equal(f.call.live(), true);
  assert.equal(f.call.sentTo(), 'an•••@safepay.test');
  assert.equal(f.call.inboxCode(), '424242');
  assert.match(f.call.script(), /key in the 6-digit code we sent to an•••@safepay.test/);
  assert.equal(f.call.attemptsLeft(), 3);
  assert.ok(f.sounds.some(item => item[0] === 'chime' && item[1] === 'answer'));
});

test('a complete code is submitted on its own and settles the payment', async () => {
  const f = fixture({ send: () => challenge(), verify: () => settled });
  f.call.ring(held);
  await f.call.accept();
  f.call.code('424242');
  await flush();
  assert.deepEqual(f.calls[1], ['verify', 321, '424242', 'call-key']);
  assert.equal(f.call.phase(), 'verified');
  assert.equal(f.call.done(), true);
  assert.deepEqual(f.settledRows, [settled]);
  assert.equal(f.call.code(), '');
  assert.ok(f.sounds.some(item => item[0] === 'chime' && item[1] === 'ok'));
});

test('non-digits never reach the service and a short code is refused locally', async () => {
  const f = fixture({ send: () => challenge() });
  f.call.ring(held);
  await f.call.accept();
  f.call.code('12ab34');
  assert.equal(f.call.code(), '1234');
  assert.equal(f.call.codeDots().filter(Boolean).length, 4);
  await f.call.submit();
  assert.match(f.call.error(), /6-digit code/);
  assert.equal(f.calls.length, 1);
});

test('a wrong code keeps the line open, sends a new code and says so on the card', async () => {
  let sent = 0;
  const f = fixture({
    send: () => { sent += 1; return challenge(sent === 1 ? '424242' : '737373'); },
    verify: () => { throw new ApiError(400, 'That code is not right. 2 attempts left.'); }
  });
  f.call.ring(held);
  await f.call.accept();
  f.call.code('111111');
  await flush();
  assert.equal(f.call.phase(), 'connected');
  assert.equal(f.call.attemptsLeft(), 3);
  assert.match(f.call.error(), /not right/);
  assert.match(f.call.notice(), /A new code has been sent/);
  assert.equal(f.call.inboxCode(), '737373');
  assert.equal(f.call.code(), '');
  assert.equal(sent, 2);
  assert.deepEqual(f.settledRows, []);
  assert.ok(f.sounds.some(item => item[0] === 'chime' && item[1] === 'off'));
});

test('the last wrong code ends verification until a new one is sent', async () => {
  const f = fixture({
    send: () => challenge(),
    verify: () => { throw new ApiError(400, 'Too many incorrect codes.'); }
  });
  f.call.ring(held);
  await f.call.accept();
  f.call.attemptsLeft(1);
  f.call.code('111111');
  await flush();
  assert.equal(f.call.phase(), 'failed');
  assert.equal(f.call.attemptsLeft(), 0);

  f.call.code(''); // a new code reopens the entry
  await f.call.resend();
  assert.equal(f.call.phase(), 'connected');
  assert.equal(f.call.attemptsLeft(), 3);
});

test('an expired code fails the line and is reported plainly', async () => {
  const f = fixture({
    send: () => challenge(),
    verify: () => { throw new ApiError(410, 'That code has expired. Ask for a new one.'); }
  });
  f.call.ring(held);
  await f.call.accept();
  f.call.code('424242');
  await flush();
  assert.equal(f.call.phase(), 'failed');
  assert.match(f.call.error(), /expired/);
  assert.equal(f.call.attemptsLeft(), 0);
});

test('the code validity clock counts down and closes the entry at zero', async () => {
  const f = fixture({ send: () => challenge('424242', 1) });
  f.call.ring(held);
  await f.call.accept();
  f.tick();
  assert.match(f.call.codeClock(), /Code valid for 0:0[01]/);
  f.call.code('42');
  await new Promise(resolve => setTimeout(resolve, 1100));
  f.tick();
  assert.equal(f.call.codeClock(), 'Code expired');
  assert.equal(f.call.phase(), 'failed');
  assert.match(f.call.error(), /expired/);
});

test('a failure to send a code is explained rather than silently dropped', async () => {
  const f = fixture({ send: () => { throw new ApiError(500, 'boom'); } });
  f.call.ring(held);
  await f.call.accept();
  assert.equal(f.call.phase(), 'failed');
  assert.equal(f.call.error(), 'We could not send a verification code. Please try again.');
});

test('an expired session sends the customer back to sign in', async () => {
  const f = fixture({ send: () => { throw new ApiError(401, 'gone'); } });
  f.call.ring(held);
  await f.call.accept();
  assert.deepEqual(f.redirects, ['/login?reason=session-expired']);
});

test('an already decided payment closes the call instead of offering a new code', async () => {
  const f = fixture({
    send: () => challenge(),
    verify: () => { throw new ApiError(409, 'This payment has already been decided.'); }
  });
  f.call.ring(held);
  await f.call.accept();
  f.call.code('424242');
  await flush();
  assert.equal(f.call.phase(), 'closed');
  assert.equal(f.call.closed(), true);
  assert.equal(f.call.live(), false);
  assert.equal(f.call.inboxCode(), '');
  assert.match(f.call.error(), /already been decided/);
  assert.match(f.call.script(), /already been decided/);
});

test('hanging up stops the ring and closes the call', () => {
  const f = fixture({ send: () => challenge() });
  f.call.ring(held);
  f.call.hangUp();
  assert.equal(f.call.open(), false);
  assert.equal(f.call.phase(), 'ended');
  assert.ok(f.sounds.some(item => item[0] === 'stop'));
  assert.ok(f.sounds.some(item => item[0] === 'chime' && item[1] === 'end'));
  f.call.dispose();
  f.call.code('424242');
  assert.equal(f.calls.length, 0);
});
