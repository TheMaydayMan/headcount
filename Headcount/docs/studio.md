# Headcount Studio

Studio is a web app for setting up and running Headcount without editing YAML by hand. The plugin serves it, so it's there whenever the server is running. You open it in a browser, sign in, and it edits the config files in `plugins/Headcount/` and watches the game through the database.

## Opening it

Run `/hca studio` in game or in the console. It shows a link and a password. Click the link (or type it into a browser), enter the password, and you're in. The browser stays signed in for a week, or whatever `studio.session-length` says.

`/hca studio` needs `headcount.admin.studio`, which ops have by default. It isn't part of `headcount.admin`, because Studio can change every config file and run any command as the console. Give the password only to people you'd give the console to.

`/hca studio reset` makes a new password and signs every browser out. Do that if the password ever ends up somewhere it shouldn't, like a log you pasted for help (the console's reply goes into `logs/latest.log`).

## Where it listens

By default Studio shares the Minecraft port. A browser and a Minecraft client both connect to the same address, and Headcount tells them apart by the first few bytes they send. So if players join `play.example.com`, Studio is at `http://play.example.com:25565`. This works on hosts that only give you one port.

It doesn't work when the game port sits behind something that only passes Minecraft traffic, like TCPShield, or on a BungeeCord or Velocity backend that players can't reach directly. There, give Studio a port of its own:

```yaml
studio:
  port: 8765        # instead of same
  bind: 0.0.0.0     # 127.0.0.1 keeps it to this machine, for a reverse proxy
```

Your host or firewall has to let that port through. If you put Studio behind a subdomain or a reverse proxy, set `studio.public-url` (like `https://studio.example.com`) so `/hca studio` shows the right link. The `studio` options apply after `/hca reload`, and [config-reference.md](config-reference.md) lists them all.

Studio uses plain HTTP, so the password and your configs cross the network unencrypted. A reverse proxy with HTTPS in front of it fixes that. Five wrong passwords from one address lock it out for five minutes, and every sign-in, save and command is written to the console and the audit log.

The first time Studio is opened, it downloads Minecraft's textures, font and sounds from Mojang into `plugins/Headcount/studio/cache/`, so the previews look like the game. That's about 30 MB, once. Without them, items show as flat colours. `studio.textures: false` skips the download.

## Finding your way

The sidebar has six groups of pages.

- **Overview** sums up your setup in a paragraph, and lists problems and unsaved changes.
- **Game** and **Server** have a page for each part of the setup. Game covers the cycle, vaults and heads, scoring, outcomes, the shop, titles, trading and statuses. Server covers integrations, hooks, action lists, the sidebar, menus, messages and general settings. Each page shows what players will see as you change it.
- **Live server** shows the game as it is now. **This cycle** shows the countdown and lets you move the end, simulate it, end it or start it over. **Players** ranks everyone, and each player's page shows their vault as a chest, with their effects, titles, purchases and past cycles. **Past cycles**, **Effects**, **Audit log** (which can follow one head from start to finish) and **Console** are there too.
- **Help** starts with **How it works**, which explains a cycle using your own settings, with a glossary. **Recipes** are ready-made setups from the docs that you can apply after seeing exactly what they change. **Why is it blocked?** works out which status stops a player. **Reference** covers commands, permissions, placeholders, actions, status sources, the formula, text formatting and every setting, and **Docs** has these docs.
- **Files** has **Problems**, which lists what Studio finds as you edit, **Raw files** for each file as text, and **Backups**, which also imports and exports your whole setup as a .zip.

Ctrl+K searches settings, pages, commands, placeholders, players and docs. Ctrl+Z and Ctrl+Shift+Z undo and redo across files.

## Action lists

An action list is a named list of actions in `actions.yml`, run by name from outcomes, the shop, hooks and other lists. In Studio, an action that runs a list opens that list right where it's used, so you can change it without leaving the page. Studio shows every other place that runs it, because the change applies to all of them. From there you can also swap in a copy of its actions for just that spot.

The **Action lists** page (under Server) shows every list and where it's used. To make a new one from actions you already have, tick them and choose **Make a list from these**: they move into the new list, and that spot runs it instead. Renaming a list updates every place that runs it.

## Saving

Changes stay in Studio until you save them. Ctrl+S, or the button at the top right, shows every changed line first. Studio only rewrites the lines you changed, so comments and layout stay put.

Before a save, Studio keeps the old file in `plugins/Headcount/backups/studio/`. If something else changed the file since Studio opened it, Studio asks before overwriting. After saving, Studio offers to run `/hca reload` and `/hca selftest` for you.

## Commands

Anything on the live pages that changes the game, like giving points or ending a cycle, runs as a `/hca` command from the console, and Studio asks first. The Console page runs any command and shows its reply. A hard reset still needs its typed codes, so it runs from the Console.

## What you still do elsewhere

Studio doesn't install the plugin, restart the server, or edit `server.properties`, other plugins' configs, or translations other than `lang/en_US.yml`. Restoring the database from a hard-reset backup means copying `headcount.db` back while the server is stopped.
