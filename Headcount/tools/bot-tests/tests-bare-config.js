// Config handling on the bare server (1.1): cycle.time-zone forms on a live server, options missing from a file
// using the bundled defaults, providers missing from integrations.yml meaning none, the 1-minute warning's title
// and boss bar (every variable filled in), a new player's sidebar, a live cycle end, and auto-switch when a plugin
// is added or removed between restarts. Run it last on the bare server: it ends a cycle and restarts the server.
const L = require('./lib');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat, waitFor } = L;
const HC = `${L.S}/botserver/plugins/Headcount/`;
const PLUGINS = `${L.S}/botserver/plugins/`;
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const RAW = /<\/?[a-z_]+(?::[^>]*)?>/;
const HERE = __dirname;

/** Stops the server, waits for its java process to go (found by its folder, as reset-bot.sh does), starts it again. */
async function restart() {
  serverConsole('stop');
  const running = () => {
    let pids = [];
    try { pids = execFileSync('pgrep', ['-f', 'paper-.*\\.jar']).toString().trim().split('\n'); } catch (e) { return false; }
    return pids.some((pid) => { try { return fs.readlinkSync(`/proc/${pid}/cwd`) === `${L.S}/botserver`; } catch (e) { return false; } });
  };
  for (let i = 0; i < 90 && running(); i++) await sleep(1000);
  execFileSync(path.join(HERE, 'restart-bot.sh'), { env: { ...process.env, HC_BOT_DIR: L.S }, stdio: 'ignore' });
  await sleep(2000);
}

