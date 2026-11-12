// Every status source on a live player: each test status is checked false, its condition is set up in the
// game, and it is checked true (and false again where that is cheap). Also combinations, a dependency loop and
// the %headcount_status_<name>% placeholder.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { Vec3 } = require('vec3');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat } = L;
const S = `${WORK}`;
const HC = S + '/botserver/plugins/Headcount/';
const consoleOut = async (cmd, wait = 700) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const N = process.argv[2] || 'Whiskey';

const STATUSES = `
  t-ph:
    source: placeholder
    placeholder: "%headcount_points%"
    operator: greater-than
    value: "5"
  t-ph-regex:
    source: placeholder
    placeholder: "%headcount_points%"
    operator: regex
    value: "^1[0-9]$"
  t-tag:
    source: scoreboard-tag
    tag: hctest
  t-perm:
    source: permission
    permission: hc.status.test
  t-flag:
    source: flag
    flag: t-flag
  t-pvpdmg:
    source: pvp-damage
    direction: took
    seconds: 15
  t-dmg:
    source: recent-damage
    seconds: 10
  t-mobdmg:
    source: mob-damage
    seconds: 10
  t-nearby:
    source: nearby-player
    radius: 8
  t-hostile:
    source: nearby-hostile
    radius: 8
  t-health:
    source: health
    below: 15
  t-food:
    source: food
    below: 6
  t-fire:
    source: on-fire
  t-water:
    source: in-water
  t-lava:
    source: in-lava
  t-sneak:
    source: sneaking
  t-sprint:
    source: sprinting
  t-invis:
    source: invisible
  t-op:
    source: op
  t-bedrock:
    source: bedrock
  t-dead:
    source: dead
  t-riding:
    source: riding
  t-gm:
    source: gamemode
    modes: [creative]
  t-world:
    source: world
    worlds: [world_nether]
  t-joined:
    source: joined-recently
    seconds: 20
  t-potion:
    source: potion
    effect: speed
  t-y:
    source: y-level
    range: "<0"
  t-biome:
    source: biome
    biomes: [plains]
  t-holding:
    source: holding
    materials: [DIAMOND_SWORD]
  t-time:
    source: world-time
    from: 13000
    to: 23000
  t-afk:
    source: essentials
    check: afk
  t-god:
    source: essentials
    check: god
  t-muted:
    source: muted
  t-trade:
    source: in-trade
  t-hc:
    source: headcount
    value: points
    range: ">5"
  t-all:
    source: all-of
    statuses: [t-tag, t-perm]
  t-any:
    source: any-of
    statuses: [t-tag, "!t-perm"]
  t-not:
    source: not
    status: t-tag
  t-always:
    source: always
  t-never:
    source: never
  t-loop-a:
    source: not
    status: t-loop-b
  t-loop-b:
    source: not
    status: t-loop-a
`;

