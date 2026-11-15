// Headcount's own providers (1.1) on the bare server: permissions (nodes saved, applied again on join, value:false
// beating op, removal), the jail (moved in, pulled back, allowed commands, release), the mute's blocked commands,
// AFK, placeholders (included and none), the vault title's <score>, and a sweep for raw MiniMessage tags.
const L = require('./lib');
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat, waitFor, openWindow } = L;
const HC = `${L.S}/botserver/plugins/Headcount/`;
const consoleOut = async (cmd, wait = 800) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const statusOf = async (p, st) => { const o = await consoleOut(`hca status ${p} ${st}`, 600); const m = new RegExp(`${st}: (\\S+)`).exec(o); return m ? m[1] : null; };
const recent = (bot, mark) => bot.chatLog.slice(mark).join(' | ');
const N = process.argv[2] || 'Inca';
const RAW = /<\/?(?:red|green|gray|gold|yellow|white|aqua|dark_\w+|bold|italic|cycle|player|score|points|unique|time_left|prefix|reason|seconds|state|target|other)>/;

(async () => {
  const intBackup = fs.readFileSync(HC + 'integrations.yml', 'utf8');
  const actBackup = fs.readFileSync(HC + 'actions.yml', 'utf8');
  const cfgBackup = fs.readFileSync(HC + 'config.yml', 'utf8');
  const stBackup = fs.readFileSync(HC + 'statuses.yml', 'utf8');
  // there's no jailed or muted status by default; these use the player-state and muted sources
  fs.writeFileSync(HC + 'statuses.yml', stBackup.replace('statuses:\n', 'statuses:\n  jailed:\n    source: player-state\n    check: jailed\n  muted:\n    source: muted\n', 1));
  const lists = [
    '  t-perm-give:\n    - type: permission\n      permission: headcount.admin.cycle\n      duration: 10m\n',
    '  t-perm-deny:\n    - type: permission\n      permission: headcount.admin.reset.hard\n      value: false\n      duration: 5m\n',
    '  t-jail:\n    - type: jail\n      reason: "Test jail"\n      duration: 40s\n',
    '  t-mute:\n    - type: mute\n      reason: "Test mute"\n      duration: 20s\n',
    '  t-minus:\n    - type: score-bonus\n      amount: -10\n      duration: cycle\n',
  ].join('');
  fs.writeFileSync(HC + 'actions.yml', actBackup.replace('lists:\n', 'lists:\n' + lists, 1));
  fs.writeFileSync(HC + 'integrations.yml', intBackup
    .replace(/(included-jail:\n(?:\s*#.*\n)*\s*location: )""/, '$1"world,40,-60,40"')
    .replace(/(afk:\n(?:\s*#.*\n)*\s*after: )\S+/, '$110s'));
  await consoleOut('hca reload', 2500);
  let out = await consoleOut('hca integrations', 1200);
  record('integrations: on a server with no other plugins every job is included or none', /PvP: included|PvP: Headcount/i.test(out) && !/PvPManager|LuckPerms|Essentials/.test(out), out.replace(/\[\d\d:\d\d:\d\d INFO\]: /g, '').trim().slice(0, 400));

  const p = await makeBot(N);
  const op = await makeBot('Opal');
  await sleep(3000);
  serverConsole('op Opal');
  serverConsole(`minecraft:gamemode survival ${N}`);
  await sleep(800);

  // --- permissions ------------------------------------------------------------------------------------------------
  let mark = p.chatLog.length;
  p.chat('/hca cycle info');
  record('permissions: without the node, /hca cycle info is refused', await waitChat(p, /permission|Unknown or incomplete/i, mark, 1500), recent(p, mark).slice(0, 80));
  await consoleOut(`hca run ${N} t-perm-give`, 1500);
  mark = p.chatLog.length;
  p.chat('/hca cycle info');
  record('permissions: a permission action gives the node', await waitChat(p, /Cycle \d+ \(run/, mark, 2000), recent(p, mark).slice(0, 100));
  const uuid = db(`SELECT uuid FROM players WHERE name='${N}'`)[0][0];
  record('permissions: the node is saved in included_permissions', db(`SELECT value FROM included_permissions WHERE uuid='${uuid}' AND node='headcount.admin.cycle'`).length === 1);
  p.quit();
  await sleep(1500);
  const p2 = await makeBot(N);
  await sleep(2500);
  mark = p2.chatLog.length;
  p2.chat('/hca cycle info');
  record('permissions: after rejoining, the node is applied again', await waitChat(p2, /Cycle \d+ \(run/, mark, 2000), recent(p2, mark).slice(0, 100));
  serverConsole(`hca effects ${N} remove all`);
  await sleep(1500);
  mark = p2.chatLog.length;
  p2.chat('/hca cycle info');
  record('permissions: removing the effect takes the node away', await waitChat(p2, /permission|Unknown or incomplete/i, mark, 2000) && db(`SELECT COUNT(*) FROM included_permissions WHERE uuid='${uuid}'`)[0][0] === 0, recent(p2, mark).slice(0, 80));
  await consoleOut('hca run Opal t-perm-deny', 1500);
  mark = op.chatLog.length;
  op.chat('/hca reset hard');
  record('permissions: value: false takes a node away from an op', await waitChat(op, /permission/i, mark, 2000) && !/Confirmation/.test(recent(op, mark)), recent(op, mark).slice(0, 100));
  serverConsole('hca effects Opal remove all');
  await sleep(1500);
  mark = op.chatLog.length;
  op.chat('/hca reset hard');
  record('permissions: once removed, the op can start a hard reset again', await waitChat(op, /Confirmation 1\/3/, mark, 2000), recent(op, mark).slice(0, 100));
  op.chat('/hca reset hard 99999');   // cancel it
  await sleep(500);
  out = await consoleOut(`hca run ${N} t-perm-give`, 1200);
  // group/prefix actions are skipped with a warning
  fs.writeFileSync(HC + 'actions.yml', fs.readFileSync(HC + 'actions.yml', 'utf8').replace('lists:\n', 'lists:\n  t-group:\n    - type: group\n      group: vip\n', 1));
  await consoleOut('hca reload', 2500);
  out = await consoleOut(`hca run ${N} t-group`, 1200);
  record('permissions: a group action is skipped with a warning naming LuckPerms or Vault', /group action was skipped because it needs LuckPerms or Vault/.test(out), out.trim().slice(-160));
  serverConsole(`hca effects ${N} remove all`);

  // --- jail ------------------------------------------------------------------------------------------------------
  serverConsole(`minecraft:tp ${N} 0 -60 0`);
  await sleep(1000);
  mark = p2.chatLog.length;
  await consoleOut(`hca run ${N} t-jail`, 1500);
  const at = p2.entity.position;
  record('jail: the player is moved to the jail location and told', Math.abs(at.x - 40) < 1.5 && Math.abs(at.z - 40) < 1.5 && await waitChat(p2, /You have been jailed/, mark, 1500), `${at} ${recent(p2, mark).slice(0, 80)}`);
  record('status: jailed is true', (await statusOf(N, 'jailed')) === 'true');
  serverConsole(`minecraft:tp ${N} 60 -60 60`);
  await sleep(2500);
  const back = p2.entity.position;
  record('jail: going more than 8 blocks away pulls them back', Math.abs(back.x - 40) < 1.5 && Math.abs(back.z - 40) < 1.5, `${back}`);
  mark = p2.chatLog.length;
  p2.chat('/spawn');
  p2.chat('/hc points');
  await sleep(1200);
  record('jail: other commands are blocked, allowed ones (/hc) work', /can't use that command in jail/.test(recent(p2, mark)) && /points/i.test(recent(p2, mark).replace(/can't use that command in jail/g, '')), recent(p2, mark).slice(0, 160));
  mark = p2.chatLog.length;
  await waitFor(() => /You have been released/.test(recent(p2, mark)), 45000, 1000);
  await sleep(800);
  const rel = p2.entity.position;
  record('jail: when it ends they are released back where they were', /You have been released/.test(recent(p2, mark)) && Math.abs(rel.x) < 1.5 && Math.abs(rel.z) < 1.5, `${rel}`);

  // --- mute ------------------------------------------------------------------------------------------------------
  await consoleOut(`hca run ${N} t-mute`, 1200);
  record('status: muted is true', (await statusOf(N, 'muted')) === 'true');
  let mo = op.chatLog.length;
  mark = p2.chatLog.length;
  p2.chat('hello from a muted player');
  p2.chat('/msg Opal psst');
  await sleep(1200);
  record('mute: chat and /msg are blocked', !/hello from a muted|psst/.test(recent(op, mo)) && /You are muted/.test(recent(p2, mark)), recent(p2, mark).slice(0, 120));
  mark = p2.chatLog.length;
  await waitFor(() => /no longer muted/.test(recent(p2, mark)), 25000, 1000);
  record('mute: it lifts on its own, with a message', /no longer muted/.test(recent(p2, mark)) && (await statusOf(N, 'muted')) === 'false');

  // --- AFK -----------------------------------------------------------------------------------------------------------
  p2.chat('/hc points');   // activity, so the AFK timer starts now
  await sleep(1500);
  mark = p2.chatLog.length;
  const t0 = Date.now();
  await waitFor(() => /You are now AFK/.test(recent(p2, mark)), 25000, 500);
  record('afk: it takes about afk.after (10s), checked every 5s', Date.now() - t0 >= 7000 && Date.now() - t0 <= 17000, `${Date.now() - t0} ms`);
  record('afk: standing still for afk.after (10s) makes a player AFK, and they\'re told', /You are now AFK/.test(recent(p2, mark)) && (await statusOf(N, 'afk')) === 'true');
  mark = p2.chatLog.length;
  p2.chat('back');
  record('afk: chatting ends it', await waitChat(p2, /no longer AFK/, mark, 2500) && (await statusOf(N, 'afk')) === 'false');
  fs.writeFileSync(HC + 'integrations.yml', fs.readFileSync(HC + 'integrations.yml', 'utf8').replace(/(afk:\n(?:\s*#.*\n)*\s*after: \S+\n(?:\s*#.*\n|\s*looking-counts: \S+\n)*\s*announce: )\S+/, '$1everyone'));
  await consoleOut('hca reload', 2500);
  mo = op.chatLog.length;
  await waitFor(() => new RegExp(`${N} is now AFK`).test(recent(op, mo)), 25000, 1000);
  record('afk: with announce: everyone, others see it', new RegExp(`${N} is now AFK`).test(recent(op, mo)), recent(op, mo).slice(0, 100));
  op.chat('/hc');   // keep Opal from going AFK as well
  await sleep(500);
  if (op.currentWindow) op.closeWindow(op.currentWindow);

  // --- placeholders --------------------------------------------------------------------------------------------------
  out = await consoleOut(`hca placeholders ${N}`, 1200);
  record('placeholders: included gives values for %headcount_*%', /%headcount_unique% = \d+/.test(out) && /%headcount_status_afk% = (true|false)/.test(out), out.trim().slice(0, 200));
  record('placeholders: status placeholders agree with /hca status', (/%headcount_status_in-pvp% = (\S+)/.exec(out) || [])[1] === (await statusOf(N, 'in-pvp')));
  fs.writeFileSync(HC + 'integrations.yml', fs.readFileSync(HC + 'integrations.yml', 'utf8').replace(/(\nplaceholders:\n(?:  #.*\n)*  provider: )\S+/, '$1none'));
  fs.writeFileSync(HC + 'config.yml', cfgBackup.replace('first-join-cycle:\n', 'first-join-cycle:\n      - message: "PH unique=%headcount_unique%"\n'));
  await consoleOut('hca reload', 2500);
  mark = p2.chatLog.length;
  serverConsole(`hca hook first-join-cycle ${N}`);
  record('placeholders: with provider none, %headcount_...% stays as plain text', await waitChat(p2, /PH unique=%headcount_unique%/, mark, 2000), recent(p2, mark).slice(0, 100));
  fs.writeFileSync(HC + 'integrations.yml', fs.readFileSync(HC + 'integrations.yml', 'utf8').replace(/(\nplaceholders:\n(?:  #.*\n)*  provider: )\S+/, '$1included'));
  await consoleOut('hca reload', 2500);
  mark = p2.chatLog.length;
  serverConsole(`hca hook first-join-cycle ${N}`);
  record('placeholders: back on included, it is filled in', await waitChat(p2, /PH unique=\d+/, mark, 2000), recent(p2, mark).slice(0, 100));
  fs.writeFileSync(HC + 'config.yml', cfgBackup);
  await consoleOut('hca reload', 2500);

  // --- the vault title's score ------------------------------------------------------------------------------------------
  const titles = [];
  p2.on('windowOpen', (w) => titles.push(typeof w.title === 'string' ? w.title : JSON.stringify(w.title)));
  const w = await openWindow(p2, '/hc');
  const nbtText = (t) => { try { const o = JSON.parse(t); return o && o.type ? require('prismarine-nbt').simplify(o) : o; } catch (e) { return t; } };
  const flat = (t) => { try { return new (require('prismarine-chat')(p2.registry))(nbtText(t)).toString(); } catch (e) { return String(t); } };
  record('vault title: it shows unique heads and the score', w && /\(1 unique, score -?\d+\)/.test(flat(titles[titles.length - 1] || '')), flat(titles[titles.length - 1] || ''));
  const scoreNow = Number((/score (-?\d+)/.exec(flat(titles[titles.length - 1] || '')) || [])[1]);
  await consoleOut(`hca run ${N} t-minus`, 800);
  await waitFor(() => titles.length > 1, 6000, 300);
  const t2 = flat(titles[titles.length - 1] || '');
  record('vault title: the score updates while the vault stays open', titles.length > 1 && new RegExp(`score ${scoreNow - 10}\\)`).test(t2), `${scoreNow} -> ${t2}`);
  const colored = JSON.stringify(nbtText(titles[titles.length - 1] || ''));
  record('vault title: a negative score is colored by score-colors.negative (red)', /"color":"red","text":"-\d+"|"text":"-\d+"[^}]*"color":"red"/.test(colored.replace(/,"(italic|bold|underlined|strikethrough|obfuscated)":\d/g, '')), colored.slice(0, 300));
  if (p2.currentWindow) p2.closeWindow(p2.currentWindow);
  serverConsole(`hca effects ${N} remove all`);

  // --- raw tags anywhere the bots saw -----------------------------------------------------------------------------------
  const seen = [...p2.chatLog, ...op.chatLog, ...titles.map(flat)].filter((l) => RAW.test(l));
  record('no raw MiniMessage tags in anything the bots received', seen.length === 0, seen.slice(0, 3).join(' || ').slice(0, 300));

  fs.writeFileSync(HC + 'integrations.yml', intBackup);
  fs.writeFileSync(HC + 'actions.yml', actBackup);
  fs.writeFileSync(HC + 'config.yml', cfgBackup);
  fs.writeFileSync(HC + 'statuses.yml', stBackup);
  await consoleOut('hca reload', 2500);
  p2.quit(); op.quit();
  await sleep(1000);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e.stack); L.summary(); process.exit(2); });
