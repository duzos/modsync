# ModSync

A Minecraft Forge mod that allows clients to automatically download missing mods from a server, making it easy to join modded servers without manual mod management.

## Features

- 🔄 **Automatic Mod Detection** - "Sync Mods" button appears on the mod mismatch disconnect screen
- 🔒 **SHA-256 Verification** - Downloaded mods are verified against server checksums
- 🔁 **Auto-Reconnect** - Automatically reconnects to the server after restart
- 🌐 **IPv4 & IPv6 Support** - Works with all server address formats
- ✅ **User Consent** - Always asks before downloading anything

## How It Works

### Server Side
When the mod is installed on a server, it starts an HTTP server that:
- Serves a list of installed mods with SHA-256 checksums
- Allows clients to download mod JARs

**Default HTTP port:** 25566 (configurable)

### Client Side
1. Attempt to connect to a modded server
2. If kicked due to mod mismatch, click "Sync Mods"
3. Review the list of missing mods and confirm download
4. Mods are downloaded to your `mods` folder
5. Click "Restart Now" or "Exit Game" to restart
6. Game automatically reconnects to the server

## Installation

1. Download from [Releases](https://github.com/duzos/modsync/releases)
2. Place in your `mods` folder
3. **Required on both server and client**

## Configuration

Config file: `config/modsync-common.toml`

```toml
[server]
# HTTP port for serving mod files
httpPort = 25566

# Shared secret for downloads (leave empty to disable auth)
serverSecret = ""

[client]
# Auto-start download when mods are missing (still shows consent)
autoDownload = false

# Auto-restart after download completes
autoRestart = false
```

## Security

⚠️ **Only sync mods from servers you trust!**

Mods have full access to your computer. While ModSync verifies file integrity, it cannot guarantee mod safety.

## Requirements

- Minecraft 1.20.1
- Forge 47.4+

## Building

```bash
git clone https://github.com/duzos/modsync.git
cd modsync
./gradlew build
```

Output: `build/libs/`

## License

[MIT](LICENSE)

## Contributing

Issues and PRs welcome!

