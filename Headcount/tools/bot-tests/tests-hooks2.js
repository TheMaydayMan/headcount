// The hooks that follow something a player did to someone else (hurt-pet, kill-pet, open-trapped-chest), their
// cooldowns and who they skip, and pvp.pet-protection in integrations.yml.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, makeBot, waitChat } = L;
const HC = `${WORK}/botserver/plugins/Headcount/`;
const consoleOut = async (cmd, wait = 700) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const N = process.argv[2] || 'Whiskey';

// Minecraft stores an owner as four signed ints.
const uuidInts = (u) => {
  const h = u.replace(/-/g, '');
  return [0, 8, 16, 24].map((i) => (parseInt(h.slice(i, i + 8), 16) | 0)).join(',');
};

(async () => {
  const cfgBackup = fs.readFileSync(HC + 'config.yml', 'utf8');
  const intOriginal = fs.readFileSync(HC + 'integrations.yml', 'utf8');
  // Start from pet protection off, whatever an earlier, interrupted run left behind.
  const intBackup = intOriginal.replace(/(pet-protection:\n(?:\s*#.*\n)*\s*enabled: )true/, '$1false');
  fs.writeFileSync(HC + 'integrations.yml', intBackup);
  let c = cfgBackup
    .replace(/^    hurt-pet: \[\]$/m, '    hurt-pet:\n      - message: "HOOK hurt-pet <owner> <pet_type> <damage> <x>"')
    .replace(/^    kill-pet: \[\]$/m, '    kill-pet:\n      - message: "HOOK kill-pet <owner> <pet_type>"')
    .replace(/^    open-trapped-chest: \[\]$/m, '    open-trapped-chest:\n      - message: "HOOK open-trapped-chest <world> <y>"');
  if (!/HOOK hurt-pet/.test(c)) {   // a config from before these hooks: add them under player
    c = c.replace('  player:\n', '  player:\n    hurt-pet:\n      - message: "HOOK hurt-pet <owner> <pet_type> <damage> <x>"\n'
      + '    kill-pet:\n      - message: "HOOK kill-pet <owner> <pet_type>"\n'
      + '    open-trapped-chest:\n      - message: "HOOK open-trapped-chest <world> <y>"\n');
  }
  fs.writeFileSync(HC + 'config.yml', c);
  await consoleOut('hca reload', 2500);

  const w = await makeBot(N);
  const a = await makeBot('Alpha');
  await sleep(2500);
  serverConsole(`minecraft:gamemode survival ${N}`);
  serverConsole('minecraft:gamemode survival Alpha');
  serverConsole(`minecraft:tp Alpha ${N}`);
  await sleep(800);

  const owner = uuidInts(a.player.uuid);
  const summonWolf = async () => {
    serverConsole('minecraft:kill @e[tag=hcw]');
    serverConsole(`execute at ${N} run minecraft:summon wolf ~2 ~ ~ {Tags:["hcw"],Owner:[I;${owner}],Health:40f,attributes:[{id:"minecraft:max_health",base:40}],NoAI:1b}`);
    await sleep(1000);
    // the newest wolf: the one just killed can still be in the bot's entity list for a moment
    return Object.values(w.entities).filter((e) => e.name === 'wolf' && e.isValid !== false).sort((x, y) => y.id - x.id)[0];
  };
  const health = async () => {
    const m = /has the following entity data: ([\d.]+)f/.exec(await consoleOut('minecraft:data get entity @e[tag=hcw,limit=1] Health', 700));
    return m ? parseFloat(m[1]) : null;
  };
  const hit = async (bot, ent) => { bot.attack(ent); await sleep(700); };

  // hurt-pet: runs on the attacker with the owner and pet
  let wolf = await summonWolf();
  record('setup: a wolf owned by Alpha is next to the bot', !!wolf);
  let mark = w.chatLog.length;
  await hit(w, wolf);
  record('hurt-pet: hitting another player\'s wolf runs the hook with <owner> and <pet_type>', await waitChat(w, /HOOK hurt-pet Alpha wolf [\d.]+ -?\d+/, mark, 1500),
    w.chatLog.slice(mark).join(' | ').slice(0, 160));
  mark = w.chatLog.length;
  await sleep(600);
  await hit(w, wolf);
  record('hurt-pet: a second hit inside hooks.cooldowns.hurt-pet (10s) runs nothing', !(await waitChat(w, /HOOK hurt-pet/, mark, 1200)));

  // the owner hurting their own pet doesn't count
  mark = a.chatLog.length;
  const aw = Object.values(a.entities).find((e) => e.name === 'wolf');
  if (aw) await hit(a, aw);
  record('hurt-pet: the owner hitting their own wolf runs nothing', !!aw && !(await waitChat(a, /HOOK hurt-pet/, mark, 1200)));

  // creative players are left out
  serverConsole(`minecraft:gamemode creative ${N}`);
  await sleep(11000);   // past the cooldown
  mark = w.chatLog.length;
  wolf = await summonWolf();
  await hit(w, wolf);
  record('hurt-pet: a player in creative runs nothing', !(await waitChat(w, /HOOK hurt-pet/, mark, 1200)));
  serverConsole(`minecraft:gamemode survival ${N}`);
  await sleep(500);

  // kill-pet
  wolf = await summonWolf();
  serverConsole('minecraft:data merge entity @e[tag=hcw,limit=1] {Health:1f}');
  await sleep(500);
  mark = w.chatLog.length;
  await hit(w, wolf);
  record('kill-pet: killing another player\'s wolf runs the hook', await waitChat(w, /HOOK kill-pet Alpha wolf/, mark, 1500),
    w.chatLog.slice(mark).join(' | ').slice(0, 160));

  // /hca hook test run
  mark = w.chatLog.length;
  const out = await consoleOut(`hca hook hurt-pet ${N}`, 900);
  record('/hca hook hurt-pet runs it on the player, ignoring the cooldown', /Ran hook/.test(out) && await waitChat(w, new RegExp(`HOOK hurt-pet ${N} wolf`), mark, 1500));

  // open-trapped-chest
  const pos = w.entity.position.floored().offset(0, 0, -2);
  serverConsole(`minecraft:setblock ${pos.x} ${pos.y} ${pos.z} trapped_chest`);
  serverConsole(`minecraft:setblock ${pos.x + 2} ${pos.y} ${pos.z} chest`);
  await sleep(800);
  const open = async (p) => {
    const win = new Promise((r) => w.once('windowOpen', r));
    try { w.activateBlock(w.blockAt(p)); } catch (e) { /* the window event decides */ }
    const got = await Promise.race([win, sleep(3000).then(() => null)]);
    await sleep(400);
    if (w.currentWindow) w.closeWindow(w.currentWindow);
    await sleep(300);
    return got;
  };
  mark = w.chatLog.length;
  await open(pos);
  record('open-trapped-chest: opening a trapped chest runs the hook with <world> and <y>', await waitChat(w, new RegExp(`HOOK open-trapped-chest \\S+ ${pos.y}`), mark, 1500),
    w.chatLog.slice(mark).join(' | ').slice(0, 160));
  mark = w.chatLog.length;
  await open(pos);
  record('open-trapped-chest: opening it again inside the cooldown runs nothing', !(await waitChat(w, /HOOK open-trapped-chest/, mark, 1000)));
  await sleep(10500);
  mark = w.chatLog.length;
  await open(pos.offset(2, 0, 0));
  record('open-trapped-chest: a normal chest runs nothing', !(await waitChat(w, /HOOK open-trapped-chest/, mark, 1000)));
  serverConsole(`minecraft:setblock ${pos.x} ${pos.y} ${pos.z} air`);
  serverConsole(`minecraft:setblock ${pos.x + 2} ${pos.y} ${pos.z} air`);

  // pet protection: with the attacker's PvP off, the hit is stopped and the wolf keeps its health
  // pet protection on, and its notice in chat (the bot only records chat; the default is the action bar)
  fs.writeFileSync(HC + 'integrations.yml', intBackup.replace(/(pet-protection:\n(?:\s*#.*\n)*\s*enabled: )false/, '$1true')
    .replace(/(blocked-hit:\n\s*channel: )actionbar/, '$1chat'));
  await consoleOut('hca reload', 2500);
  await consoleOut(`pvp ${N} off`, 900);
  await consoleOut('pvp Alpha on', 900);
  wolf = await summonWolf();
  let before = await health();
  mark = w.chatLog.length;
  await hit(w, wolf);
  let after = await health();
  record('pet protection: with the attacker\'s PvP off, the wolf takes no damage', before !== null && before === after, `${before} -> ${after}`);
  record('pet protection: the attacker is told why', await waitChat(w, /PvP is off/, mark, 1200), w.chatLog.slice(mark).join(' | ').slice(0, 160));
  // the owner offline: protected by default
  await consoleOut(`pvp ${N} on`, 900);
  a.quit();
  await sleep(1500);
  before = await health();
  mark = w.chatLog.length;
  await hit(w, wolf);
  after = await health();
  record('pet protection: a pet of an offline owner is protected (offline-owners: protect)', before === after && await waitChat(w, /offline/, mark, 1200), `${before} -> ${after}`);

  // pet vengeance: a hurt-pet pvp action turns the attacker's PvP on and locks it
  const a2 = await makeBot('Alpha');
  await sleep(2000);
  fs.writeFileSync(HC + 'integrations.yml', intBackup);
  fs.writeFileSync(HC + 'config.yml', c.replace(/    hurt-pet:\n      - message: "HOOK hurt-pet[^\n]*\n/,
    '    hurt-pet:\n      - type: pvp\n        state: "on"\n        lock: true\n        duration: 1m\n'));
  await consoleOut('hca reload', 2500);
  await consoleOut(`pvp ${N} off`, 900);
  wolf = await summonWolf();
  await hit(w, wolf);
  const st = await consoleOut(`hca status ${N} in-pvp`, 800);
  record('pet vengeance: the hurt-pet pvp action turns the attacker\'s PvP on', /in-pvp: true/.test(st), st.trim().slice(-120));
  await consoleOut(`hca effects ${N} remove all`, 600);

  serverConsole('minecraft:kill @e[tag=hcw]');
  fs.writeFileSync(HC + 'config.yml', cfgBackup);
  fs.writeFileSync(HC + 'integrations.yml', intBackup);
  await consoleOut('hca reload', 2500);
  w.quit(); a2.quit(); await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