(async () => {
  if (fs.readdirSync(PLUGINS).some((f) => f.startsWith('PlaceholderAPI'))) throw new Error('PlaceholderAPI is in the bare server\'s plugins/ (from an interrupted run): delete it and run reset-bot.sh');
  const cfg0 = fs.readFileSync(HC + 'config.yml', 'utf8');
  const int0 = fs.readFileSync(HC + 'integrations.yml', 'utf8');

  // --- time zones ------------------------------------------------------------------------------------------------
  const zone = async (z) => {
    fs.writeFileSync(HC + 'config.yml', cfg0.replace(/^(\s*)time-zone: .*$/m, `$1time-zone: "${z}"`));
    const out = await consoleOut('hca reload', 2500);
    return out + await consoleOut('hca cycle info');
  };
  let out = await zone('MST');
  record('time-zone MST reads as UTC-07:00 in /hca cycle info', /UTC-07:00\)/.test(out), (/\(([^()]*left[^()]*)\)/.exec(out) || [])[1]);
  out = await zone('gmt-7');
  record('time-zone gmt-7 reads as UTC-07:00', /UTC-07:00\)/.test(out));
  out = await zone('UTC+5:30');
  record('time-zone UTC+5:30 reads as UTC+05:30', /UTC\+05:30\)/.test(out));
  out = await zone('america/denver');
  record('time-zone america/denver reads as America/Denver', /America\/Denver\)/.test(out));
  out = await zone('nonsense');
  record('time-zone nonsense: a console warning, and UTC', /WARN.*time-zone.*nonsense|nonsense.*(not|isn't).*zone/i.test(out) && /, UTC\)/.test(out), out.split('\n').filter((l) => /nonsense|UTC\)/.test(l)).join(' | ').slice(0, 240));
  fs.writeFileSync(HC + 'config.yml', cfg0);
  await consoleOut('hca reload', 2500);

  // --- missing options use the bundled defaults --------------------------------------------------------------------
  const noReset = cfg0.replace(/\nreset:\n(?:(?:  .*|\s*#.*|)\n)+/, '\n');
  fs.writeFileSync(HC + 'config.yml', noReset);
  out = await consoleOut('hca reload', 2500);
  record('setup: config.yml without its reset section', !/\nreset:\n/.test(noReset));
  out = await consoleOut('hca reset hard');
  record('a missing reset section uses the defaults (3 confirmations, 60s)', /Confirmation 1\/3: .* within 60s/.test(out), out.trim().slice(-120));
  serverConsole('hca reset hard 99999');
  out = await consoleOut('hca selftest', 2500);
  record('selftest passes with options missing', !/FAIL|✗|problem/i.test(out.split('\n').filter((l) => /selftest|check|pass|fail/i.test(l)).join('\n')) || /all .*pass|0 problems/i.test(out), out.trim().slice(-200));
  fs.writeFileSync(HC + 'config.yml', cfg0);

  // providers missing from integrations.yml mean none
  const noProviders = int0
    .replace(/\neconomy:\n(?:(?:  .*|\s*#.*|)\n)+/, '\n')
    .replace(/\ndiscord:\n(?:(?:  .*|\s*#.*|)\n)+/, '\n')
    .replace(/\nbedrock:\n(?:(?:  .*|\s*#.*|)\n)+/, '\n');
  fs.writeFileSync(HC + 'integrations.yml', noProviders);
  await consoleOut('hca reload', 2500);
  out = await consoleOut('hca integrations', 1200);
  const line = (k) => (out.split('\n').find((l) => new RegExp(k, 'i').test(l)) || '').replace(/^.*INFO\]: /, '');
  record('setup: integrations.yml without economy, discord and bedrock', !/\neconomy:\n/.test(noProviders) && !/\nbedrock:\n/.test(noProviders));
  record('missing economy, discord and bedrock providers mean none', /none/i.test(line('money|economy')) && /none/i.test(line('discord')) && /none/i.test(line('bedrock')),
    [line('money|economy'), line('discord'), line('bedrock')].join(' | '));
  fs.writeFileSync(HC + 'integrations.yml', int0);
  await consoleOut('hca reload', 2500);

  // --- a new player's sidebar, and the 1-minute warning ----------------------------------------------------------------
  const p = await makeBot('Zulu' + String(Date.now() % 1000));
  await sleep(3000);
  record('sidebar.shown-for-new-players: a new player has the sidebar on', !!(p.scoreboard && p.scoreboard.sidebar) || Object.keys(p.scoreboards || {}).length > 0);
  const titles = [];
  const bars = [];
  p.on('title', (t) => titles.push(typeof t === 'string' ? t : JSON.stringify(t)));
  p.on('bossBarCreated', (b) => bars.push(b));
  const end = new Date(Date.now() + 75000);
  // setend reads a local date-time in the cycle's zone (UTC here); seconds are allowed
  const stamp = end.toISOString().slice(0, 19);
  out = await consoleOut(`hca cycle setend ${stamp}`, 1200);
  record('setup: the cycle ends in under two minutes', /setend|ends|moved/i.test(out), out.trim().slice(-120));
  const mark = p.chatLog.length;
  await waitFor(() => bars.length > 0, 125000, 1000);
  await sleep(1500);
  const barText = bars.length ? bars[0].title.toString() : '';
  record('1m warning: the title shows', titles.some((t) => /1 minute left/.test(t)), titles.join(' | ').slice(0, 120));
  record('1m warning: a boss bar reads "Cycle N ends in 0:SS" with the real number', /^Cycle \d+ ends in 0:[0-5]\d$/.test(barText.trim()), barText);
  record('1m warning: nothing in the title or boss bar is a raw tag', !RAW.test(barText) && !titles.some((t) => /&lt;|<cycle>|<countdown/.test(t)));
  // rejoining in the last minute gets the boss bar too
  const name = p.username;
  p.quit();
  await sleep(1500);
  const p2 = await makeBot(name);
  // the bar can arrive before the bot has spawned, so read mineflayer's list rather than wait for an event
  const bars2 = () => Object.values(p2.bossBars || {});
  await waitFor(() => bars2().length > 0, 8000, 300);
  record('1m warning: a player who rejoins in the last minute gets the boss bar', bars2().length > 0 && /Cycle \d+ ends in/.test(bars2()[0].title.toString()), bars2().length ? bars2()[0].title.toString() : 'none');
  const cyc = db("SELECT id FROM cycles WHERE state='active'")[0][0];
  await waitFor(() => db("SELECT COUNT(*) FROM cycles WHERE state='active'")[0][0] === 1 && db("SELECT id FROM cycles WHERE state='active'")[0][0] === cyc + 1, 90000, 1000);
  await sleep(2500);
  const all = p2.chatLog.join('\n');
  record('cycle end: the next cycle starts', db("SELECT id FROM cycles WHERE state='active'")[0][0] === cyc + 1);
  record('cycle end: results reach the player, with no raw tags', /Cycle \d+ (is over|ended|results)/i.test(all) && !p2.chatLog.some((l) => RAW.test(l)), p2.chatLog.filter((l) => RAW.test(l)).slice(0, 2).join(' || ').slice(0, 200) || all.slice(-200));
  void mark;
  p2.quit();
  await sleep(1000);

  // --- auto-switch -------------------------------------------------------------------------------------------------
  const papi = fs.readdirSync(`${HERE}/work/plugin-jars`).find((f) => f.startsWith('PlaceholderAPI'));
  fs.copyFileSync(`${HERE}/work/plugin-jars/${papi}`, PLUGINS + papi);
  await restart();
  let log = fs.readFileSync(`${L.S}/botserver.log`, 'utf8');
  record('auto-switch: adding PlaceholderAPI switches placeholders to it, with a console line', /placeholders\.provider is now 'placeholderapi' \(was 'included'\)/.test(log) && /provider: placeholderapi/.test(fs.readFileSync(HC + 'integrations.yml', 'utf8')),
    log.split('\n').filter((l) => /provider is now/.test(l)).join(' | ').slice(0, 200));
  fs.unlinkSync(PLUGINS + papi);
  await restart();
  log = fs.readFileSync(`${L.S}/botserver.log`, 'utf8');
  record('auto-switch: removing it hands placeholders back to included', /placeholders\.provider is now 'included'/.test(log) && /\nplaceholders:\n(?:  #.*\n)*  provider: included/.test(fs.readFileSync(HC + 'integrations.yml', 'utf8')),
    log.split('\n').filter((l) => /provider is now|placeholder/i.test(l)).join(' | ').slice(0, 200));
  fs.writeFileSync(HC + 'integrations.yml', fs.readFileSync(HC + 'integrations.yml', 'utf8').replace(/^auto-switch: true/m, 'auto-switch: false'));
  fs.copyFileSync(`${HERE}/work/plugin-jars/${papi}`, PLUGINS + papi);
  await restart();
  log = fs.readFileSync(`${L.S}/botserver.log`, 'utf8');
  record('auto-switch: false leaves the provider alone when a plugin is added', !/provider is now/.test(log) && /\nplaceholders:\n(?:  #.*\n)*  provider: included/.test(fs.readFileSync(HC + 'integrations.yml', 'utf8')));
  fs.unlinkSync(PLUGINS + papi);
  fs.writeFileSync(HC + 'integrations.yml', fs.readFileSync(HC + 'integrations.yml', 'utf8').replace(/^auto-switch: false/m, 'auto-switch: true'));
  await restart();
  log = fs.readFileSync(`${L.S}/botserver.log`, 'utf8');
  record('no Headcount warnings or errors on a plain start', !/WARN\]: \[Headcount\]|ERROR\]: \[Headcount\]|Headcount.*Exception/.test(log),
    log.split('\n').filter((l) => /WARN\]: \[Headcount\]|ERROR\]: \[Headcount\]/.test(l)).slice(0, 3).join(' | ').slice(0, 300));

  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e.stack); L.summary(); process.exit(2); });
