// The moderation providers other than LibertyBans (tested in tests-actions): AdvancedBan, the generic
// command templates, and Headcount's own vanilla fallback. Each: kick, warn, mute, and a temporary ban.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, makeBot, waitChat } = L;
const HC = `${WORK}/botserver/plugins/Headcount/`;
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const tryJoin = (name) => Promise.race([makeBot(name).then((x) => { x.quit(); return 'joined'; }).catch(() => 'refused'), sleep(6000).then(() => 'refused')]);
(async () => {
  const backup = fs.readFileSync(HC + 'integrations.yml', 'utf8');
  const a = await makeBot('Alpha');
  // Fresh names each run: the moderation plugins keep their own history (AdvancedBan auto-kicks on a second warning).
  const run = String(Date.now() % 100000);
  for (const [provider, name, expect] of [['advancedban', 'MA' + run, /ban=AdvancedBan/], ['commands', 'MC' + run, /kick=commands/], ['vanilla', 'MV' + run, /ban=vanilla|ban=Vanilla|mute=Headcount|vanilla/i]]) {
    fs.writeFileSync(HC + 'integrations.yml', backup.replace("Headcount's own mutes and jails.\n  provider: auto\n", `Headcount's own mutes and jails.\n  provider: ${provider}\n`));
    if (!fs.readFileSync(HC + 'integrations.yml', 'utf8').includes(`  provider: ${provider}\n`)) throw new Error('provider edit did not apply');
    await consoleOut('hca reload', 2500);
    const integ = await consoleOut('hca integrations', 1200);
    const modLine = integ.split('\n').find((l) => /Moderation/.test(l)) || '';
    record(`${provider}: chosen as the moderation provider`, expect.test(modLine), modLine.replace(/^.*Moderation/, 'Moderation').slice(0, 140));
    let p = await makeBot(name);
    await sleep(2500);
    const mp = p.chatLog.length;
    serverConsole(`hca run ${name} ta-warn`);
    record(`${provider}: warn reaches the player`, await waitChat(p, /warn/i, mp, 3000), p.chatLog.slice(mp).join(' | ').slice(0, 100));
    serverConsole(`hca run ${name} ta-mute`);
    await sleep(1500);
    const ma = a.chatLog.length;
    p.chat(`mute-check-${provider}`);
    await sleep(1500);
    record(`${provider}: a mute blocks the player's chat`, !a.chatLog.slice(ma).some((m) => m.includes(`mute-check-${provider}`)));
    let kicked = null;
    p.once('kicked', (r) => { kicked = JSON.stringify(r); });
    serverConsole(`hca run ${name} ta-kick`);
    await sleep(2500);
    record(`${provider}: kick disconnects the player`, !!kicked, String(kicked).slice(0, 80));
    await sleep(1500);
    p = await makeBot(name);
    await sleep(2000);
    kicked = null;
    p.once('kicked', (r) => { kicked = JSON.stringify(r); });
    serverConsole(`hca run ${name} ta-ban`);   // 10 s
    await sleep(2500);
    const during = await tryJoin(name);
    record(`${provider}: a temporary ban kicks and keeps the player out`, !!kicked && during === 'refused', `kicked=${!!kicked} rejoin=${during}`);
    await sleep(10000);
    const after = await tryJoin(name);
    record(`${provider}: the ban lifts when its time is up`, after === 'joined');
    await sleep(1000);
  }
  fs.writeFileSync(HC + 'integrations.yml', backup);
  await consoleOut('hca reload', 2500);
  a.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
