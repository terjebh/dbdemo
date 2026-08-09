# dbdemo

Enkel app for å hente data fra databaser med SQL — laget for studenter som
skal øve på SQL mot flere RDBMS-er.

Støttede databaser:
- PostgreSQL
- Oracle
- Microsoft SQL Server
- MySQL/MariaDB

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

## Konfigurasjon (miljøvariabler)

**Ingen passord ligger i kildekoden eller git!** Sett dem som miljøvariabler:

| Variabel | Beskrivelse | Default |
|---|---|---|
| `APP_USER` | Innloggingsbrukernavn | `kurs` |
| `APP_PASSWORD` | Innloggingspassord | `kurs123` |
| `DB_HOST` | Database-server | `noderia.com` |
| `DB_PORT_ORACLE` | Oracle-port | `1521` |
| `DB_PORT_MSSQL` | MS SQL-port | `1433` |
| `ORACLE_SERVICE` | Oracle service-navn | `FREE` |
| `PG_USERNAME` / `PG_PWD` | PostgreSQL-bruker | *(må settes)* |
| `MS_USERNAME` / `MS_PWD` | MS SQL-bruker | *(må settes)* |
| `OR_USERNAME` / `OR_PWD` | Oracle-bruker | *(må settes)* |
| `MY_USERNAME` / `MY_PWD` | MySQL-bruker | *(må settes)* |
| `QUERY_TIMEOUT_SECONDS` | Timeout for spørringer | `15` |
| `QUERY_MAX_ROWS` | Maks antall rader per spørring | `10000` |
| `DB_READONLY` | Read-only-kobling (hindrer uhell) | `true` |

Eksempel:

```bash
export PG_USERNAME=dbdemo PG_PWD='hemmelig'
export MS_USERNAME=kurs1 MS_PWD=':)Kurs123'
java -jar target/dbdemo-*.jar
```

## Sikkerhet

- Passord kun via miljøvariabler — aldri i kode, git eller logger
- Read-only-kobling som standard (studenter kan ikke endre/slette data)
- `queryTimeout` + `maxRows` beskytter serveren mot tunge spørringer
- PreparedStatement for katalog-spørringer (ingen SQL-injeksjon)
- XSS-sikker visning (data escapes i Thymeleaf/JS, aldri rå HTML fra server)

## Kjøretips

- I SQL-editoren: `shift+enter` formaterer, `ctrl+enter` kjører
- Velg database i nedtrekksmenyen — kun databaser du har tilgang til vises
- Tabeller og views for valgt database vises i sidepanelet

## Nexus

Jar-fil: https://nexus.itfakultetet.no/#browse/browse:DBDemo
(Nexus-steg i Jenkins-pipelinen er valgfritt inntil Nexus er installert — se `SKIP_NEXUS`-parameteren.)

## Mål og status

Se [GOAL.md](GOAL.md) for prosjektmålet, akseptkriterier og forbedringsliste.

@ your service

Terje
