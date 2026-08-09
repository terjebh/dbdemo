# Contributing to dbdemo

Takk for at du vil bidra! 🎉 dbdemo er en enkel web-app for å øve på SQL mot
flere RDBMS-er (PostgreSQL, Oracle, MS SQL Server og MySQL/MariaDB).

## Kom i gang

```bash
git clone https://github.com/terjebh/dbdemo.git
cd dbdemo
./mvnw clean package        # krever JDK 17
java -jar target/dbdemo-*.jar
```

Ved første kjøring viser appen en oppsettsveiviser på `/setup` der du legger
inn admin-bruker og databasetilkoblinger. Konfigurasjonen lagres i
`~/.dbdemo/dbconfig.json` — **aldri** i repoet.

## Feil og forslag

- Åpne et issue på GitHub: https://github.com/terjebh/dbdemo/issues
- Beskriv hva du gjorde, hva du forventet, og hva som skjedde (inkl. loggutdrag)

## Kodebidrag

1. Fork repoet og opprett en branch: `git checkout -b fix/bra-ting`
2. Gjør endringene dine — med tester der det er naturlig
3. Kjør testene: `./mvnw test` — alle må være grønne
4. Committe med beskrivende melding og push til branchen din
5. Åpne en pull request mot `master`

## Kodestandard

- **Språk:** Norsk (bokmål) i UI og kommentarer
- **Stack:** Java 17 + Spring Boot 3, Thymeleaf, Bootstrap 5.3, highlight.js
- **Sikkerhet først:**
  - Ingen passord i kildekode, git, logger eller Docker-image
  - Parametriserte SQL-spørringer (PreparedStatement) for katalog-spørringer
  - Ingen rå HTML fra server (XSS-sikkert) — data escapes i Thymeleaf/JS
- Følg eksisterende kodemønstre (se `model/Dao.java` og `controller/`)

## Sikkerhetsrapporter

Ikke åpne et offentlig issue for sikkerhetsproblemer. Send e-post til
terje@itfakultetet.no i stedet.

## Lisens

Ved å bidra godtar du at bidraget ditt lisensieres under MIT-lisensen
(se [LICENSE](LICENSE)).
