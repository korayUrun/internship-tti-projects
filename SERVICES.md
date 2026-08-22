# TTI Platform — Running Services and Ports

> Seven containers on a single machine, one `docker compose`. Definition: `tti-sunucu/docker-compose.yml`.
> Turkish version: [SERVISLER.md](SERVISLER.md) · Credentials: [KULLANICILAR-VE-PAROLALAR.md](KULLANICILAR-VE-PAROLALAR.md)

## Overview

```
                   port 443 (HTTPS) ──────── users (by domain name, NO IP/port)
                   port 80  (HTTP) ────────── redirects to 443 (301)
   tti-proxy (Caddy) ◄───┤
     ├─ hakedis.<DOMAIN> ─► tti-hakedis-server   (internal 8081)
     ├─ rapor.<DOMAIN>   ─► tti-rapor-server      (internal 8080)
     └─ data.<DOMAIN>    ─┬ /api,/admin,/docs ─► tti-data-server (internal 8000)
                          └ everything else   ─► tti-data-panel  (internal 8080)

   tti-db (PostgreSQL 17) ── server-local only (127.0.0.1:5432)

   tti-portainer ◄── port 9443 (IT subnet only) ── IT: container management
```

## Services and ports

| Service | Container | Image | Internal port | Host binding | Network access |
|---|---|---|---|---|---|
| **Reverse proxy** | `tti-proxy` | `caddy:2` | 80, 443 | `0.0.0.0:80`, `0.0.0.0:443` | ✅ **Whole intranet** — by domain name (HTTPS; 80→443) |
| **Portainer** | `tti-portainer` | `portainer/portainer-ce:2.21.5` | 9443 | `0.0.0.0:9443` | ✅ **IT subnet only** (firewall) |
| **Progress (TTI)** | `tti-hakedis-server` | `tti-hakedis-server:<SURUM>` | 8081 | `127.0.0.1:8081` | ❌ via proxy only |
| **Report (TTIDataRapor)** | `tti-rapor-server` | `tti-rapor-server:<SURUM>` | 8080 | `127.0.0.1:8082`¹ | ❌ via proxy only |
| **Central Data — API** | `tti-data-server` | `tti-data-server:<SURUM>` | 8000 | `127.0.0.1:8083`¹ | ❌ via proxy only |
| **Central Data — panel** | `tti-data-panel` | `tti-data-panel:<SURUM>` | 8080 | `127.0.0.1:8084`¹ | ❌ via proxy only |
| **Database** | `tti-db` | `postgres:17` | 5432 | `127.0.0.1:5432` | ❌ server-local only |

¹ Server ports. On this dev machine `8082`/`8083` are taken, so `.env` sets `RAPOR_PORT=8083`,
`DATA_PORT=8093`, `DATA_PANEL_PORT=8094`. `dogrula.sh` reads the ports from `.env` rather than
hard-coding them.

## How to reach each

| Who | What | Address |
|---|---|---|
| Users | TTI Saha Hakediş | `https://hakedis.<DOMAIN>` (http redirects to https) |
| Users | TTIDataRapor | `https://rapor.<DOMAIN>` |
| Users | TTI Central Data (panel) | `https://data.<DOMAIN>` |
| Android apps | TTI Central Data (API) | `https://data.<DOMAIN>/api/...` (`X-API-Key`) |
| IT | Portainer (management) | `https://<server-ip>:9443` (self-signed cert) |
| On the server | Health check | `curl http://localhost:8081/api/saglik` · `:8082` · `:8083` |

- **`<DOMAIN>`** is the `DOMAIN` value in `.env` (`turktelekomint.com` on the server). Mapping `hakedis.<DOMAIN>` → IP is the job of the **corporate DNS** (IT adds an A record), not Docker.
- **HTTPS:** the proxy serves the `*.<DOMAIN>` wildcard CA certificate (`Caddyfile` → `tls`, `certs/`); expires **2027-02-27**, renewal in `ServerDeploy.md` §14. `certs/` is excluded from git/zip.
- **`data.<DOMAIN>` is ONE domain serving two containers:** Caddy splits by path — `/api/*`,
  `/admin/*`, `/docs`, `/openapi.json`, `/static/*`, `/health` go to the API, everything else to
  the panel. Same origin, so the panel calls the API with relative paths and no CORS is needed.
- The apps (`8081`/`8082`/`8083`/`8084`) bind to `127.0.0.1`: not reachable directly from the network, only the proxy reaches them internally. The firewall opens **443 + 80** (plus 9443 for IT); **5432 stays closed**.

## Everyday commands

```bash
cd tti-sunucu
docker compose ps                     # what is running + ports
docker compose logs -f tti-hakedis-server   # live logs
docker compose up -d                  # start / update the stack
docker compose down                   # stop (data preserved — DB volume is external)
bash dogrula.sh                       # platform checks (incl. proxy + port surface)
```

> **Version tag** is a date (`YYYY-MM-DD`), not `dev`/`latest` — rollback depends on it. Rollback:
> set `SURUM` in `.env` to the previous date, `docker compose up -d`.

## Backups

The backup script lives in the **platform layer** (single authority): `tti-sunucu/yedek.sh`. It does
not belong to any one project, so it moved here out of the TTI repo. On the server a nightly
**systemd timer** runs it.

```bash
cd tti-sunucu
bash yedek.sh            # take one backup manually
bash yedek.sh dogrula    # verify the latest backup — read-only, safe in production
```

| | Content | Where |
|---|---|---|
| ✅ Backed up | Document **records** (quantities, prices, status) | database dump (`db/`) |
| ✅ Backed up | Document **attachments** — EK1/EK2, field photos, rapor route files (**PRIMARY DATA**) | upload roots (`yukleme/`) |
| ✅ Backed up | Uploaded **Excel files + rollback JSON snapshots** (**PRIMARY DATA**) | `/srv/veri/tti-data/yukleme` |
| ✅ Backed up | Roles + `tti-sunucu` settings (compose, `.env`, `Caddyfile`, `certs/` — **incl. TLS private key**) | `roller/`, `sunucu-ayarlari/` |
| ❌ Not backed up | Generated PDF/Excel archive (`ARSIV_KOK`) | **regenerated** from the database |

Details, systemd timer setup, and **restore rehearsal**: `tti-hakedis/deploy/RedHatKurulum.md` §12.

> ⚠️ Copy backups off-machine too (a single disk failure loses everything) and **rehearse a restore** —
> an untested backup is not a backup.