(async () => {
  const backup = fs.readFileSync(HC + 'statuses.yml', 'utf8');
  fs.writeFileSync(HC + 'statuses.yml', backup.replace('statuses:\n', 'statuses:' + STATUSES));
  const rl = await consoleOut('hca reload', 3000);
  record('loop: a status loop (t-loop-a <-> t-loop-b) is reported at load', /refers back to itself \(t-loop-[ab] -> t-loop-[ab] -> t-loop-[ab]\)/.test(rl),
    rl.split('\n').filter((l) => /t-loop/.test(l)).join(' | ').slice(0, 160));
  const w = await makeBot(N);
  const a = await makeBot('Alpha');
  await sleep(2500);
  const st = async (name) => {
    const out = await consoleOut(`hca status ${N} ${name}`, 600);
    const m = new RegExp(`${name}: (true|false)`).exec(out);
    return m ? m[1] === 'true' : null;
  };
  const flip = async (name, setOn, setOff) => {
    const before = await st(name);
    await setOn();
    const on = await st(name);
    let off = null;
    if (setOff) { await setOff(); off = await st(name); }
    record(`status ${name}: false, then true when set${setOff ? ', then false again' : ''}`, before === false && on === true && (!setOff || off === false), `${before} -> ${on}${setOff ? ' -> ' + off : ''}`);
  };
  serverConsole(`minecraft:clear ${N}`);
  serverConsole(`hca points ${N} set 0`);
  await L.setPvp(N, false);
  serverConsole('minecraft:time set 1000');
  serverConsole(`minecraft:tp Alpha ${N}`);
  serverConsole(`execute as Alpha at @s run minecraft:tp @s ~30 ~ ~`);
  await sleep(1500);

  const loopOut = await consoleOut(`hca status ${N} t-loop-a`, 600);
  record('status t-loop-a: disabled, so it never answers true', !/t-loop-a: true/.test(loopOut), loopOut.trim().split('\n').pop().slice(0, 120));
  record('status t-joined: true right after joining', (await st('t-joined')) === true);
  record('status t-always / t-never', (await st('t-always')) === true && (await st('t-never')) === false);
  record('status t-y: below y 0 on the flat world', (await st('t-y')) === true);
  record('status t-biome: plains', (await st('t-biome')) === true);
  record('status t-bedrock: false for a Java player', (await st('t-bedrock')) === false);
  await flip('t-ph', () => consoleOut(`hca points ${N} set 12`), () => consoleOut(`hca points ${N} set 3`));
  await consoleOut(`hca points ${N} set 0`);
  await flip('t-ph-regex', () => consoleOut(`hca points ${N} set 14`), () => consoleOut(`hca points ${N} set 4`));
  await flip('t-hc', () => consoleOut(`hca points ${N} set 9`), () => consoleOut(`hca points ${N} set 0`));
  await flip('t-tag', () => consoleOut(`minecraft:tag ${N} add hctest`), () => consoleOut(`minecraft:tag ${N} remove hctest`));
  await flip('t-perm', () => consoleOut(`lp user ${N} permission set hc.status.test true`, 1500), () => consoleOut(`lp user ${N} permission unset hc.status.test`, 1500));
  // combinations
  await consoleOut(`minecraft:tag ${N} add hctest`);
  const all1 = await st('t-all'), any1 = await st('t-any'), not1 = await st('t-not');
  await consoleOut(`lp user ${N} permission set hc.status.test true`, 1500);
  const all2 = await st('t-all');
  await consoleOut(`minecraft:tag ${N} remove hctest`);
  const any2 = await st('t-any'), not2 = await st('t-not');
  await consoleOut(`lp user ${N} permission unset hc.status.test`, 1500);
  record('status all-of / any-of with "!" / not', all1 === false && all2 === true && any1 === true && any2 === false && not1 === false && not2 === true,
    `all ${all1},${all2} any ${any1},${any2} not ${not1},${not2}`);
  await flip('t-flag', () => consoleOut(`hca flag ${N} set t-flag`), () => consoleOut(`hca flag ${N} clear t-flag`));
  await flip('t-dmg', () => consoleOut(`minecraft:damage ${N} 1`), null);
  await flip('t-health', () => consoleOut(`minecraft:damage ${N} 12`), () => consoleOut(`minecraft:effect give ${N} instant_health 1 5`, 900));
  await flip('t-food', async () => { serverConsole(`hca run ${N} ta-hunger`); serverConsole(`hca run ${N} ta-hunger`); await sleep(600); }, () => consoleOut(`hca run ${N} ta-feed`));
  await flip('t-fire', () => consoleOut(`hca run ${N} ta-fire`, 300), () => consoleOut(`execute at ${N} run minecraft:setblock ~ ~ ~ water`, 900));
  record('status t-water: in the water placed above', (await st('t-water')) === true);
  await consoleOut(`execute at ${N} run minecraft:setblock ~ ~ ~ air`, 600);
  serverConsole(`minecraft:effect give ${N} fire_resistance 30`);
  await sleep(1000);
  await flip('t-lava', () => consoleOut(`execute at ${N} run minecraft:setblock ~ ~ ~ lava`, 1800), () => consoleOut(`execute at ${N} run minecraft:setblock ~ ~ ~ air`, 1800));
  serverConsole(`minecraft:effect clear ${N}`);
  await consoleOut(`hca run ${N} ta-heal`);
  await flip('t-sneak', async () => { w.setControlState('sneak', true); await sleep(600); }, async () => { w.setControlState('sneak', false); await sleep(600); });
  await flip('t-invis', () => consoleOut(`minecraft:effect give ${N} invisibility 30`), () => consoleOut(`minecraft:effect clear ${N} invisibility`));
  await flip('t-potion', () => consoleOut(`minecraft:effect give ${N} speed 30`), () => consoleOut(`minecraft:effect clear ${N} speed`));
  await flip('t-op', () => consoleOut(`minecraft:op ${N}`), () => consoleOut(`minecraft:deop ${N}`));
  await flip('t-gm', () => consoleOut(`minecraft:gamemode creative ${N}`), () => consoleOut(`minecraft:gamemode survival ${N}`));
  await flip('t-holding', async () => { serverConsole(`minecraft:item replace entity ${N} weapon.mainhand with diamond_sword`); await sleep(700); },
    () => consoleOut(`minecraft:item replace entity ${N} weapon.mainhand with air`));
  await flip('t-time', () => consoleOut('minecraft:time set 14000'), () => consoleOut('minecraft:time set 1000'));
  await flip('t-nearby', () => consoleOut(`minecraft:tp Alpha ${N}`, 900), () => consoleOut(`execute as Alpha at @s run minecraft:tp @s ~30 ~ ~`, 900));
  await flip('t-riding', async () => { serverConsole(`execute at ${N} run minecraft:summon oak_boat ~ ~ ~ {Tags:["hcboat"]}`); await sleep(500); serverConsole(`minecraft:ride ${N} mount @e[tag=hcboat,limit=1]`); await sleep(900); },
    async () => { serverConsole(`minecraft:ride ${N} dismount`); serverConsole('minecraft:kill @e[tag=hcboat]'); await sleep(900); });
  await flip('t-afk', () => consoleOut(`afk ${N}`, 1000), () => consoleOut(`afk ${N}`, 1000));
  await flip('t-god', () => consoleOut(`god ${N} on`, 1000), () => consoleOut(`god ${N} off`, 1000));
  serverConsole('difficulty easy');
  await flip('t-hostile', async () => { serverConsole(`execute at ${N} run minecraft:summon zombie ~2 ~ ~ {NoAI:1b,Tags:["hczombie"],PersistenceRequired:1b}`); await sleep(1200); },
    async () => { serverConsole('minecraft:kill @e[tag=hczombie]'); await sleep(900); });
  await flip('t-mobdmg', async () => {
    serverConsole(`execute at ${N} run minecraft:summon zombie ~2 ~ ~ {NoAI:1b,Tags:["hczombie"],PersistenceRequired:1b}`);
    await sleep(800);
    serverConsole(`minecraft:damage ${N} 2 minecraft:mob_attack by @e[tag=hczombie,limit=1]`);
    await sleep(900);
  }, null);
  serverConsole('minecraft:kill @e[tag=hczombie]');
  serverConsole('difficulty peaceful');
  serverConsole(`untag ${N}`);             // the hit combat-tagged both: that blocks trades and teleports
  serverConsole('untag Alpha');
  await consoleOut(`hca run ${N} ta-heal`);
  // trade (PvP on, or a punishment from an earlier cycle end, would block it)
  await L.setPvp(N, false);
  await L.setPvp('Alpha', false);
  const tb = await st('t-trade');
  const mb = a.chatLog.length;
  w.chat('/trade Alpha');
  await waitChat(a, /wants to trade/, mb, 3000);
  a.chat('/trade accept');
  await sleep(1500);
  const tt = await st('t-trade');
  if (w.currentWindow) w.closeWindow(w.currentWindow);
  await sleep(1000);
  record('status t-trade: true while a trade is open', tb === false && tt === true && (await st('t-trade')) === false);
  // the moderation mute (LibertyBans)
  await flip('t-muted', () => consoleOut(`hca run ${N} ta-mute`, 2000), null);
  // the world
  await flip('t-world', () => consoleOut(`execute in minecraft:the_nether run minecraft:tp ${N} 0 100 0`, 2000), () => consoleOut(`execute in minecraft:overworld run minecraft:tp ${N} 0 -60 0`, 2000));
  // dead (checked before respawning)
  const write = w._client.write.bind(w._client);
  w._client.write = (name, params) => (name === 'client_command' ? undefined : write(name, params));   // hold back mineflayer's automatic respawn
  serverConsole(`minecraft:kill ${N}`);
  await sleep(800);
  record('status t-dead: true while dead, before respawning', (await st('t-dead')) === true);
  w._client.write = write;
  try { w.respawn(); } catch (e) { /* fine */ }
  await sleep(1500);
  // PvP damage: Alpha hits the player
  await L.setPvp(N, true);
  await L.setPvp('Alpha', true);
  serverConsole('difficulty easy');
  serverConsole('minecraft:give Alpha netherite_sword');
  serverConsole(`minecraft:tp Alpha ${N}`);
  await sleep(1500);
  const sword = a.inventory.items().find((i) => i.name === 'netherite_sword');
  if (sword) await a.equip(sword, 'hand');
  await sleep(4000);                        // past PvPManager's respawn protection from the t-dead check
  const before = await st('t-pvpdmg');
  const hp = async () => Number(((await consoleOut(`minecraft:data get entity ${N} Health`, 300)).match(/entity data: ([\d.]+)f/) || [])[1]);
  const h0 = await hp();
  for (let i = 0; i < 6 && (await hp()) >= h0; i++) {          // a swing can miss; keep going until one lands
    const target = Object.values(a.entities).find((e) => e.username === N);
    if (target) a.attack(target);
    await sleep(700);
  }
  record('status t-pvpdmg: false, then true after taking a PvP hit', before === false && (await st('t-pvpdmg')) === true, String(before));
  serverConsole('difficulty peaceful');
  await consoleOut(`hca run ${N} ta-heal`);
  await L.setPvp(N, false);
  await L.setPvp('Alpha', false);
  // placeholders and joined-recently timing out
  const ph = await consoleOut(`papi parse ${N} %headcount_status_t-always% %headcount_status_t-never%`);
  record('placeholder: %headcount_status_<name>% reads each status', /true false/.test(ph), ph.trim().split('\n').pop().slice(0, 100));
  await sleep(9000);
  record('status t-joined: false after its 20 s', (await st('t-joined')) === false);

  fs.writeFileSync(HC + 'statuses.yml', backup);
  await consoleOut('hca reload', 2500);
  w.quit(); a.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
