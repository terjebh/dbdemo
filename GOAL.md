# 🎯 GOAL — dbdemo: SQL-webgrensesnitt for studenter

> **Stående mål for prosjektet** (Hermes `/goal`). Dette dokumentet er den
> autoritative målbeskrivelsen; alt arbeid skal styres mot den.

## Mål

Lage et web-grensesnitt der **studenter kan logge inn og skrive SQL-setninger**
mot fire RDBMS-er:

| RDBMS | Driver | Server |
|---|---|---|
| PostgreSQL | JDBC | noderia.com |
| Oracle | JDBC thin | noderia.com:1521 (FREE) |
| Microsoft SQL Server | JDBC | noderia.com:1433 |
| MariaDB/MySQL | JDBC | noderia.com |

**Stack (uforanderlig):** Java + Spring Boot (backend), Thymeleaf (templating),
Bootstrap + highlight.js (frontend). Kjører i Docker, bygges med Maven wrapper.

## Nøkkelkrav (fra Terje)

1. **Innlogging** — én felles kursbruker `kurs` / `kurs123` (Spring Security).
2. **SQL-editor** med fargekoding av SQL (highlight.js) + valg av skin.
3. **Enkel valg av database** — alle databaser brukeren har tilgang til i de 4
   systemene listes opp, og studenten velger.
4. **Resultatvisning** som tabell (DataTables) med header fra metadata.
5. **Tabelliste** per database (tabeller + views) vises ved siden av editoren.
6. **Jenkins-pipeline** som bygger jar, lager Docker-image og laster opp til
   Nexus (Nexus installeres på serveren etter hvert).

## Akseptkriterier (definisjon av ferdig)

- [ ] Appen bygger med `./mvnw clean package` på CI
- [ ] Innlogging fungerer; utlogget bruker omdirigeres til login
- [ ] SQL kan skrives, formateres (`shift+enter`) og kjøres (`ctrl+enter`)
- [ ] Fargekoding (syntax highlighting) virker for alle 4 RDBMS-er
- [ ] Databasevelgeren lister kun databaser/skjemaer brukeren har tilgang til
- [ ] Tabelliste viser tabeller og views for valgt database
- [ ] Resultatsett vises i sorterbar tabell; feil vises pent (ikke rå stacktrace)
- [ ] Ingen passord i kildekode, git eller logger (miljøvariabler i stedet)
- [ ] Docker-image bygger og kjører (Java-versjon matcher pom)
- [ ] Jenkins-pipeline går grønt (Nexus-steg gjøres valgfritt inntil Nexus er oppe)

## Prioriterte forbedringer (funnet i analyse 2026-08-09)

### 🔴 Kritisk (sikkerhet)
1. **Passord i git** — `application.properties` inneholder reelle DB-passord
   (`ms.pwd=:)Kurs123`, `pg.pwd`, `or.pwd`, `my.pwd`). Flyttes til miljøvariabler.
2. **Passord i logg** — `Select.java` (~linje 94) logger brukernavn+passord ved
   ukjent RDBMS. Må fjernes.
3. **XSS** — `Dao.createTableList()` bygger HTML-strenger i Java med rå
   tabellnavn; `TableListRestController` returnerer HTML. Skal returnere data,
   escaping i Thymeleaf.
4. **SQL-injection i katalog-spørringer** — `DBListRestController`/
   `TableListRestController` bygger SQL med strengkonkatenering. Bruk
   `PreparedStatement`.
5. **Ressurslekkasje** — `Dao` lukker ikke `Statement`, og `Connection` lukkes
   bare i noen grener. Bruk `try-with-resources`.
6. **Ubegrenset query** — ingen `queryTimeout`/`maxRows`; en student kan henge
   serveren med `SELECT *` på kjempetabell. Sett read-only + timeout + maxRows.

### 🟠 Viktig (korrekthet)
7. **Java-versjonsmismatch** — pom sier Java 20, Dockerfile bruker `openjdk:17`.
   Samkjør på Java 17 (eller 21 LTS).
8. **Oracle/MSSQL db-velger virker ikke** — Oracle-URL-en har hardkodet `FREE`
   (db-parameteren ignoreres), MSSQL har hardkodet `databaseName=hr`. Enten fiks
   URL-byggingen eller fjern db-velgeren for disse.
9. **Oracle-dblisten er feil** — viser `ALL_USERS like 'K%'` (alle brukere som
   begynner på K), ikke brukerens skjemaer. Skal vise skjemaer brukeren har
   tilgang til (`ALL_TABLES`-eierskap / `SESSION_PRIVS`).
10. **MS SQL-dblisten viser alle server-databaser** — filtrer på tilgang.
11. **Feilhåndtering** — `createResultset` returnerer `Object` (ResultSet
    ELLER feilmelding). Kast exceptions og håndter i controller.
12. **`dbList`-skjult-felt** refererer til modell-attributt som aldri settes.

### 🟡 Forbedringer (UX/DevOps)
13. **Logg-filer i git** (`dbdemo.log`, `logs/*.gz`) — fjern fra repo, legg til
    i `.gitignore` (er der, men filene ble committed før).
14. **`lang="en"`** → `lang="no"` på alle sider.
15. **`document.execCommand("insertHTML")`** er deprecated — erstatt med
    tryggere innsetting av linjeskift.
16. **Bootstrap 5.1.3 → 5.3.x** (siste).
17. **SQL-formatering** — `shift+enter` gjør bare re-highlight i dag; bruk
    `sql-formatter` for ekte formatering.
18. **Nexus-steg i Jenkinsfile** — gjør valgfritt (param) til Nexus er oppe.
19. **Dockerfile** — `eclipse-temurin` + helsesjekk + ikke-root.
20. **Vis antall rader og kjøretid** i resultatvisningen.

## Arbeidsmåte

- Språk: norsk (bokmål) i UI og kommunikasjon
- Alt testes lokalt/bygget før leveranse
- Hver leveranse: zip av repo + kort oppsummering
- Sikkerhet først: ingen hemmeligheter i kode, git, logger eller Docker-image
