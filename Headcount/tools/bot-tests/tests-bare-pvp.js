// Headcount's included PvP (1.1), on the bare server: the toggle and its cooldown, new-player and respawn protection,
// blocked hits (melee, arrows, splash potions), the combat tag and what it blocks, combat logging, kills and kill
// heads, the PvP lock action, forced worlds and the message channels. Only a fresh bare server has new players
// for the newbie checks, so run it right after reset-bot.sh (run-bare.sh does).
const L = require('./lib');
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat, waitFor, openWindow } = L;
const HC = `${L.S}/botserver/plugins/Headcount/`;
const consoleOut = async (cmd, wait = 800) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const statusOf = async (p, st) => { const o = await consoleOut(`hca status ${p} ${st}`, 600); const m = new RegExp(`${st}: (\\S+)`).exec(o); return m ? m[1] : null; };
const uuidInts = (u) => { const h = u.replace(/-/g, ''); return [0, 8, 16, 24].map((i) => (parseInt(h.slice(i, i + 8), 16) | 0)).join(','); };
const headsIn = (bot) => bot.inventory.items().filter((i) => i.name === 'player_head');
const text = (item) => JSON.stringify(item ? item.components || '' : '');
const near = (bot, name) => Object.values(bot.entities).find((e) => e.username === name);
const recent = (bot, mark) => bot.chatLog.slice(mark).join(' | ');

