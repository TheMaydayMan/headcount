#!/bin/bash
# Sets up this test server: Paper 26.3 build 246, server.properties from the template, and the Minecraft EULA
# once you accept it. Headcount needs no other plugin, so by default that's all it downloads.
# The flags add the free plugins Headcount integrates with, at the exact versions it was tested with. Every download
# is checked against its sha256, and files that are already there and match are kept.
# Usage: ./setup.sh [--accept-eula] [--with-bedrock] [--with-plugins] [--plugins-dir <dir>]
#   --with-bedrock       also Geyser and Floodgate, so Bedrock players can join
#   --with-plugins       also every integration plugin: PvPManager, PlaceholderAPI, LuckPerms, EssentialsX,
#                        VaultUnlocked, LibertyBans, AdvancedBan, DiscordSRV, Geyser and Floodgate
#   --plugins-dir <dir>  put those plugins in <dir> instead of plugins/ and skip the rest of the setup
#                        (the bot tests use this)
set -e
ACCEPT="" BEDROCK="" ALL="" DIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --accept-eula) ACCEPT=1 ;;
    --with-bedrock) BEDROCK=1 ;;
    --with-plugins) ALL=1; BEDROCK=1 ;;
    --plugins-dir) [ -n "$2" ] || { echo "--plugins-dir needs a folder" >&2; exit 1; }; DIR=$2; shift ;;
    -h|--help) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option $1 (./setup.sh --help lists them)" >&2; exit 1 ;;
  esac
  shift
done
if [ -n "$DIR" ]; then
  [ -n "$BEDROCK" ] || { echo "--plugins-dir needs --with-plugins or --with-bedrock" >&2; exit 1; }
  mkdir -p "$DIR"
  DIR=$(cd "$DIR" && pwd)   # before the cd below, so a relative path means the caller's folder
fi
cd "$(dirname "$0")"
P=${DIR:-plugins}
mkdir -p "$P"

