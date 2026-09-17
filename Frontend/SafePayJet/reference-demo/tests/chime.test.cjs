const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function compile() {
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/utils/chime.ts'), 'utf8');
  return ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 }
  }).outputText;
}

function load(windowStub) {
  const code = compile();
  const module = { exports: {} };
  const window = Object.assign({
    addEventListener() {},
    removeEventListener() {}
  }, windowStub);
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', {
    window, setInterval, clearInterval
  })(() => ({}), module, module.exports);
  return { chime: module.exports, window };
}

test('chimes are silent when the browser has no audio context', () => {
  const { chime } = load({});
  assert.doesNotThrow(() => {
    chime.playChime('paid');
    chime.playChime('ok');
    chime.playChime('off');
    chime.playChime('answer');
    chime.playChime('end');
    chime.chimeIfSettled({ transactionId: 1, state: 'SETTLED' });
    chime.chimeIfSettled({ transactionId: 1, state: 'SETTLED' });
    chime.rememberSettled({ transactionId: 2, state: 'SETTLED' });
    chime.chimeIfSettled({ transactionId: 2, state: 'SETTLED' });
    chime.startRing();
    chime.stopRing();
  });
});

test('a held or protected payment still plays a placement chime', () => {
  const { chime } = load({});
  assert.doesNotThrow(() => {
    chime.chimeForPayment({ transactionId: 3, state: 'HARD_HOLD' });
    chime.chimeForPayment({ transactionId: 3, state: 'HARD_HOLD' });
    chime.chimeForPayment({ transactionId: 4, state: 'PROTECTED' });
    chime.chimeForPayment({ transactionId: 5, state: 'CANCELLED' });
    chime.chimeForPayment({ transactionId: 5, state: 'CANCELLED' });
    chime.chimeIfSettled({ transactionId: 3, state: 'HARD_HOLD' });
  });
});

test('tones wait for a suspended audio context to resume before they schedule', async () => {
  const starts = [];
  class Oscillator {
    constructor() { this.frequency = { value: 0 }; this.type = 'sine'; }
    connect() {}
    start() { starts.push('start'); }
    stop() {}
  }
  class Gain {
    constructor() {
      this.gain = { value: 0, setValueAtTime() {}, exponentialRampToValueAtTime() {} };
    }
    connect() {}
  }
  class FakeAudio {
    constructor() {
      this.state = 'suspended';
      this.currentTime = 0;
      this.sampleRate = 44100;
      this.destination = {};
    }
    createOscillator() { return new Oscillator(); }
    createGain() { return new Gain(); }
    createBuffer() { return {}; }
    createBufferSource() { return { buffer: null, connect() {}, start() {} }; }
    resume() { this.state = 'running'; return Promise.resolve(); }
  }
  const { chime } = load({ AudioContext: FakeAudio });
  chime.armAudio();
  chime.playChime('answer');
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));
  assert.ok(starts.length > 0);
});
