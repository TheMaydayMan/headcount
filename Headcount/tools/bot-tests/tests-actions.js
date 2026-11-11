// Every action type on a live, non-op player (Juliet), each checked by its effect in the game or on the server,
// plus the common options (chance, conditions, delay, repeat) and the revert of timed ones.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, waitFor, makeBot, waitChat } = L;
const S = `${WORK}`;
const HC = S + '/botserver/plugins/Headcount/';
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const isEcon = (s) => s && s.name === 'player_head' && JSON.stringify(s.components || s.nbt || '').includes('head_id');
const N = process.argv[2] || 'Juliet';
const uuidOf = () => db(`SELECT uuid FROM players WHERE name='${N}'`)[0][0];
const pl = (col) => db(`SELECT ${col} FROM players WHERE name='${N}'`)[0][0];
const count = (bot, name) => bot.inventory.items().filter((i) => i.name === name).reduce((n, i) => n + i.count, 0);
const status = async (st) => /: true/.test((await consoleOut(`hca status ${N} ${st}`, 700)).split('\n').filter((l) => l.includes(st)).join('\n'));

const LISTS = `
  ta-message:
    - message: "TA-MESSAGE <player>"
  ta-broadcast:
    - type: broadcast
      text: "TA-BROADCAST <player>"
  ta-title:
    - type: title
      title: "TA-TITLE"
      subtitle: "TA-SUB"
  ta-actionbar:
    - type: actionbar
      text: "TA-ACTIONBAR"
  ta-sound:
    - type: sound
      sound: entity.experience_orb.pickup
  ta-particle:
    - type: particle
      particle: heart
      count: 5
  ta-bossbar:
    - type: bossbar
      text: "TA-BOSSBAR"
      duration: 6s
  ta-location:
    - type: broadcast-location
      text: "TA-LOC <player> <x> <y> <z> <world>"
  ta-console:
    - type: console
      command: "say TA-CONSOLE <player>"
  ta-pcmd:
    - type: player-command
      command: "hc points"
  ta-give:
    - type: give-item
      material: DIAMOND
      amount: 2
      duration: 6s
      take-back: true
  ta-equip:
    - type: equip
      slot: head
      material: CARVED_PUMPKIN
      lock: true
      duration: 6s
  ta-heads:
    - type: give-heads
      owner: self
      amount: 2
  ta-loot:
    - type: loot
      table: minecraft:chests/simple_dungeon
  ta-xp:
    - type: experience
      amount: 50
  ta-points:
    - type: points
      amount: 7
  ta-points-next:
    - type: points
      amount: 3
      when: next-cycle
  ta-money:
    - type: money
      amount: 100
  ta-perm:
    - type: permission
      permission: hc.test.node
      duration: 6s
  ta-group:
    - type: group
      group: hctest
      duration: 6s
  ta-track:
    - type: track
      track: hctrack
      direction: promote
  ta-prefix:
    - type: prefix
      text: "[TAP]"
      priority: 50
      duration: 6s
  ta-grant:
    - type: grant-title
      title: collector
      equip: true
  ta-force:
    - type: force-title
      title: dunce
      duration: 6s
  ta-strip:
    - type: strip-title
      which: all
  ta-pvp:
    - type: pvp
      state: on
      lock: true
      duration: 6s
  ta-potion:
    - type: potion
      effect: speed
      amplifier: 1
      duration: 6s
  ta-attr:
    - type: attribute
      attribute: max_health
      amount: -4
      duration: 6s
  ta-glow:
    - type: glowing
      duration: 6s
  ta-fire:
    - type: fire
      ticks: 100
  ta-lightning:
    - type: lightning
      damage: false
  ta-freeze:
    - type: freeze
      ticks: 200
  ta-hunger:
    - type: hunger
      food: -10
  ta-gamemode:
    - type: gamemode
      mode: adventure
      lock: true
      duration: 6s
  ta-teleport:
    - type: teleport
      to: world,40,-60,40
      return: true
      duration: 6s
  ta-damage:
    - type: damage
      amount: 6
  ta-heal:
    - type: heal
      amount: full
  ta-feed:
    - type: feed
      amount: full
  ta-launch:
    - type: launch
      power: 2
  ta-clear:
    - clear-effects
  ta-deny:
    - type: deny
      deny: [chat, trade, shop, vault, commands]
      commands: [spawn]
      duration: 8s
  ta-nextcycle:
    - type: next-cycle
      starting-heads: "+5"
  ta-flag:
    - type: flag
      flag: ta-flag
  ta-bonus:
    - type: score-bonus
      amount: 10
  ta-run:
    - type: run
      list: ta-message
  ta-random:
    - type: random
      choices:
        - weight: 1
          actions: ["message: TA-RANDOM-A"]
        - weight: 1
          actions: ["message: TA-RANDOM-B"]
  ta-chance0:
    - type: message
      text: "TA-NEVER"
      chance: 0
  ta-cond:
    - type: message
      text: "TA-COND"
      conditions:
        points: ">=100000"
  ta-delay:
    - type: message
      text: "TA-DELAY"
      delay: 2s
  ta-repeat:
    - type: message
      text: "TA-REPEAT"
      duration: 5s
      repeat: 2s
  ta-warn:
    - type: warn
      reason: "TA warn"
  ta-mute:
    - type: mute
      reason: "TA mute"
      duration: 8s
  ta-jail:
    - type: jail
      jail: hcjail
      reason: "TA jail"
      duration: 8s
  ta-kick:
    - type: kick
      reason: "TA kick"
  ta-ban:
    - type: ban
      reason: "TA ban"
      duration: 10s
`;

