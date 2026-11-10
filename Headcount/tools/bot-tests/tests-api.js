// The public API through a real addon plugin (HeadcountTestAddon, built from addon/ by setup.sh): every API
// method, a status source and an action type from another plugin, all 13 events, and the changeable events.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat, waitFor, openWindow } = L;
const S = `${WORK}`;
const HC = S + '/botserver/plugins/Headcount/';
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const N = process.argv[2] || 'Apex', M = process.argv[3] || 'Apex2';
const pts = (n) => db(`SELECT points FROM players WHERE name='${n}'`)[0][0];
(async () => {
  const startLog = logSince(0);
  record('addon: loads after Headcount and registers for all 13 events', /\[HCADDON\] ready: 13 events/.test(startLog));
  const sBackup = fs.readFileSync(HC + 'statuses.yml', 'utf8');
  const aBackup = fs.readFileSync(HC + 'actions.yml', 'utf8');
  fs.writeFileSync(HC + 'statuses.yml', sBackup.replace('statuses:\n', 'statuses:\n  in-arena-test:\n    source: in-arena\n    arena: red\n'));
  fs.writeFileSync(HC + 'actions.yml', aBackup.replace('lists:\n', 'lists:\n  tapi-tag:\n    - type: addon-tag\n      tag: addon-applied\n      duration: 5s\n'));
  const rl = await consoleOut('hca reload', 3000);
  record('addon: its status source and action type are accepted in the configs (no warnings)', !/WARN.*(in-arena|addon-tag|unknown source|unknown action)/i.test(rl), rl.split('\n').filter((l) => /WARN/.test(l)).join(' | ').slice(0, 160));
  const mark = logSize();
  const p = await makeBot(N);
  const a = await makeBot('Alpha');
  await sleep(3000);
  let ev = logSince(mark);
  for (const e of ['PlayerFirstJoinEvent', 'PlayerCycleJoinEvent', 'PlayerBecameActiveEvent']) record(`event: ${e} on a first join`, new RegExp(`EVENT ${e}[^\\n]*`).test(ev));

  // --- API methods ------------------------------------------------------------------------------------------
  const p0 = pts(N);
  const api = await consoleOut(`hcaddon ${N}`, 1500);
  const cyc = db(`SELECT id FROM cycles WHERE state='active'`)[0][0];
  record('api: currentCycle and cycleEndsAt', new RegExp(`currentCycle=${cyc} endsInFuture=true`).test(api));
  record('api: vaultCounts, previewScore, points', /vaultCounts=\[\d+, 27, 27\] previewScore=-?\d+ points=\d+/.test(api), (api.match(/vaultCounts[^\n]*/) || [''])[0]);
  record('api: addPoints adds and returns the new balance', new RegExp(`addPoints\\(3\\)=${p0 + 3}`).test(api) && pts(N) === p0 + 3);
  record('api: equippedTitle and hasStatus (false before the arena tag)', /hasStatus\(in-arena-test\)=false equippedTitle=null/.test(api));
  record('api: createHead makes a current economy head; headInfo reads it (and nothing from stone)', new RegExp(`owner=${N} cycle=${cyc} current=true source=addon plainStone=false`).test(api),
    (api.match(/createHead[^\n]*/) || [''])[0].slice(0, 140));
  record('api: runActionList runs a named list (and returns false for a missing one)', /runActionList\(ta-message\)=true runActionList\(nope\)=false/.test(api) && p.chatLog.some((m) => new RegExp(`TA-MESSAGE ${N}`).test(m)));

  // --- a status source and an action type from another plugin -------------------------------------------------
  const st = async (name) => /: true/.test((await consoleOut(`hca status ${N} ${name}`, 600)).split('\n').filter((l) => l.includes(name)).join(''));
  await consoleOut(`minecraft:tag ${N} add arena.red`);
  record('api: the addon\'s status source answers (in-arena)', await st('in-arena-test'));
  let m2 = logSize();
  await consoleOut(`hca run ${N} tapi-tag`, 1000);
  const tags1 = await consoleOut(`minecraft:tag ${N} list`);
  record('api: the addon\'s action type applies, with ActionAppliedEvent', /addon-applied/.test(tags1) && /EVENT ActionAppliedEvent/.test(logSince(m2)));
  await sleep(5500);
  const tags2 = await consoleOut(`minecraft:tag ${N} list`);
  record('api: and reverts when its duration ends, with ActionRevertedEvent', !/addon-applied/.test(tags2) && /EVENT ActionRevertedEvent/.test(logSince(m2)));

  // --- changeable events ------------------------------------------------------------------------------------
  await consoleOut(`minecraft:tag ${N} add addon-points`);
  const p1 = pts(N);
  m2 = logSize();
  await consoleOut(`hca points ${N} give 5`);
  record('event: PointsChangeEvent can change the amount (5 became 50)', pts(N) === p1 + 50 && /EVENT PointsChangeEvent/.test(logSince(m2)), `${p1} -> ${pts(N)}`);
  await consoleOut(`minecraft:tag ${N} remove addon-points`);
  await consoleOut(`minecraft:tag ${N} add addon-noshop`);
  const p2 = pts(N);
  let w = await openWindow(p, '/hc shop common');
  const mp = p.chatLog.length;
  const m3 = logSize();
  const conf = new Promise((r) => p.once('windowOpen', r));
  await p.clickWindow(10, 0, 0);
  const cw = await Promise.race([conf, sleep(1500).then(() => null)]);
  if (cw && cw.type === 'minecraft:generic_9x3') { await sleep(500); await p.clickWindow(11, 0, 0); }   // this server confirms cheap purchases
  await sleep(1200);
  record('event: ShopPurchaseEvent can cancel a purchase', pts(N) === p2 && !p.chatLog.slice(mp).some((m) => /Bought/.test(m)) && /EVENT ShopPurchaseEvent/.test(logSince(m3)));
  if (p.currentWindow) p.closeWindow(p.currentWindow);
  await consoleOut(`minecraft:tag ${N} remove addon-noshop`);
  await consoleOut(`minecraft:tag ${N} add addon-score42`);
  const ms = p.chatLog.length;
  p.chat('/hc score');
  await waitChat(p, /If the cycle ended now/, ms, 2500);
  const preview = Number((p.chatLog.slice(ms).join(' ').match(/now: score (-?\d+)/) || [])[1]);
  record('event: ScoreCalculatedEvent can set the score (preview shows 42)', preview === 42, String(preview));

  // --- kill: KillHeadDropEvent doubles the drop -----------------------------------------------------------------
  serverConsole('minecraft:tag Alpha add addon-double');
  serverConsole('minecraft:give Alpha netherite_sword');
  serverConsole(`pvp ${N} on`);
  serverConsole('pvp Alpha on');
  serverConsole('difficulty easy');
  await sleep(1000);
  await a.equip(a.inventory.items().find((i) => i.name === 'netherite_sword'), 'hand');
  serverConsole(`minecraft:tp ${N} Alpha`);
  await sleep(1200);
  for (let i = 0; i < 16 && p.health > 0; i++) {
    const t = Object.values(a.entities).find((e) => e.username === N);
    if (t) a.attack(t);
    await sleep(650);
  }
  await sleep(1500);
  const kill = (fs.readdirSync(HC + 'audit').filter((f) => f.endsWith('.log')).map((f) => fs.readFileSync(HC + 'audit/' + f, 'utf8')).join('').match(new RegExp(`KILL_DROP killer=Alpha[^\\n]*victim=${N}[^\\n]*`, 'g')) || []).pop() || '';
  const heads = ((/heads=(\S+)/.exec(kill) || [])[1] || '').split(',').filter(Boolean).length;
  record('event: KillHeadDropEvent can change the amount (1 became 2)', heads === 2, `heads=${heads}`);
  try { p.respawn(); } catch (e) { /* fine */ }
  serverConsole('difficulty peaceful');
  serverConsole('minecraft:tag Alpha remove addon-double');
  serverConsole(`pvp ${N} off`);
  serverConsole('pvp Alpha off');
  serverConsole(`untag ${N}`);
  serverConsole('untag Alpha');
  await sleep(4000);

  // --- trade: TradeCompleteEvent --------------------------------------------------------------------------------
  serverConsole(`minecraft:give ${N} dirt 1`);
  serverConsole(`minecraft:tp Alpha ${N}`);
  await sleep(1000);
  m2 = logSize();
  const mb = a.chatLog.length;
  p.chat('/trade Alpha');
  await waitChat(a, /wants to trade/, mb, 3000);
  const pa = new Promise((r) => p.once('windowOpen', r));
  a.chat('/trade accept');
  w = await Promise.race([pa, sleep(4000).then(() => null)]);
  await sleep(700);
  const dirt = w ? w.slots.findIndex((s, i) => i >= w.inventoryStart && s && s.name === 'dirt') : -1;
  if (dirt >= 0) await p.clickWindow(dirt, 0, 1);
  await sleep(700);
  await p.clickWindow(13, 0, 0);
  await sleep(400);
  await a.clickWindow(13, 0, 0);
  await sleep(4500);
  record('event: TradeCompleteEvent when a trade goes through', /EVENT TradeCompleteEvent/.test(logSince(m2)));

  // --- cycle end: CycleEnd/CycleStart, ScoreCalculated at the end, OutcomeApply cancelled ----------------------
  const q = await makeBot(M);
  await sleep(2500);
  serverConsole(`minecraft:tag ${M} add addon-nooutcome`);
  serverConsole(`hca run ${M} to-neg`);
  await sleep(800);
  m2 = logSize();
  await consoleOut('hca cycle end confirm', 5000);
  ev = logSince(m2);
  record('event: CycleEndEvent and CycleStartEvent at a cycle end', /EVENT CycleEndEvent/.test(ev) && /EVENT CycleStartEvent/.test(ev));
  const r1 = db(`SELECT score FROM results WHERE cycle=${cyc} AND name='${N}'`)[0];
  record('event: ScoreCalculatedEvent sets the final score too (42 stored)', r1 && r1[0] === 42, JSON.stringify(r1));
  const r2 = db(`SELECT score, outcomes FROM results WHERE cycle=${cyc} AND name='${M}'`)[0];
  record('event: OutcomeApplyEvent can cancel an outcome (score -10 or lower, no penalty applied)', r2 && r2[0] <= -10 && !/penalty/.test(r2[1] || '') && /EVENT OutcomeApplyEvent[^\n]*getRuleId=penalty-10/.test(ev),
    JSON.stringify(r2));
  const seen = new Set((logSince(0).match(/EVENT (\w+)/g) || []).map((x) => x.slice(6)));
  record('events: all 13 were fired during this run', seen.size === 13, [...seen].sort().join(','));

  fs.writeFileSync(HC + 'statuses.yml', sBackup);
  fs.writeFileSync(HC + 'actions.yml', aBackup);
  serverConsole(`minecraft:tag ${N} remove addon-score42`);
  serverConsole(`minecraft:tag ${N} remove arena.red`);
  await consoleOut('hca reload', 2500);
  p.quit(); a.quit(); q.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
