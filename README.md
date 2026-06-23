<div align="center">

# ModSync

### Join a modded server without hunting down a single mod. It syncs them for you.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=for-the-badge)
![GitHub Release](https://img.shields.io/github/v/release/duzos/modsync?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

[<img alt="forge" height="52" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/forge_vector.svg">](https://files.minecraftforge.net)

**by [Duzo](https://duzo.is-a.dev/)**

</div>

## What is it?

A Minecraft Forge mod that lets clients **automatically download the mods a server is missing** — so joining a modded server is one click instead of a manual mod hunt. Think "gmod addon share", but for Minecraft.

## Features

- 🔄 **Automatic mod detection** — a "Sync Mods" button appears on the mod-mismatch disconnect screen.
- 🔒 **SHA-256 verification** — downloaded mods are checked against the server's checksums.
- 🔁 **Auto-reconnect** — rejoins the server after the restart.
- 🌐 **IPv4 & IPv6** — works with all server address formats.
- ✅ **User consent** — always asks before downloading anything.

## How it works

**Server side** — running the mod starts a small HTTP server (default port **25566**, configurable) that serves the installed mod list with SHA-256 checksums and lets clients download the JARs.

**Client side**
1. Try to connect to a modded server.
2. If you're kicked for a mod mismatch, click **Sync Mods**.
3. Review the missing mods and confirm the download.
4. Mods drop into your `mods` folder.
5. Click **Restart Now** — the game reconnects automatically.

> **Required on both the server and the client.**

## Configuration

`config/modsync-common.toml`:

```toml
[server]
httpPort = 25566      # HTTP port for serving mod files
serverSecret = ""     # shared secret for downloads (empty = no auth)

[client]
autoDownload = false  # start downloading missing mods automatically (still asks consent)
autoRestart = false   # restart automatically after download
```

## ⚠️ Security

**Only sync mods from servers you trust.** Mods have full access to your computer — ModSync verifies file integrity, but it can't vouch for what a mod actually does.

## Requirements

- Minecraft **1.20.1**
- Forge **47.4+**

## Building

```bash
git clone https://github.com/duzos/modsync.git
cd modsync
./gradlew build      # output in build/libs/
```

## Links

- [Releases](https://github.com/duzos/modsync/releases)
- [GitHub](https://github.com/duzos/modsync)

## License

[MIT](LICENSE). Issues and PRs welcome.
