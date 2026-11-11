# Text, MiniMessage and placeholders

## Where text lives

- **`lang/<language>.yml`**: everything Headcount itself says: command replies, errors, confirmations, the help, `/hc effects` and its durations, the included PvP and AFK messages, `/hca inspect` and `/hca integrations`.
- **Every other config file**: text you design: menu titles, item names and lore, head names and lore, the sidebar, and every message inside an action list (hooks, outcomes, shop entries).

Each file's header says which kind it holds. Console log lines, self-test details and permission descriptions stay in English in the code.

## Formatting

Every text Headcount shows comes from a config file or from `lang/<language>.yml`, and uses [MiniMessage](https://docs.advntr.dev/minimessage/format): `<red>`, `<bold>`, `<gradient:gold:yellow>`, `<click:run_command:'/trade accept'>`, `<newline>` and so on. An empty string (`""`) in the language file switches that message off.

In order, a text is filled in with:

1. `%headcount_<name>%` placeholders (see [placeholders.md](placeholders.md)), always available.
2. Any other `%plugin_value%` placeholder, through PlaceholderAPI when it is installed. Legacy colors from other plugins are converted.
3. `<variables>` that the specific text provides, such as `<player>`, `<price>` or `<cycle>`. They are listed next to each option. Values that come from players (like names) are inserted as plain text and can never inject formatting. To show a literal `<`, write `\<` in MiniMessage, which is `\\<` inside a double-quoted YAML string.

`<prefix>` (from the language file) is available in every text.

Item names and lore are not italic unless you ask for it. Durations in text use the unit names under `time:` in the language file, and `general.time-units.shown` controls how many units appear.

Headcount's extra tags:

- `<score>` in the vault title and info button: the owner's score if the cycle ended now, colored by `vault.gui.score-colors`. `<score:green:red:gray>` sets the positive, negative and zero colors in place (any color or tag; an empty one keeps the default).
- Values that come from the configs or the language file (a tier name, `<streak_text>`, an outcome name) keep their formatting; values that come from players never do.

Headcount Studio previews any text as it looks in game, and has a full MiniMessage reference with a live "try it" box.

## Translating

To translate, copy `lang/en_US.yml` to `lang/<code>.yml`, translate the values, and set `general.language`. Any key missing from your file falls back to the bundled English.
