# Text, MiniMessage and placeholders

## Where text lives

`lang/<language>.yml` holds everything Headcount says itself: command replies, errors, confirmations, the help, `/hc effects` and its durations, the included PvP and AFK messages, `/hca inspect` and `/hca integrations`.

The other config files hold the text you design. That covers menu titles, item and head names and lore, the sidebar, and every message inside an action list, whether it's in a hook, an outcome or a shop entry.

Each file's header says which kind it holds. Console log lines, self-test details and permission descriptions are written in English in the code.

## Formatting

All of this text uses [MiniMessage](https://docs.advntr.dev/minimessage/format): `<red>`, `<bold>`, `<gradient:gold:yellow>`, `<click:run_command:'/trade accept'>`, `<newline>` and so on. An empty string (`""`) in the language file switches that message off.

Headcount fills in a text in this order:

1. `%headcount_<name>%` placeholders (see [placeholders.md](placeholders.md)), which are always available.
2. Any other `%plugin_value%` placeholder, through PlaceholderAPI when it is installed. Legacy colors from other plugins are converted.
3. `<variables>` that the specific text provides, such as `<player>`, `<price>` or `<cycle>`. They are listed next to each option. Values that come from players, like names, go in as plain text and can't add formatting.

To show a literal `<`, write `\<` in MiniMessage, which is `\\<` inside a double-quoted YAML string. `<prefix>` from the language file works in every text.

Item names and lore are not italic unless you ask for it. Durations in text use the unit names under `time:` in the language file, and `general.time-units.shown` sets how many units appear.

Headcount adds a `<score>` tag for the vault title and info button. It shows the owner's score if the cycle ended now, colored by `vault.gui.score-colors`. `<score:green:red:gray>` sets the positive, negative and zero colors in place. Each can be any color or tag, and an empty one keeps the default.

Values that come from the configs or the language file, such as a tier name, `<streak_text>` or an outcome name, keep their formatting.

Headcount Studio previews any text as it looks in game, and its MiniMessage reference has a box to try tags in.

## Translating

To translate, copy `lang/en_US.yml` to `lang/<code>.yml`, translate the values, and set `general.language`. Any key missing from your file falls back to the bundled English.