(async () => {
  const intBackup = fs.readFileSync(HC + 'integrations.yml', 'utf8');
  const actBackup = fs.readFileSync(HC + 'actions.yml', 'utf8');
  // a sword that kills in one hit, and a test list that locks PvP on
  fs.writeFileSync(HC + 'actions.yml', actBackup.replace('lists:\n', 'lists:\n  t-pvplock:\n    - type: pvp\n      state: "on"\n      lock: true\n      duration: 5m\n', 1));
  await consoleOut('hca reload', 2500);

  const A = await makeBot('Ace');
  const B = await makeBot('Bee');
  const C = await makeBot('Cee');
  await sleep(3000);
  for (const n of ['Ace', 'Bee', 'Cee']) serverConsole(`minecraft:gamemode survival ${n}`);
  serverConsole('minecraft:tp Bee Ace');
  serverConsole('minecraft:tp Cee Ace');
  await sleep(1000);

  // --- defaults and newbie protection -----------------------------------------------------------------------
  let mark = A.chatLog.length;
  A.chat('/pvp status');
  record('/pvp status: PvP starts off', await waitChat(A, /Your PvP is off/, mark, 2000), recent(A, mark).slice(0, 100));
  record('/pvp status: a new player is told how long their protection lasts', await waitChat(A, /protected as a new player for (9|10)m/, mark, 2000), recent(A, mark).slice(0, 160));
  record('status: a new player is newbie-protected and has PvP off', (await statusOf('Ace', 'newbie-protected')) === 'true' && (await statusOf('Ace', 'in-pvp')) === 'false');

  // a newbie can't be hit even by someone with PvP on, and can't hit
  mark = A.chatLog.length;
  A.chat('/pvp on');
  record('/pvp on: a new player is told their protection ended, then PvP is on', await waitChat(A, /new-player protection has ended/, mark, 2000) && await waitChat(A, /PvP is now on/, mark, 2000), recent(A, mark).slice(0, 160));
  record('status: newbie-protected ends once they turn PvP on', (await statusOf('Ace', 'newbie-protected')) === 'false' && (await statusOf('Ace', 'in-pvp')) === 'true');
  let hb = B.health;
  mark = A.chatLog.length;
  A.attack(near(A, 'Bee'));
  await sleep(800);
  record('blocked hit: hitting a new player does nothing, and says why', B.health === hb && await waitChat(A, /Bee is protected as a new player/, mark, 1500), `${hb}->${B.health} ${recent(A, mark).slice(0, 80)}`);
  // the vault gate: in-pvp blocks moving heads
  let w = await openWindow(A, '/hc');
  mark = A.chatLog.length;
  if (w) await A.clickWindow(0, 0, 1);
  await sleep(700);
  record('vault gate: with PvP on, moving a head is blocked with a message', headsIn(A).length === 0 && await waitChat(A, /PvP is on/i, mark, 1500), recent(A, mark).slice(0, 100));
  if (w) A.closeWindow(w);
  mark = A.chatLog.length;
  A.chat('/pvp off');
  record('/pvp off right after: the cooldown says how long to wait', await waitChat(A, /Wait \d+s before switching PvP again/, mark, 2000), recent(A, mark).slice(0, 100));

  // Bee and Cee end their protection too; Cee keeps PvP off for the blocked-hit checks
  B.chat('/pvp on');
  await sleep(600);
  C.chat('/pvp on');
  await sleep(11000);   // past Cee's cooldown
  C.chat('/pvp off');
  await sleep(800);
  record('setup: Ace and Bee on, Cee off', (await statusOf('Bee', 'in-pvp')) === 'true' && (await statusOf('Cee', 'in-pvp')) === 'false');

  // --- blocked hits ----------------------------------------------------------------------------------------
  const hc = C.health;
  mark = A.chatLog.length;
  A.attack(near(A, 'Cee'));
  await sleep(800);
  record('blocked hit: no damage to a player with PvP off, and the attacker is told', C.health === hc && await waitChat(A, /Cee has PvP off/, mark, 1500), `${hc}->${C.health} ${recent(A, mark).slice(0, 80)}`);
  mark = C.chatLog.length;
  hb = B.health;
  C.attack(near(C, 'Bee'));
  await sleep(800);
  record('blocked hit: a player with PvP off can\'t hit back', B.health === hb && await waitChat(C, /Your PvP is off/, mark, 1500), recent(C, mark).slice(0, 80));
  const aU = uuidInts(A.player.uuid);
  serverConsole(`execute at Cee run minecraft:summon arrow ~ ~3 ~ {Motion:[0.0d,-1.5d,0.0d],Owner:[I;${aU}],damage:6d}`);
  await sleep(1500);
  record('blocked hit: an arrow from Ace does no damage to Cee', C.health === hc, `${hc}->${C.health}`);
  serverConsole(`execute at Cee run minecraft:summon splash_potion ~ ~2.5 ~ {Item:{id:"minecraft:splash_potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:strong_harming"}}},Owner:[I;${aU}]}`);
  await sleep(2000);
  record('blocked hit: a harming splash potion from Ace does no damage to Cee', C.health === hc, `${hc}->${C.health}`);
  // control: the same arrow does hurt Bee, who has PvP on
  // full health first, so the arrow can't kill Bee (that would use up Ace's kill head for this cycle);
  // peaceful regenerates quickly, so watch for any drop rather than compare once
  serverConsole('minecraft:effect give Bee instant_health 1 5');
  await sleep(800);
  hb = B.health;
  let lowest = hb;
  const onHealth = () => { lowest = Math.min(lowest, B.health); };
  B.on('health', onHealth);
  serverConsole(`execute at Bee run minecraft:summon arrow ~ ~3 ~ {Motion:[0.0d,-1.5d,0.0d],Owner:[I;${aU}],damage:6d}`);
  await sleep(1500);
  B.off('health', onHealth);
  record('control: the same arrow hurts Bee, who has PvP on', lowest < hb, `${hb}->${lowest}`);
  serverConsole('minecraft:effect give @a instant_health 1 5');
  await sleep(500);

  // --- combat tag -------------------------------------------------------------------------------------------
  // the arrow above already tagged both; wait it out, then hit
  await waitFor(async () => (await statusOf('Ace', 'combat-tagged')) === 'false', 20000, 1000);
  serverConsole('minecraft:tp Bee Ace');   // back within reach, whatever the arrows did
  await sleep(1000);
  mark = A.chatLog.length;
  let mb = B.chatLog.length;
  A.attack(near(A, 'Bee'));
  await sleep(1000);
  record('combat tag: both players are told who they\'re in combat with', await waitChat(A, /In combat with Bee/, mark, 1500) && await waitChat(B, /In combat with Ace/, mb, 1500), recent(A, mark).slice(0, 80));
  record('combat tag: a live countdown follows', await waitChat(A, /In combat \d+s/, mark, 2500));
  record('status: combat-tagged is true for both', (await statusOf('Ace', 'combat-tagged')) === 'true' && (await statusOf('Bee', 'combat-tagged')) === 'true');
  for (const cmd of ['/spawn', '/home', '/tpa Cee', '/essentials:home']) {
    // /essentials:home gives the same message as /home, which is held back for a second
    if (cmd === '/essentials:home') await sleep(1100);
    mark = A.chatLog.length;
    A.chat(cmd);
    const bare = cmd.split(' ')[0].replace(/^\/(essentials:)?/, '');
    record(`combat tag: ${cmd} is refused with the seconds left`, await waitChat(A, new RegExp(`can't use /${bare} while in combat \\(\\d+s left\\)`), mark, 1500), recent(A, mark).slice(0, 100));
  }
  mark = A.chatLog.length;
  A.chat('/pvp off');
  record('combat tag: /pvp off is refused', await waitChat(A, /can't turn PvP off while in combat/, mark, 1500), recent(A, mark).slice(0, 100));
  const posBefore = B.entity.position.clone();
  mark = B.chatLog.length;
  serverConsole('minecraft:tp Bee 50 -60 50');
  await sleep(1200);
  record('combat tag: a console /tp of a tagged player fails, and they\'re told', B.entity.position.distanceTo(posBefore) < 2 && await waitChat(B, /can't teleport while in combat/, mb, 1500), `${posBefore} -> ${B.entity.position}`);
  let out = await consoleOut('pvp Ace');
  record('/pvp <player> from the console shows their PvP and combat', /Ace's PvP is on \(in combat: yes\)/.test(out), out.trim().slice(-80));
  mark = A.chatLog.length;
  await waitFor(() => /no longer in combat/.test(recent(A, mark)), 20000, 500);
  record('combat tag: it ends after 15s with a message', /no longer in combat/.test(recent(A, mark)));
  record('status: combat-tagged turns off', (await statusOf('Ace', 'combat-tagged')) === 'false');

  // --- kills and kill heads ----------------------------------------------------------------------------------
  serverConsole('minecraft:give Ace netherite_sword[enchantments={sharpness:255}]');
  await sleep(800);
  const sword = A.inventory.items().find((i) => i.name === 'netherite_sword');
  if (sword) await A.equip(sword, 'hand');
  serverConsole('minecraft:tp Cee 20 -60 20');   // out of reach of the drop
  serverConsole('minecraft:tp Bee Ace');
  await sleep(1000);
  const headsBefore = headsIn(A).length;
  mark = A.chatLog.length;
  const deathAt = near(A, 'Bee').position.clone();
  A.attack(near(A, 'Bee'));
  await sleep(800);
  // the killer is combat-tagged now and can't be teleported, so the drop comes to them
  serverConsole(`execute at Ace run minecraft:tp @e[type=item,distance=..12] Ace`);
  void deathAt;
  await sleep(2000);
  const got = headsIn(A).filter((i) => text(i).includes(B.player.uuid.replace(/-/g, '').slice(0, 0)) && /Bee/.test(text(i)));
  record('kill: the killer picks up a fresh head of the victim', headsIn(A).length === headsBefore + 1 && got.length >= 1, `${headsBefore}->${headsIn(A).length}`);
  await sleep(6000);   // respawn protection is over
  serverConsole('minecraft:tp Bee Ace');
  await sleep(1000);
  mark = A.chatLog.length;
  A.attack(near(A, 'Bee'));
  await sleep(1200);
  record('kill: the same killer gets no second head this cycle, and is told', headsIn(A).length === headsBefore + 1 && await waitChat(A, /already took Bee's head/, mark, 1500), recent(A, mark).slice(0, 120));

  // respawn protection (5s): Bee just respawned; Ace teleports over and hits at once
  await sleep(200);
  serverConsole('minecraft:tp Bee Ace');   // Ace is tagged from the kill and can't be teleported; Bee's tag ended at death
  await sleep(600);
  mark = A.chatLog.length;
  hb = B.health;
  A.attack(near(A, 'Bee'));
  await sleep(600);
  record('respawn protection: a player who just respawned can\'t be hit', B.health === hb && await waitChat(A, /Bee just respawned/, mark, 1500), recent(A, mark).slice(0, 80));

  // dying on your own: no head. And keepInventory still drops the kill head
  const before2 = db("SELECT COUNT(*) FROM heads WHERE source LIKE 'kill%'")[0][0];
  serverConsole('minecraft:kill Cee');
  await sleep(2000);
  record('kill: /kill drops no head', db("SELECT COUNT(*) FROM heads WHERE source LIKE 'kill%'")[0][0] === before2, `${before2}`);

  // the 15s killer-only pickup: Cee kills Bee with Ace standing on the body
  await sleep(6000);
  serverConsole('minecraft:gamerule keep_inventory true');
  serverConsole('minecraft:give Cee netherite_sword[enchantments={sharpness:255}]');
  serverConsole('minecraft:give Bee dirt 3');
  C.chat('/pvp on');
  await sleep(1000);
  const cs = C.inventory.items().find((i) => i.name === 'netherite_sword');
  if (cs) await C.equip(cs, 'hand');
  serverConsole('minecraft:tp Cee Bee');
  serverConsole('minecraft:tp Ace Bee');
  await sleep(1000);
  const aHeads = headsIn(A).length;
  C.attack(near(C, 'Bee'));
  await sleep(800);
  // put the drop on Ace, who isn't the killer, so the check means something
  serverConsole('execute at Cee run minecraft:tp @e[type=item,distance=..12] Ace');
  await sleep(2700);
  record('kill pickup: for 15s only the killer can pick the head up', headsIn(A).length === aHeads, `Ace ${aHeads}->${headsIn(A).length}`);
  // Cee is tagged from the kill and can't be teleported: bring the head to Cee instead
  serverConsole('execute at Cee run minecraft:tp @e[type=item,distance=..12] Cee');
  await sleep(2500);
  // the head Cee's kill made, by its ID in the database
  const cUuid = db("SELECT uuid FROM players WHERE name='Cee'")[0][0];
  const bUuid = db("SELECT uuid FROM players WHERE name='Bee'")[0][0];
  const killHead = (db(`SELECT head_id FROM heads WHERE source LIKE 'kill%' AND recipient='${cUuid}' AND owner='${bUuid}'`)[0] || [null])[0];
  const cHas = () => !!killHead && headsIn(C).some((i) => text(i).includes(killHead));
  await waitFor(cHas, 4000, 300);
  record('kill pickup: the killer picks it up', cHas(), `head ${killHead} / Cee holds ${headsIn(C).length} head(s)`);
  await sleep(1500);
  record('keepInventory: the victim keeps their items, and the kill head still dropped', B.inventory.items().some((i) => i.name === 'dirt') && !!killHead, `head ${killHead}`);
  serverConsole('minecraft:gamerule keep_inventory false');

  // --- combat log --------------------------------------------------------------------------------------------
  await sleep(6000);
  serverConsole('minecraft:clear Bee');
  serverConsole('minecraft:give Bee cobblestone 7');
  serverConsole('minecraft:tp Ace Bee');
  await sleep(1000);
  A.unequip('hand');
  await sleep(300);
  const hits = db("SELECT COUNT(*) FROM heads WHERE source LIKE 'kill%'")[0][0];
  A.attack(near(A, 'Bee'));   // a fist: Bee is tagged, not killed
  await sleep(800);
  mark = A.chatLog.length;
  const deaths0 = db("SELECT COALESCE(SUM(deaths),0) FROM cycle_players cp JOIN players p ON p.uuid=cp.uuid WHERE p.name='Bee'")[0][0];
  B.quit();
  await sleep(2500);
  record('combat log: everyone else is told', await waitChat(A, /Bee logged out during a fight/, mark, 2000), recent(A, mark).slice(0, 100));
  const cobble = Object.values(A.entities).filter((e) => e.name === 'item').length;
  record('combat log: they die where they stood and drop their items', cobble > 0, `item entities ${cobble}`);
  record('combat log: Ace already has Bee\'s head this cycle, so no second kill head', db("SELECT COUNT(*) FROM heads WHERE source LIKE 'kill%'")[0][0] === hits);
  const B2 = await makeBot('Bee');
  await sleep(3000);
  record('combat log: rejoining, they respawn with full health and without the items', B2.health > 0 && !B2.inventory.items().some((i) => i.name === 'cobblestone'), `health ${B2.health}`);
  void deaths0;
  // a kick doesn't count
  serverConsole('minecraft:kill @e[type=item]');
  serverConsole('minecraft:give Bee cobblestone 5');
  serverConsole('minecraft:tp Ace Bee');
  await sleep(6000);
  A.attack(near(A, 'Bee'));
  await sleep(800);
  serverConsole('kick Bee');
  await sleep(2000);
  const B3 = await makeBot('Bee');
  await sleep(2500);
  record('combat log: a kicked player isn\'t punished (keeps their items)', B3.inventory.items().some((i) => i.name === 'cobblestone'));

  // --- /pvp <player> off and the lock --------------------------------------------------------------------------
  out = await consoleOut('pvp Cee off');
  record('/pvp <player> off from the console switches it', /Cee's PvP is off/.test(out) && (await statusOf('Cee', 'in-pvp')) === 'false', out.trim().slice(-80));
  await consoleOut('hca run Cee t-pvplock', 1200);
  record('lock: the pvp action with lock: true turns PvP on', (await statusOf('Cee', 'in-pvp')) === 'true');
  mark = C.chatLog.length;
  C.chat('/pvp off');
  record('lock: /pvp off says it\'s locked', await waitChat(C, /PvP is locked on/, mark, 1500), recent(C, mark).slice(0, 100));
  mark = C.chatLog.length;
  C.chat('/hc effects');
  record('lock: /hc effects describes it as forced on (locked)', await waitChat(C, /PvP forced on \(locked\)/, mark, 1500), recent(C, mark).slice(0, 200));
  serverConsole('hca effects Cee remove all');
  await sleep(800);
  record('lock: removing the effect unlocks it and puts PvP back', (await statusOf('Cee', 'in-pvp')) === 'false');

  // --- config: forced worlds, the boss bar countdown, a title for the tag, pearls, combat-log actions -------------
  await waitFor(async () => (await statusOf('Ace', 'combat-tagged')) === 'false', 20000, 1000);   // a tagged player can't be teleported
  let ic = intBackup
    .replace('force-on-worlds: []', 'force-on-worlds: [world]')
    .replace(/(\n\s+countdown: )actionbar/, '$1bossbar')
    .replace(/(tag-start:\n\s+channel: )actionbar/, '$1title')
    .replace('block-ender-pearls: false', 'block-ender-pearls: true')
    .replace(/(combat-log:[\s\S]*?\n(\s+)actions: )\[\]/, '$1\n$2  - type: points\n$2    amount: -5');
  fs.writeFileSync(HC + 'integrations.yml', ic);
  await consoleOut('hca reload', 2500);
  mark = C.chatLog.length;
  C.chat('/pvp off');
  record('force-on-worlds: /pvp off says PvP is always on in this world', await waitChat(C, /PvP is always on in this world/, mark, 1500) && (await statusOf('Cee', 'in-pvp')) === 'true', recent(C, mark).slice(0, 100));
  const titles = [];
  A.on('title', (t) => titles.push(typeof t === 'string' ? t : JSON.stringify(t)));
  const bars = [];
  A.on('bossBarCreated', (b) => bars.push(b.title ? b.title.toString() : ''));
  serverConsole('minecraft:tp Ace Cee');
  await sleep(1000);
  A.attack(near(A, 'Cee'));
  await sleep(2000);
  record('messages.tag-start.channel: title shows the tag as a title', titles.some((t) => /In combat with Cee/.test(t)), titles.join(' | ').slice(0, 120));
  record('messages.countdown: bossbar shows the countdown as a boss bar', bars.some((t) => /In combat/.test(t)), bars.join(' | ').slice(0, 120));
  serverConsole('minecraft:give Ace ender_pearl 2');
  await sleep(800);
  const pearl = A.inventory.items().find((i) => i.name === 'ender_pearl');
  if (pearl) await A.equip(pearl, 'hand');
  mark = A.chatLog.length;
  A.activateItem();
  await sleep(800);
  A.deactivateItem();
  record('block-ender-pearls: a pearl is refused in combat', await waitChat(A, /can't use ender pearls while in combat/, mark, 1500) && A.inventory.items().some((i) => i.name === 'ender_pearl' && i.count === 2), recent(A, mark).slice(0, 100));
  serverConsole('hca points Ace give 10');
  await sleep(500);
  const pts = db("SELECT points FROM players WHERE name='Ace'")[0][0];
  A.quit();
  await sleep(2500);
  record('combat-log.actions: a points -5 action runs on the player who left', db("SELECT points FROM players WHERE name='Ace'")[0][0] === pts - 5, `${pts} -> ${db("SELECT points FROM players WHERE name='Ace'")[0][0]}`);

  fs.writeFileSync(HC + 'integrations.yml', intBackup);
  fs.writeFileSync(HC + 'actions.yml', actBackup);
  await consoleOut('hca reload', 2500);
  serverConsole('minecraft:kill @e[type=item]');
  for (const b of [B3, C]) b.quit();
  await sleep(1000);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e.stack); L.summary(); process.exit(2); });
