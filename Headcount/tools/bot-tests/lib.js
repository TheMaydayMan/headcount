// Test harness helpers: bots, console, server log, database.
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');

// HC_BOT_DIR holds botserver/, bot-console.in and botserver.log (default: ./work next to these scripts).
const S = process.env.HC_BOT_DIR || require('path').join(__dirname, 'work');
const CONSOLE = S + '/bot-console.in';
const LOG = S + '/botserver.log';
const DB = S + '/botserver/plugins/Headcount/headcount.db';

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function record(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
}

function serverConsole(cmd) {
  fs.appendFileSync(CONSOLE, cmd + '\n');
}

/**
 * Sets a player's PvP from the console and checks it took. PvPManager's console /pvp <player> on|off flips the state
 * instead of setting it, so a player an earlier suite left in that state would end up the wrong way round. And by the
 * later suites, cycle ends have given the regular bots punishments like Wanted, which locks PvP on; if a toggle
 * doesn't take, the player's effects are cleared first.
 */
async function setPvp(name, on) {
  for (let i = 0; i < 4; i++) {
    const m = logSize();
    serverConsole(`hca status ${name} in-pvp`);
    await sleep(500);
    const now = /in-pvp: true/.test(logSince(m));
    if (now === on) return true;
    if (i === 1) { serverConsole(`hca effects ${name} remove all`); await sleep(600); }
    serverConsole(`pvp ${name} ${on ? 'on' : 'off'}`);
    await sleep(500);
  }
  return false;
}

function logSize() {
  return fs.statSync(LOG).size;
}

function logSince(offset) {
  const buf = fs.readFileSync(LOG);
  return buf.slice(offset).toString('utf8');
}

function db(sql) {
  const out = execFileSync('python3', ['-c', `
import sqlite3, json, sys
db = sqlite3.connect('file:${DB}?mode=ro', uri=True)
print(json.dumps(db.execute(sys.argv[1]).fetchall()))`, sql]);
  return JSON.parse(out.toString());
}

async function waitFor(pred, timeout = 5000, step = 100) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    try {
      if (await pred()) return true;
    } catch (e) { /* keep waiting */ }
    await sleep(step);
  }
  return false;
}

function makeBot(username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25566, username, version: '26.1', auth: 'offline' });
    // ViaBackwards rejects this mineflayer version's routine movement packets; the tests don't need walking.
    const write = bot._client.write.bind(bot._client);
    bot._client.write = (name, params) => {
      if (['position', 'look', 'flying'].includes(name)) return;
      return write(name, params);
    };
    bot.chatLog = [];
    bot.on('messagestr', (m) => bot.chatLog.push(m));
    bot.on('kicked', (r) => console.log(`[${username}] KICKED ${JSON.stringify(r).slice(0, 150)}`));
    bot.on('error', (e) => console.log(`[${username}] ERROR ${e.message}`));
    bot.once('spawn', () => { bot.physicsEnabled = false; resolve(bot); });
    setTimeout(() => reject(new Error(username + ' did not spawn')), 30000);
  });
}

function chatSince(bot, mark) {
  return bot.chatLog.slice(mark).join('\n');
}

async function waitChat(bot, regex, mark, timeout = 5000) {
  return waitFor(() => regex.test(chatSince(bot, mark)), timeout);
}

async function openWindow(bot, command, timeout = 5000) {
  if (bot.currentWindow) {
    bot.closeWindow(bot.currentWindow);
    await sleep(300);
  }
  const p = new Promise((resolve) => {
    const t = setTimeout(() => resolve(null), timeout);
    bot.once('windowOpen', (w) => { clearTimeout(t); resolve(w); });
  });
  bot.chat(command);
  const w = await p;
  await sleep(400);
  return w;
}

/** Sends a raw container click, so modes mineflayer does not simulate (drop, middle, double, drag) can be tested. */
function rawClick(bot, window, slot, button, mode) {
  bot._client.write('window_click', {
    windowId: window.id,
    stateId: window.stateId ?? bot.inventory.stateId ?? 0,
    slot,
    mouseButton: button,
    mode,
    changedSlots: [],
    cursorItem: null,
  });
}

function itemName(item) {
  if (!item) return null;
  return item.customName || item.displayName || item.name;
}

function summary() {
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length} passed, ${failed.length} failed`);
  for (const f of failed) console.log('  FAILED: ' + f.name + (f.detail ? ' -- ' + f.detail : ''));
  return failed.length;
}

module.exports = { S, sleep, record, serverConsole, setPvp, logSize, logSince, db, waitFor, makeBot, chatSince, waitChat, openWindow, rawClick, itemName, summary, results };