(async () => {
  let acts = fs.readFileSync(HC + 'actions.yml', 'utf8');
  if (!acts.includes('ta-message:')) fs.writeFileSync(HC + 'actions.yml', acts.replace('lists:\n', 'lists:' + LISTS));
  for (const c of ['lp creategroup hctest', 'lp createtrack hctrack', 'lp track hctrack append default', 'lp track hctrack append hctest']) serverConsole(c);
  const reloadOut = await consoleOut('hca reload', 3000);
  record('setup: the test lists load with no warnings', !/WARN.*Headcount|\[Headcount\].*(could not|couldn.t|unknown|failed)/i.test(reloadOut), reloadOut.split('\n').filter((l) => /WARN/.test(l)).join(' | ').slice(0, 200));
  const a = await makeBot('Alpha');
  let j = await makeBot(N);
  await sleep(3000);
  serverConsole(`minecraft:clear ${N}`);
  serverConsole(`minecraft:tp ${N} Alpha`);
  await sleep(800);
  const run = async (list, wait = 900) => { serverConsole(`hca run ${N} ${list}`); await sleep(wait); };
  const got = async (bot, re, fn, t = 2500) => { const m = bot.chatLog.length; await fn(); return waitChat(bot, re, m, t); };

  // --- messages and effects -----------------------------------------------------------------------------------
  record('message: reaches the player with <player> filled', await got(j, new RegExp(`TA-MESSAGE ${N}`), () => run('ta-message')));
  record('broadcast: reaches everyone', await got(a, new RegExp(`TA-BROADCAST ${N}`), () => run('ta-broadcast')));
  let title = null;
  j.once('title', (t) => { title = t; });
  await run('ta-title', 1200);
  record('title: a title packet reaches the player', !!title && /TA-TITLE|TA-SUB/.test(JSON.stringify(title)), JSON.stringify(title).slice(0, 80));
  // This mineflayer version raises no events for these packets, so watch the raw ones.
  let bar = null, sound = null;
  j._client.on('packet', (d, meta) => {
    if (meta.name === 'action_bar' || (meta.name === 'system_chat' && d.isActionBar)) bar = JSON.stringify(d);
    if (/sound_effect/.test(meta.name)) sound = meta.name;
  });
  await run('ta-actionbar', 1200);
  record('actionbar: reaches the player', !!bar && /TA-ACTIONBAR/.test(bar), String(bar).slice(0, 80));
  await run('ta-sound', 1200);
  record('sound: a sound packet reaches the player', sound !== null, String(sound));
  let part = 0;
  j._client.on('world_particles', () => { part++; });
  await run('ta-particle', 1200);
  record('particle: particles are sent to the player', part > 0, `packets=${part}`);
  let boss = null;
  j.once('bossBarCreated', (b) => { boss = b; });
  await run('ta-bossbar', 1200);
  let bossGone = false;
  j.once('bossBarDeleted', () => { bossGone = true; });
  record('bossbar: a timed boss bar is shown', !!boss && /TA-BOSSBAR/.test(JSON.stringify(boss.title || '')));
  await sleep(6500);
  record('bossbar: and removed when it ends', bossGone);
  record('broadcast-location: reports the coordinates', await got(a, new RegExp(`TA-LOC ${N} -?\\d+ -?\\d+ -?\\d+ world`), () => run('ta-location')));

  // --- commands -------------------------------------------------------------------------------------------------
  record('console: runs a console command with <player>', await got(j, new RegExp(`TA-CONSOLE ${N}`), () => run('ta-console')));
  record('player-command: the player runs the command', await got(j, /You have \d+ points/, () => run('ta-pcmd')));

  // --- items --------------------------------------------------------------------------------------------------
  await run('ta-give');
  const dia = count(j, 'diamond');
  await sleep(6500);
  record('give-item: gives the items, and take-back takes them when the timed action ends', dia === 2 && count(j, 'diamond') === 0, `${dia} then ${count(j, 'diamond')}`);
  serverConsole(`minecraft:item replace entity ${N} armor.head with iron_helmet`);
  await sleep(600);
  await run('ta-equip');
  const head = j.inventory.slots[5];
  await sleep(6500);
  const head2 = j.inventory.slots[5];
  record('equip: puts the item in the slot, then gives the previous item back at the end', head && head.name === 'carved_pumpkin' && head2 && head2.name === 'iron_helmet',
    `${head && head.name} then ${head2 && head2.name}`);
  const before = j.inventory.items().filter(isEcon).length;
  await run('ta-heads');
  record('give-heads: two of the player\'s own economy heads', j.inventory.items().filter(isEcon).length === before + 2);
  const n0 = j.inventory.items().length;
  await run('ta-loot');
  record('loot: the loot table fills the inventory', j.inventory.items().length > n0, `${n0} -> ${j.inventory.items().length} stacks`);
  const xp = j.experience.points;
  await run('ta-xp', 1200);
  record('experience: adds experience', j.experience.points >= xp + 50, `${xp} -> ${j.experience.points}`);

  // --- currency -----------------------------------------------------------------------------------------------
  const p0 = pl('points'), pn0 = pl('points_next');
  await run('ta-points');
  await run('ta-points-next');
  record('points: now and next-cycle', pl('points') === p0 + 7 && pl('points_next') === pn0 + 3, `points ${p0}->${pl('points')} next ${pn0}->${pl('points_next')}`);
  const bal = (out) => { const m = /Balance of \S+: \$?(-?[\d,.]+)/.exec(out); return m ? Number(m[1].replace(/,/g, '')) : NaN; };
  const m0 = bal(await consoleOut(`balance ${N}`));
  await run('ta-money');
  const m1 = bal(await consoleOut(`balance ${N}`));
  record('money: pays through Vault (EssentialsX economy)', m1 === m0 + 100, `${m0} -> ${m1}`);

  // --- permissions and ranks (LuckPerms) -----------------------------------------------------------------------
  const lpHas = async (node) => /Result: true/.test(await consoleOut(`lp user ${N} permission check ${node}`, 1500));
  await run('ta-perm', 1500);
  const permOn = await lpHas('hc.test.node');
  await sleep(6000);
  const permOff = await lpHas('hc.test.node');
  record('permission: added for the duration, then gone (LuckPerms expiry)', permOn && !permOff, `${permOn} then ${permOff}`);
  await run('ta-group', 1500);
  const grpOn = await lpHas('group.hctest');
  await sleep(6000);
  const grpOff = await lpHas('group.hctest');
  record('group: added for the duration, then removed', grpOn && !grpOff, `${grpOn} then ${grpOff}`);
  await run('ta-track', 1500);
  const info = await consoleOut(`lp user ${N} parent info`, 1500);
  record('track: promotes along the LuckPerms track', /hctest/.test(info), info.split('\n').filter((l) => /hctest|default/.test(l)).join(' | ').slice(0, 120));
  serverConsole(`lp user ${N} parent remove hctest`);
  await run('ta-prefix', 500);
  let meta = '';
  for (let i = 0; i < 6 && !/\[TAP\]/.test(meta); i++) meta = await consoleOut(`lp user ${N} meta info`, 800);   // LuckPerms applies it asynchronously
  record('prefix: set through LuckPerms', /\[TAP\]/.test(meta));

  // --- titles -------------------------------------------------------------------------------------------------
  await run('ta-grant', 1200);
  record('grant-title: owned and equipped', db(`SELECT COUNT(*) FROM titles WHERE uuid='${uuidOf()}' AND title='collector'`)[0][0] === 1 && pl('equipped_title') === 'collector');
  const shown = async () => ((await consoleOut(`papi parse ${N} %headcount_title%`, 900)).trim().split('\n').filter((l) => !/HCADDON|issued server command/.test(l)).pop() || '').replace(/^\[[^\]]*\] (INFO\]: )?/, '');
  await run('ta-force', 1200);
  const forced = await shown();
  await sleep(6500);
  const after = await shown();
  record('force-title: shown for the duration, then the equipped one is back', /Dunce/.test(forced) && /Collector/.test(after) && pl('equipped_title') === 'collector', `${forced} then ${after}`);
  await run('ta-strip', 1200);
  record('strip-title: removes them', pl('equipped_title') === null && db(`SELECT COUNT(*) FROM titles WHERE uuid='${uuidOf()}'`)[0][0] === 0);

  // --- PvP ---------------------------------------------------------------------------------------------------
  if (await status('in-pvp')) { j.chat('/pvp'); await sleep(900); }
  await run('ta-pvp', 1000);
  const on = await status('in-pvp');
  j.chat('/pvp');                            // locked: can't turn it off
  await sleep(900);
  const stillOn = await status('in-pvp');
  await sleep(5000);
  record('pvp: forced on and locked for the duration, then restored', on && stillOn && !(await status('in-pvp')));

  // --- buffs and debuffs ---------------------------------------------------------------------------------------
  await run('ta-potion', 1200);
  const speed = Object.values(j.entity.effects || {}).some((e) => e.id === 1 || e.id === 0 || /speed/i.test(String(e.name || '')));
  await sleep(6000);
  const speedGone = Object.keys(j.entity.effects || {}).length === 0;
  record('potion: applied for the duration, then removed', speed && speedGone, JSON.stringify(j.entity.effects).slice(0, 80));
  const maxHp = () => { const at = j.entity.attributes || {}; const k = Object.keys(at).find((x) => /max_health/.test(x)); if (!k) return null; const v = at[k]; return v.value + (v.modifiers || []).reduce((s, m) => s + (m.operation === 0 ? m.amount : 0), 0); };
  await run('ta-attr', 1200);
  const hp1 = maxHp();
  await sleep(6000);
  const hp2 = maxHp();
  record('attribute: max_health -4 for the duration, then restored', hp1 === 16 && hp2 === 20, `${hp1} then ${hp2}`);
  const glowing = () => { const e = Object.values(a.entities).find((x) => x.username === N); return e && e.metadata && typeof e.metadata[0] === 'number' ? (e.metadata[0] & 0x40) !== 0 : null; };
  await run('ta-glow', 1200);
  const g1 = glowing();
  await sleep(6000);
  record('glowing: others see the player glow, then stop', g1 === true && glowing() === false, `${g1} then ${glowing()}`);
  await run('ta-fire', 700);
  record('fire: sets the player on fire', await status('on-fire'));
  serverConsole(`minecraft:effect give ${N} fire_resistance 10`);
  await run('ta-heal');
  let lightning = false;
  const onSpawn = (e) => { if (/lightning/.test(e.name || '')) lightning = true; };
  a.on('entitySpawn', onSpawn);
  await run('ta-lightning', 1500);
  record('lightning: a lightning bolt strikes (no damage)', lightning);
  await run('ta-freeze', 800);
  const frozen = await consoleOut(`minecraft:data get entity ${N} TicksFrozen`, 800);
  record('freeze: sets frozen ticks', /TicksFrozen|has the following entity data: [1-9]/.test(frozen) && !/: 0$/m.test(frozen.trim()), frozen.trim().split('\n').pop().slice(0, 100));
  serverConsole('difficulty easy');            // peaceful refills food and health within a second
  serverConsole(`minecraft:item replace entity ${N} armor.head with air`);
  await run('ta-feed');
  const f0 = j.food;
  await run('ta-hunger', 1200);
  record('hunger: takes 10 food', j.food === f0 - 10, `${f0} -> ${j.food}`);
  await run('ta-feed', 1200);
  record('feed: fills the food bar', j.food === 20);
  await run('ta-gamemode', 1200);
  const gm1 = j.game.gameMode;
  serverConsole(`minecraft:gamemode survival ${N}`);   // locked: put back to adventure
  await sleep(1500);
  const gm2 = j.game.gameMode;
  await sleep(5000);
  record('gamemode: set and locked for the duration, then restored', gm1 === 'adventure' && gm2 === 'adventure' && j.game.gameMode === 'survival', `${gm1}, ${gm2}, ${j.game.gameMode}`);
  const pos0 = j.entity.position.clone();
  await run('ta-teleport', 1500);
  const pos1 = j.entity.position.clone();
  await sleep(6000);
  const pos2 = j.entity.position.clone();
  record('teleport: goes there, and comes back when it ends (return: true)', Math.round(pos1.x) === 40 && Math.round(pos1.z) === 40 && pos2.distanceTo(pos0) < 2,
    `${pos0.floored()} -> ${pos1.floored()} -> ${pos2.floored()}`);
  // Read health on the server: the bot's own value lags and includes natural regeneration.
  const hp = async () => Number(((await consoleOut(`minecraft:data get entity ${N} Health`, 400)).match(/entity data: ([\d.]+)f/) || [])[1]);
  await run('ta-heal');
  await run('ta-damage', 300);
  const hpAfterDamage = await hp();
  await run('ta-heal', 300);
  const hpAfterHeal = await hp();
  record('damage, then heal: health drops by 6 and comes back to full', hpAfterDamage === 14 && hpAfterHeal === 20, `${hpAfterDamage} then ${hpAfterHeal}`);
  serverConsole('difficulty peaceful');
  let vel = 0;
  j._client.on('entity_velocity', (p) => { if (p.entityId === j.entity.id) vel++; });
  await run('ta-launch', 1200);
  record('launch: the player is pushed (velocity packet)', vel > 0);
  serverConsole(`minecraft:effect give ${N} speed 30`);
  await sleep(800);
  await run('ta-clear', 1200);
  record('clear-effects: removes potion effects', Object.keys(j.entity.effects || {}).length === 0);

  // --- deny ---------------------------------------------------------------------------------------------------
  await run('ta-deny', 900);
  const ma = a.chatLog.length;
  record('deny: chat is blocked', await got(j, /can't chat right now/, async () => { j.chat('deny-test-chat'); await sleep(300); }) &&
    !a.chatLog.slice(ma).some((m) => /deny-test-chat/.test(m)));
  record('deny: the listed command is blocked', await got(j, /can't use that command right now/, async () => { j.chat('/spawn'); await sleep(300); }));
  record('deny: trade is blocked', await got(j, /can't trade right now/, async () => { j.chat('/trade Alpha'); await sleep(300); }));
  record('deny: the shop is blocked', await got(j, /can't use the shop right now/, async () => { j.chat('/hc shop'); await sleep(300); }));
  record('deny: the vault is blocked', await got(j, /can't use your vault right now/, async () => { j.chat('/hc'); await sleep(300); }));
  await sleep(8000);
  const mb = a.chatLog.length;
  j.chat('deny-lifted-chat');
  record('deny: lifted when it ends', await waitChat(a, /deny-lifted-chat/, mb, 2000));

  // --- Headcount types --------------------------------------------------------------------------------------
  await run('ta-flag');
  record('flag: stored for the player', db(`SELECT COUNT(*) FROM flags WHERE uuid='${uuidOf()}' AND flag='ta-flag'`)[0][0] === 1);
  const score = async () => { const m = j.chatLog.length; j.chat('/hc score'); await waitChat(j, /If the cycle ended now/, m, 2500); return Number((j.chatLog.slice(m).join(' ').match(/now: score (-?\d+)/) || [])[1]); };
  const s0 = await score();
  await run('ta-bonus');
  const s1 = await score();
  record('score-bonus: adds to the score preview', s1 === s0 + 10, `${s0} -> ${s1}`);
  await run('ta-nextcycle');
  const headsBefore = j.inventory.items().filter(isEcon).length;
  await consoleOut('hca cycle end confirm', 4000);
  await sleep(1000);
  const vaultAfter = db(`SELECT COUNT(*) FROM vault_items WHERE uuid='${uuidOf()}'`)[0][0];
  const headsAfter = j.inventory.items().filter(isEcon).length;
  record('next-cycle: +5 starting heads; 18 + 5 fit the 27-slot vault, so none arrive as items', vaultAfter === 23 && headsAfter === headsBefore,
    `vault ${vaultAfter}, heads in inventory ${headsBefore} -> ${headsAfter}`);

  // --- flow and common options ------------------------------------------------------------------------------
  record('run: runs a named list', await got(j, new RegExp(`TA-MESSAGE ${N}`), () => run('ta-run')));
  record('random: runs one of the choices', await got(j, /TA-RANDOM-[AB]/, () => run('ta-random')));
  record('chance: 0 never runs', !(await got(j, /TA-NEVER/, () => run('ta-chance0'), 1500)));
  record('conditions: a failed condition skips the action', !(await got(j, /TA-COND/, () => run('ta-cond'), 1500)));
  let mj = j.chatLog.length;
  await run('ta-delay', 800);
  const early = j.chatLog.slice(mj).some((m) => /TA-DELAY/.test(m));
  record('delay: runs after the delay, not before', !early && await waitChat(j, /TA-DELAY/, mj, 3000));
  mj = j.chatLog.length;
  await run('ta-repeat', 6000);
  const reps = j.chatLog.slice(mj).filter((m) => /TA-REPEAT/.test(m)).length;
  record('repeat: runs again every 2 s while active (5 s: 3 times)', reps === 3, `times=${reps}`);

  // --- moderation (LibertyBans and EssentialsX jails) -----------------------------------------------------------
  record('warn: the player is warned (LibertyBans)', await got(j, /warn/i, () => run('ta-warn', 1500), 3000), j.chatLog.slice(-2).join(' | ').slice(0, 120));
  await run('ta-mute', 1500);
  const mc = a.chatLog.length;
  j.chat('mute-test-chat');
  await sleep(1200);
  record('mute: the player\'s chat is blocked (LibertyBans)', !a.chatLog.slice(mc).some((m) => /mute-test-chat/.test(m)));
  a.chat('/setjail hcjail');
  await sleep(1200);
  const posJ = j.entity.position.clone();
  serverConsole(`minecraft:tp ${N} ~ ~ ~`);
  serverConsole(`execute as ${N} at @s run minecraft:tp @s ~8 ~ ~`);
  await sleep(1200);
  await run('ta-jail', 2000);
  const jailedOut = await consoleOut(`whois ${N}`, 1200);
  record('jail: the player is jailed (EssentialsX)', /jail/i.test(jailedOut) || /jailed/i.test(j.chatLog.slice(-4).join(' ')), jailedOut.split('\n').filter((l) => /jail/i.test(l)).join(' | ').slice(0, 120));
  await sleep(7500);
  let kicked = null;
  j.once('kicked', (r) => { kicked = JSON.stringify(r); });
  await run('ta-kick', 2000);
  record('kick: the player is kicked with the reason', !!kicked && /TA kick/.test(kicked), String(kicked).slice(0, 100));
  await sleep(1500);
  j = await makeBot(N);
  await sleep(2000);
  kicked = null;
  j.once('kicked', (r) => { kicked = JSON.stringify(r); });
  await run('ta-ban', 2000);
  let rejoin = 'connected';
  try {
    const t = await Promise.race([makeBot(N).then((x) => { x.quit(); return 'joined'; }).catch(() => 'refused'), sleep(6000).then(() => 'refused')]);
    rejoin = t;
  } catch (e) { rejoin = 'refused'; }
  record('ban: a temporary ban kicks and keeps the player out', !!kicked && rejoin !== 'joined', `${String(kicked).slice(0, 60)} rejoin=${rejoin}`);
  await sleep(10000);
  let back = 'refused';
  try { const x = await makeBot(N); back = 'joined'; x.quit(); } catch (e) { /* still banned */ }
  record('ban: lifted when the duration ends', back === 'joined');

  a.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
