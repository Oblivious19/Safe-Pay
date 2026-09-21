"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "..");

test("installed foundation matches the pinned project versions", () => {
  const manifest = require("../package.json");
  for (const name of ["@oracle/oraclejet", "@oracle/oraclejet-core-pack", "@oracle/ojet-cli", "@oracle/oraclejet-tooling", "typescript", "@fontsource/manrope"]) {
    const installed = JSON.parse(fs.readFileSync(path.join(root, "node_modules", name, "package.json"), "utf8"));
    assert.equal(installed.version, manifest.dependencies[name] ?? manifest.devDependencies[name], name);
  }
});
test("installed JSON codec preserves long IDs and exact monetary lexemes", () => {
  const { parse, stringify } = require("lossless-json");
  const source = '{"id":9007199254740993,"maximum":9999999999999999.99,"boundary":100000.01,"decimal":0.10}';
  assert.equal(stringify(parse(source)), source);
});
test("STOMP export and local browser bundle files exist without opening a connection", () => {
  assert.equal(typeof require("@stomp/stompjs").Client, "function");
  for (const file of ["lossless-json/lib/umd/lossless-json.js", "@stomp/stompjs/bundles/stomp.umd.js", "@stomp/stompjs/bundles/stomp.umd.min.js", "@fontsource/manrope/LICENSE"]) {
    assert.ok(fs.existsSync(path.join(root, "node_modules", file)), file);
  }
});
