# dbdemo

Enkel app for å hente data fra databaser med SQL — laget for studenter som
skal øve på SQL mot flere RDBMS-er.

Støttede databaser:
- PostgreSQL
- Oracle
- Microsoft SQL Server
- MySQL/MariaDB

## 🚀 Første gangs kjøring (first-run-oppsett)

Ved første kjøring viser appen en **oppsettsveiviser** på `/setup` der du
fyller inn:

1. **Admin-bruker** — brukernavn og passord for innlogging til appen
2. **Databasetilkoblinger** — for hver av de fire RDBMS-ene: vert, port,
   database (eller service-navn for Oracle), brukernavn og passord

Du kan koble til **én eller flere** databaser — de du ikke fyller ut, er
ikke aktive. Hver tilkobling kan **testes** før du lagrer.

Konfigurasjonen lagres som JSON i `~/.dbdemo/dbconfig.json` (utenfor repoet,
med `600`-rettigheter). Overstyr plasseringen med miljøvariabelen
`DBDEMO_CONFIG`. Admin-passordet lagres som bcrypt-hash — aldri klartekst.

Etter oppsettet redirectes alt til innlogging, og `/setup` viser kun
«allerede konfigurert».

## Bygging

```bash
./mvnw clean package        # krever JDK 17
java -jar target/dbdemo-*.jar
```

Docker:

```bash
docker build -t terjebh/dbdemo .
docker run -d --name dbapp -p 8080:8080 terjebh/dbdemo
```

## Konfigurasjon (valgfrie miljøvariabler)

| Variabel | Beskrivelse | Default |
|---|---|---|
| `DBDEMO_CONFIG` | Sti til konfigurasjonsfilen | `~/.dbdemo/dbconfig.json` |
| `QUERY_TIMEOUT_SECONDS` | Timeout for spørringer | `15` |
| `QUERY_MAX_ROWS` | Maks antall rader per spørring | `10000` |
| `DB_READONLY` | Read-only-kobling (hindrer uhell) | `true` |

## Sikkerhet

- **Ingen passord i kildekoden eller git** — alt fylles inn via `/setup`
  og lagres i en lokal fil utenfor repoet (600-rettigheter)
- Admin-passordet lagres som **bcrypt-hash**
- Read-only-kobling som standard (studenter kan ikke endre/slette data)
- `queryTimeout` + `maxRows` beskytter serveren mot tunge spørringer
- PreparedStatement for katalog-spørringer (ingen SQL-injeksjon)
- XSS-sikker visning (data escapes i Thymeleaf/JS, aldri rå HTML fra server)

## Kjøretips

- I SQL-editoren: `shift+enter` formaterer, `ctrl+enter` kjører
- Velg database i nedtrekksmenyen — kun databaser du har tilgang til vises
- Tabeller og views for valgt database vises i sidepanelet

## Nexus / CI

Jar-fil: https://nexus.itfakultetet.no/#browse/browse:DBDemo
Jenkins-pipelinen bygger og laster opp til Nexus (`SKIP_NEXUS`/`SKIP_DOCKER`
-parametre). Se [GOAL.md](GOAL.md) for prosjektmål og status.

@ your service

Terje
