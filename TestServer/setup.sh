#!/bin/bash
# Sets up this test server: Paper 26.3 build 246 and the free plugins Headcount integrates with, at the exact
# versions it was tested with (each checked against its sha256), server.properties from the template, and the
# Minecraft EULA once you accept it. Files that are already there and match are kept.
# Usage: ./setup.sh [--accept-eula]
set -e
cd "$(dirname "$0")"
ACCEPT=""
[ "$1" = "--accept-eula" ] && ACCEPT=1
mkdir -p plugins

fetch() {   # fetch <url> <file> <sha256>
  local url=$1 file=$2 sum=$3
  if [ -f "$file" ] && echo "$sum  $file" | sha256sum -c --quiet - 2>/dev/null; then echo "ok        $file"; return; fi
  echo "download  $file"
  curl -sSfL -o "$file.part" "$url"
  if ! echo "$sum  $file.part" | sha256sum -c --quiet - 2>/dev/null; then
    rm -f "$file.part"; echo "sha256 mismatch for $file (from $url)" >&2; exit 1
  fi
  mv "$file.part" "$file"
}

fetch https://fill-data.papermc.io/v1/objects/d158542561e440c4026be7ccb32f551ee88313864792f38ea0ce5d1b88f1b27c/paper-26.3-246.jar \
  paper-26.3-246.jar d158542561e440c4026be7ccb32f551ee88313864792f38ea0ce5d1b88f1b27c
fetch https://cdn.modrinth.com/data/1Ffl2uIq/versions/eKgXGdO2/PvPManager.jar \
  plugins/PvPManager-4.0.30.jar 37681ba31ef9f72609a74ae085f9b5d043efc1e8e18037c33611b08028932b94
fetch https://cdn.modrinth.com/data/lKEzGugV/versions/pIvQcXW8/PlaceholderAPI-2.12.3.jar \
  plugins/PlaceholderAPI-2.12.3.jar fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437
fetch https://cdn.modrinth.com/data/Vebnzrzj/versions/b0mk8uS6/LuckPerms-Bukkit-5.5.71.jar \
  plugins/LuckPerms-Bukkit-5.5.71.jar 49cecb66fa1fd22a133039a490e9c1e5095a238e7cd66eb9d2a16fe6c897550d
fetch https://cdn.modrinth.com/data/hXiIvTyT/versions/nY6VN1XH/EssentialsX-2.22.0.jar \
  plugins/EssentialsX-2.22.0.jar bda4685105977fca2e209820a9f0ad24275bd103390a03236f38e59bfdac58e6
fetch https://cdn.modrinth.com/data/ayRaM8J7/versions/qZgRzoYs/VaultUnlocked-2.20.3.jar \
  plugins/VaultUnlocked-2.20.3.jar fbc6651aca11e13376c115df66eeffa79592e79517be398299aa6beb49c22695
fetch https://cdn.modrinth.com/data/PgXAUxLZ/versions/TUuNKL02/LibertyBans_Release-1.2.0-M1.jar \
  plugins/LibertyBans-1.2.0-M1.jar 0ffc1ebea7ef753a10a04a9f462c52c8815a43ae0eac716f474fe9655b389951
fetch https://github.com/DevLeoko/AdvancedBan/releases/download/v2.3.0/AdvancedBan-Bundle-2.3.0-RELEASE.jar \
  plugins/AdvancedBan-2.3.0.jar 7b4965adab8a8110e28fcc14603be7148273c2e543014d915ddf44a639031dd8
fetch https://cdn.modrinth.com/data/UmLGoGij/versions/ATlquwiT/DiscordSRV-Build-1.30.5.jar \
  plugins/DiscordSRV-1.30.5.jar ef2fa1f2eb146c7c77412b7190a7dd33f1fc91282683fe45407739554c8aefef
fetch https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds/1298/downloads/spigot \
  plugins/Geyser-Spigot.jar 4e5740267f8f7e1c0b5d56213e047cfdba57a93c322ace63c05286a5d486c66c
fetch https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/141/downloads/spigot \
  plugins/Floodgate-Spigot.jar 21570aff9ce17d6983928e8552777760e1ede5050026b04c686b0ae112e6fd7e

if [ ! -f server.properties ]; then cp server.properties.template server.properties; echo "wrote     server.properties"; fi

if ! grep -qx "eula=true" eula.txt 2>/dev/null; then
  if [ -z "$ACCEPT" ]; then
    read -r -p "Do you accept the Minecraft EULA (https://aka.ms/MinecraftEULA)? [y/N] " answer
    case "$answer" in y|Y|yes|YES) ;; *) echo "Not accepted; the server won't start until eula.txt says eula=true."; exit 0 ;; esac
  fi
  printf '# Accepted for the local Headcount test server (https://aka.ms/MinecraftEULA).\neula=true\n' > eula.txt
  echo "wrote     eula.txt"
fi

[ -f plugins/Headcount-1.0.0.jar ] || echo "Headcount isn't here yet: run (cd ../Headcount && ./gradlew build), which copies the jar in."
echo "Ready: ./start.sh"
