// The rest of the status sources a bot can drive: sprinting, vanish (metadata and EssentialsX), a mob
// targeting the player, and nearby-player with a status filter.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, makeBot } = L;
const HC = `${WORK}/botserver/plugins/Headcount/`;
const consoleOut = async (cmd, wait = 700) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const N = process.argv[2] || 'Whiskey';
(async () => {
  const backup = fs.readFileSync(HC + 'statuses.yml', 'utf8');
  fs.writeFileSync(HC + 'statuses.yml', backup.replace('statuses:\n', `statuses:
  t-sprint:
    source: sprinting
  t-targeted:
    source: targeted-by-mob
    radius: 16
  t-near-tagged:
    source: nearby-player
    radius: 8
    status: t-tagged
  t-tagged:
    source: scoreboard-tag
    tag: hcnear
`));
  await consoleOut('hca reload', 2500);
  const w = await makeBot(N);
  const a = await makeBot('Alpha');
  await sleep(2500);
  const st = async (name) => { const m = new RegExp(`${name}: (true|false)`).exec(await consoleOut(`hca status ${N} ${name}`, 600)); return m ? m[1] === 'true' : null; };
  const flip = async (name, on, off) => {
    const b = await st(name); await on(); const x = await st(name); let y = null; if (off) { await off(); y = await st(name); }
    record(`status ${name}: false, then true${off ? ', then false' : ''}`, b === false && x === true && (!off || y === false), `${b} -> ${x}${off ? ' -> ' + y : ''}`);
  };
  serverConsole(`minecraft:tp Alpha ${N}`);
  serverConsole('execute as Alpha at @s run minecraft:tp @s ~30 ~ ~');
  await sleep(1000);
  // mineflayer only sends sprint from its physics loop (off here), so send the packet itself
  const sprint = (on) => w._client.write('entity_action', { entityId: w.entity.id, actionId: on ? 'start_sprinting' : 'stop_sprinting', jumpBoost: 0 });
  await flip('t-sprint', async () => { sprint(true); await sleep(700); }, async () => { sprint(false); await sleep(700); });
  await flip('vanished-metadata', () => consoleOut(`vanish ${N} on`, 1200), () => consoleOut(`vanish ${N} off`, 1200));
  await flip('vanished-essentials', () => consoleOut(`vanish ${N} on`, 1200), () => consoleOut(`vanish ${N} off`, 1200));
  await flip('vanished', () => consoleOut(`vanish ${N} on`, 1200), () => consoleOut(`vanish ${N} off`, 1200));
  await flip('t-near-tagged', async () => { serverConsole(`minecraft:tp Alpha ${N}`); await sleep(500); await consoleOut('minecraft:tag Alpha add hcnear', 800); },
    async () => { await consoleOut('minecraft:tag Alpha remove hcnear', 800); });
  serverConsole('difficulty easy');
  serverConsole(`minecraft:effect give ${N} resistance 30 4`);
  serverConsole('execute as Alpha at @s run minecraft:tp @s ~40 ~ ~');   // or the zombie may pick Alpha
  await sleep(800);
  await flip('t-targeted', async () => {
    serverConsole(`execute at ${N} run minecraft:summon zombie ~6 ~ ~ {Tags:["hcz"],PersistenceRequired:1b}`);
    for (let i = 0; i < 16 && !(await st('t-targeted')); i++) await sleep(500);   // mob AI picks a target in its own time
  },
    async () => { serverConsole('minecraft:kill @e[tag=hcz]'); await sleep(1200); });
  serverConsole('difficulty peaceful');
  serverConsole(`minecraft:effect clear ${N}`);
  fs.writeFileSync(HC + 'statuses.yml', backup);
  await consoleOut('hca reload', 2500);
  w.quit(); a.quit(); await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