sha256() {   # sha256sum on Linux, shasum on macOS
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

fetch() {   # fetch <url> <file> <sha256>
  local url=$1 file=$2 sum=$3
  if [ -f "$file" ] && [ "$(sha256 "$file")" = "$sum" ]; then echo "ok        $file"; return; fi
  echo "download  $file"
  curl -sSfL -o "$file.part" "$url"
  if [ "$(sha256 "$file.part")" != "$sum" ]; then
    rm -f "$file.part"; echo "sha256 mismatch for $file (from $url)" >&2; exit 1
  fi
  mv "$file.part" "$file"
}

if [ -z "$DIR" ]; then
  fetch https://fill-data.papermc.io/v1/objects/d158542561e440c4026be7ccb32f551ee88313864792f38ea0ce5d1b88f1b27c/paper-26.3-246.jar \
    paper-26.3-246.jar d158542561e440c4026be7ccb32f551ee88313864792f38ea0ce5d1b88f1b27c
fi
if [ -n "$ALL" ]; then
  fetch https://cdn.modrinth.com/data/1Ffl2uIq/versions/eKgXGdO2/PvPManager.jar \
    "$P/PvPManager-4.0.30.jar" 37681ba31ef9f72609a74ae085f9b5d043efc1e8e18037c33611b08028932b94
  fetch https://cdn.modrinth.com/data/lKEzGugV/versions/pIvQcXW8/PlaceholderAPI-2.12.3.jar \
    "$P/PlaceholderAPI-2.12.3.jar" fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437
  fetch https://cdn.modrinth.com/data/Vebnzrzj/versions/b0mk8uS6/LuckPerms-Bukkit-5.5.71.jar \
    "$P/LuckPerms-Bukkit-5.5.71.jar" 49cecb66fa1fd22a133039a490e9c1e5095a238e7cd66eb9d2a16fe6c897550d
  fetch https://cdn.modrinth.com/data/hXiIvTyT/versions/nY6VN1XH/EssentialsX-2.22.0.jar \
    "$P/EssentialsX-2.22.0.jar" bda4685105977fca2e209820a9f0ad24275bd103390a03236f38e59bfdac58e6
  fetch https://cdn.modrinth.com/data/ayRaM8J7/versions/qZgRzoYs/VaultUnlocked-2.20.3.jar \
    "$P/VaultUnlocked-2.20.3.jar" fbc6651aca11e13376c115df66eeffa79592e79517be398299aa6beb49c22695
  fetch https://cdn.modrinth.com/data/PgXAUxLZ/versions/TUuNKL02/LibertyBans_Release-1.2.0-M1.jar \
    "$P/LibertyBans-1.2.0-M1.jar" 0ffc1ebea7ef753a10a04a9f462c52c8815a43ae0eac716f474fe9655b389951
  fetch https://github.com/DevLeoko/AdvancedBan/releases/download/v2.3.0/AdvancedBan-Bundle-2.3.0-RELEASE.jar \
    "$P/AdvancedBan-2.3.0.jar" 7b4965adab8a8110e28fcc14603be7148273c2e543014d915ddf44a639031dd8
  fetch https://cdn.modrinth.com/data/UmLGoGij/versions/ATlquwiT/DiscordSRV-Build-1.30.5.jar \
    "$P/DiscordSRV-1.30.5.jar" ef2fa1f2eb146c7c77412b7190a7dd33f1fc91282683fe45407739554c8aefef
fi
if [ -n "$BEDROCK" ]; then
  fetch https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds/1298/downloads/spigot \
    "$P/Geyser-Spigot.jar" 4e5740267f8f7e1c0b5d56213e047cfdba57a93c322ace63c05286a5d486c66c
  fetch https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/141/downloads/spigot \
    "$P/Floodgate-Spigot.jar" 21570aff9ce17d6983928e8552777760e1ede5050026b04c686b0ae112e6fd7e
fi
if [ -n "$DIR" ]; then echo "Plugins ready in $DIR"; exit 0; fi

# Plugins an earlier setup.sh downloaded stay until you delete them. Say which ones weren't asked for this time.
extra=""
for f in plugins/PvPManager-*.jar plugins/PlaceholderAPI-*.jar plugins/LuckPerms-*.jar plugins/EssentialsX-*.jar \
         plugins/VaultUnlocked-*.jar plugins/LibertyBans-*.jar plugins/AdvancedBan-*.jar plugins/DiscordSRV-*.jar \
         plugins/Geyser-Spigot.jar plugins/Floodgate-Spigot.jar; do
  [ -e "$f" ] || continue
  case "$f" in plugins/Geyser-*|plugins/Floodgate-*) [ -n "$BEDROCK" ] && continue ;; *) [ -n "$ALL" ] && continue ;; esac
  extra="$extra $(basename "$f")"
done
[ -z "$extra" ] || echo "note      also in plugins/ from an earlier setup:$extra (delete them to run Headcount on its own)"

if [ ! -f server.properties ]; then cp server.properties.template server.properties; echo "wrote     server.properties"; fi

if ! grep -qx "eula=true" eula.txt 2>/dev/null; then
  if [ -z "$ACCEPT" ]; then
    read -r -p "Do you accept the Minecraft EULA (https://aka.ms/MinecraftEULA)? [y/N] " answer
    case "$answer" in y|Y|yes|YES) ;; *) echo "Not accepted; the server won't start until eula.txt says eula=true."; exit 0 ;; esac
  fi
  printf '# Accepted for the local Headcount test server (https://aka.ms/MinecraftEULA).\neula=true\n' > eula.txt
  echo "wrote     eula.txt"
fi

ls plugins/Headcount-*.jar >/dev/null 2>&1 || echo "Headcount isn't here yet: run (cd ../Headcount && ./gradlew build), which copies the jar in."
echo "Ready: ./start.sh"
