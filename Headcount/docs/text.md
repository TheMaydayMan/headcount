# Text, MiniMessage and placeholders

Every text Headcount shows comes from a config file or from `lang/<language>.yml`, and uses [MiniMessage](https://docs.advntr.dev/minimessage/format): `<red>`, `<bold>`, `<gradient:gold:yellow>`, `<click:run_command:'/trade accept'>`, `<newline>` and so on. An empty string (`""`) in the language file switches that message off.

In order, a text is filled in with:

1. `%headcount_<name>%` placeholders (see [placeholders.md](placeholders.md)), always available.
2. Any other `%plugin_value%` placeholder, through PlaceholderAPI when it is installed. Legacy colors from other plugins are converted.
3. `<variables>` that the specific text provides, such as `<player>`, `<price>` or `<cycle>`. They are listed next to each option. Values that come from players (like names) are inserted as plain text and can never inject formatting. To show a literal `<`, write `\<` in MiniMessage, which is `\\<` inside a double-quoted YAML string.

`<prefix>` (from the language file) is available in every text.

Item names and lore are not italic unless you ask for it. Durations in text use the unit names under `time:` in the language file, and `general.time-units.shown` controls how many units appear.

To translate, copy `lang/en_US.yml` to `lang/<code>.yml`, translate the values, and set `general.language`. Any key missing from your file falls back to the bundled English.
