// DBApp SQL-editor — CodeMirror 6 med live fargekoding, intellisense,
// trestruktur (databaser → tabeller/views) og resultatpanel uten side-reload.
import { EditorView, keymap, lineNumbers, highlightActiveLine, drawSelection, dropCursor, rectangularSelection, crosshairCursor } from "@codemirror/view";
import { EditorState, Compartment, EditorSelection, Prec } from "@codemirror/state";
import { history, defaultKeymap, historyKeymap, indentWithTab } from "@codemirror/commands";
import { syntaxHighlighting, defaultHighlightStyle, bracketMatching, indentOnInput, foldGutter, foldKeymap } from "@codemirror/language";
import { closeBrackets, autocompletion, closeBracketsKeymap, startCompletion, acceptCompletion, closeCompletion, moveCompletionSelection } from "@codemirror/autocomplete";
import { highlightSelectionMatches, searchKeymap } from "@codemirror/search";
import { sql as sqlLang, PostgreSQL, MySQL, MSSQL, PLSQL, SQLite } from "@codemirror/lang-sql";
import { oneDark, oneDarkHighlightStyle } from "@codemirror/theme-one-dark";

// Dialekter per databasesystem (rdbms_sti)
const DIALECT = {
  postgres: PostgreSQL,
  microsoft: MSSQL,
  oracle: PLSQL,
  mysql: MySQL,
  sqlite: SQLite,
};

const MØRK_TEMA = [
  oneDark,
  syntaxHighlighting(oneDarkHighlightStyle, { fallback: true }),
];

const LYS_TEMA = [
  syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
];

// Standard CodeMirror-utvidelser (uten autocomplete-keymap — den håndteres
// separat slik at Enter alltid gir linjeskift og Tab aksepterer forslag)
const grunnOppsett = [
  lineNumbers(),
  highlightActiveLine(),
  drawSelection(),
  dropCursor(),
  rectangularSelection(),
  crosshairCursor(),
  highlightSelectionMatches(),
  history(),
  foldGutter(),
  bracketMatching(),
  indentOnInput(),
  closeBrackets(),
  // Viktig: defaultKeymap: false — ellers fanger autocomplete Enter med
  // Prec.highest og ERSTATTER teksten når popupen er åpen (f.eks. skriver
  // man «insert», popupen viser INSERT, Enter → teksten blir ødelagt).
  // Vi bruker egen keymap: Tab aksepterer forslag, Enter lager linjeskift.
  autocompletion({ defaultKeymap: false }),
  keymap.of([
    // Autocomplete-taster MÅ komme FØR defaultKeymap, ellers vinner
    // defaultKeymap sine piltaster (cursor-bevegelse) over
    // moveCompletionSelection, og man kan ikke navigere i forslagene.
    // Enter er utelatt — den skal alltid gi linjeskift, ikke akseptere.
    { key: "Ctrl-Space", run: startCompletion },
    { key: "Escape", run: closeCompletion },
    { key: "ArrowDown", run: moveCompletionSelection(true) },
    { key: "ArrowUp", run: moveCompletionSelection(false) },
    { key: "PageDown", run: moveCompletionSelection(true, "page") },
    { key: "PageUp", run: moveCompletionSelection(false, "page") },
    { key: "Tab", run: acceptCompletion },
    ...closeBracketsKeymap,
    ...defaultKeymap,
    ...searchKeymap,
    ...historyKeymap,
    ...foldKeymap,
    indentWithTab, // fallback: Tab indenter når ingen autocomplete er åpen
  ]),
];

// Sender XSS-sikkert tekst inn i et element (all tekst via textContent)
function settTekst(el, tekst) {
  el.textContent = tekst == null ? "" : String(tekst);
}

export function initDbDemoEditor() {
  try {
    handleOnDocumentLoaded();
    return "OK";
  } catch (e) {
    console.error("dbdemo-editor feil:", e);
    return "FEIL: " + e.message;
  }
}

window.initDbDemoEditor = initDbDemoEditor;

function handleOnDocumentLoaded() {
  const feilMelding = document.getElementById("feilmelding");
  const systemSelect = document.getElementById("systemSelect");
  const query = document.getElementById("query");
  const sql = document.getElementById("sql");
  const rdbms_sti = document.getElementById("rdbms_sti");
  const db = document.getElementById("db");
  const tre = document.getElementById("tre");
  const skinSelect = document.getElementById("skinSelect");
  const editorContainer = document.getElementById("editorContainer");
  const editorSplitter = document.getElementById("editorSplitter");
  const resultatPanel = document.getElementById("resultatPanel");
  const resultatStatus = document.getElementById("resultatStatus");
  const resultatInnhold = document.getElementById("resultatInnhold");
  const csrfToken = document.querySelector('input[name="_csrf"]')?.value || "";
  const faneListe = document.getElementById("faneListe");

  // Aktivt databasesystem — MUTERBAR: byttes per fane (nedtrekksmenyen
  // gjelder kun den aktive fanen; de andre fanene beholder sitt system).
  let rdbms = rdbms_sti.value;
  let dialect = DIALECT[rdbms] || PostgreSQL;

  // Kompartment for språk/autocomplete — kan rekonfigureres når DB endres
  const sqlCompartment = new Compartment();
  const temaCompartment = new Compartment();

  // Autocomplete-schema: tabell → [kolonner]. Fylles fra REST-endepunktet.
  let schema = {};

  // Formaterer SQL med sql-formatter (global, fra head.html) og oppdaterer editoren
  function formaterSQL() {
    const doc = editor.state.doc.toString().trim();
    if (!doc || !window.sqlFormatter) return true;
    const språk = { postgres: "postgresql", microsoft: "tsql", oracle: "plsql", mysql: "sql" }[rdbms] || "sql";
    const formatert = sqlFormatter.format(doc, { language: språk });
    editor.dispatch({
      changes: { from: 0, to: editor.state.doc.length, insert: formatert },
    });
    editor.focus();
    return true;
  }

  // Gjenkjenner DDL-setninger (CREATE/DROP/ALTER TABLE/VIEW) og trekker ut
  // objekttype + navn. Returnerer {type, navn, handling} eller null.
  function analyserDdl(q) {
    const uq = " " + q.toUpperCase().replace(/\s+/g, " ").trim();
    // Tillater mellomrom i navn (f.eks. "mitt skjema") — første ord er nok
    // for vanlige navn, men ordene bindes sammen
    const navnMønster = "([A-Z0-9_.\\\"\\$\\-]+(?:\\s+[A-Z0-9_.\\\"\\$\\-]+)*)";
    const finn = (regex) => {
      const m = uq.match(regex);
      if (!m) return null;
      // Fjern evt. sitat og skjema-prefiks fra navnet (behold siste del)
      let navn = (m[1] || "").replace(/"/g, "").split(".").pop();
      // Kutt ved kjente nøkkelord (ALTER TABLE … ADD COLUMN, DROP SCHEMA …
      // CASCADE, CREATE VIEW … AS …) slik at navnet blir rent
      navn = navn.replace(/\s+(?:AS|CASCADE|RESTRICT|ADD|COLUMN|SET|DROP|ON|TO|USING|FROM|WITH|ALTER|CREATE|RENAME|TABLESPACE).*$/i, "");
      return navn.trim();
    };
    let navn = finn(new RegExp("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" + navnMønster));
    if (navn) return { type: "Tabell", navn, handling: "opprettet" };
    navn = finn(new RegExp("CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+" + navnMønster));
    if (navn) return { type: "View", navn, handling: "opprettet" };
    navn = finn(new RegExp("CREATE\\s+SCHEMA\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" + navnMønster));
    if (navn) return { type: "Schema", navn, handling: "opprettet" };
    navn = finn(new RegExp("DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?" + navnMønster));
    if (navn) return { type: "Tabell", navn, handling: "slettet" };
    navn = finn(new RegExp("DROP\\s+VIEW\\s+(?:IF\\s+EXISTS\\s+)?" + navnMønster));
    if (navn) return { type: "View", navn, handling: "slettet" };
    navn = finn(new RegExp("DROP\\s+SCHEMA\\s+(?:IF\\s+EXISTS\\s+)?" + navnMønster));
    if (navn) return { type: "Schema", navn, handling: "slettet" };
    navn = finn(new RegExp("ALTER\\s+TABLE\\s+" + navnMønster));
    if (navn) return { type: "Tabell", navn, handling: "endret" };
    return null;
  }

  // Viser «Tabell X opprettet»-meldingen i statusfeltet (DDL ga ingen rader)
  function visDdlMelding(ddl) {
    skjulResultat();
    feilMelding.style.display = "none";
    resultatStatus.textContent = ddl.type + " " + ddl.navn + " " + ddl.handling;
  }

  // Kjører SQL via REST uten side-reload — resultatet vises i panelet under.
  // Hvis brukeren har MARKERT tekst, kjøres kun den markerte setningen
  // (evt. flere markerte setninger atskilt med semikolon) — ellers hele feltet.
  function kjørSQL() {
    const sel = editor.state.selection.main;
    const harMarkering = !sel.empty;
    const q = harMarkering
      ? editor.state.sliceDoc(sel.from, sel.to).trim()
      : editor.state.doc.toString().trim();

    if (!q) {
      visFeil(window.dbAppTekster ? window.dbAppTekster().feilTom : "SQL-spørringen er tom");
      return true;
    }
    query.value = q;

    const url = rdbms === "sqlite"
      ? `/rest/kjor/sqlite`
      : `/rest/kjor/${rdbms}`;
    const body = JSON.stringify({ db: db.value, query: q });

    resultatStatus.textContent = window.dbAppTekster ? window.dbAppTekster().kjorer : "Kjører …";
    console.log("[kjørSQL]", url, body.slice(0, 80));
    fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
      body,
    })
      .then((response) => {
        console.log("[kjørSQL] status:", response.status);
        return response.json().then((data) => ({ ok: response.ok, data }));
      })
      .then(({ ok, data }) => {
        console.log("[kjørSQL] data:", JSON.stringify(data).slice(0, 100));
        if (!ok || data.feil) {
          visFeil(data.feil || "Kunne ikke kjøre spørringen", data.tilkobling === true);
          return;
        }
        skjulFeil();
        // psql-oppførsel: «SET search_path TO skjema» → vis meldingen
        if (data.melding) {
          skjulResultat();
          feilMelding.style.display = "none";
          resultatStatus.textContent = data.melding;
          byggTre();
          return;
        }
        // DDL (CREATE/DROP/ALTER TABLE/VIEW/SCHEMA): vis «Tabell X opprettet/slettet»
        // i stedet for «No results» og oppdater trestrukturen automatisk
        const ddl = analyserDdl(q);
        if (ddl) {
          visDdlMelding(ddl);
          byggTre();
          return;
        }
        visResultat(data.header || [], data.rows || [], data.tidMs);
      })
      .catch((err) => {
        console.error("[kjørSQL] feil:", err);
        visFeil("Nettverksfeil: " + err.message, true);
      });
    return true;
  }

  // Viser resultatet i panelet under editoren (pgAdmin4-stil)
  let dataTable = null;
  function visResultat(header, rader, tidMs) {
    if (dataTable) {
      dataTable.destroy();
      dataTable = null;
    }
    resultatInnhold.innerHTML = "";

    // DDL/INSERT gir ingen kolonner → vis bare status
    if (!header || header.length === 0) {
      resultatStatus.textContent = "Spørringen ble utført (ingen rader returnert)";
      return;
    }

    const table = document.createElement("table");
    table.id = "resultTable";
    // DataTables' egne klasser (stripe/hover) + våre tema-regler — ingen
    // Bootstrap-table-klasser (de setter hvit bakgrunn på td-cellene)
    table.className = "display stripe hover";

    const thead = document.createElement("thead");
    const trh = document.createElement("tr");
    // Linjenummerering: fast #-kolonne først (pgAdmin4-stil)
    const thNr = document.createElement("th");
    thNr.className = "rad-nr-kol";
    settTekst(thNr, "#");
    trh.appendChild(thNr);
    header.forEach((h) => {
      const th = document.createElement("th");
      settTekst(th, h);
      trh.appendChild(th);
    });
    thead.appendChild(trh);
    table.appendChild(thead);

    const tbody = document.createElement("tbody");
    rader.forEach((rad, indeks) => {
      const tr = document.createElement("tr");
      const tdNr = document.createElement("td");
      tdNr.className = "rad-nr-kol";
      settTekst(tdNr, String(indeks + 1));
      tr.appendChild(tdNr);
      header.forEach((_, i) => {
        const td = document.createElement("td");
        settTekst(td, rad[i]);
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    resultatInnhold.appendChild(table);

    if (window.DataTable) {
      dataTable = new DataTable("#resultTable", {
        // order: [] = ingen INITIAL sortering, men klikk på kolonneoverskrift
        // sorterer fortsatt (order: false deaktiverer sorteringen helt)
        order: [],
        // Alle rader som default; velgeren lar deg bytte til 10/25/50
        lengthMenu: [[10, 25, 50, -1], [10, 25, 50, "Alle"]],
        pageLength: -1,
        // Språk: norsk/engelsk fra i18n.js (flagg-knappen i menyen)
        language: (window.dbAppTekster ? window.dbAppTekster() : { datatables: {} }).datatables,
        // Smale tabeller fyller ikke hele bredden (pgAdmin4-stil) —
        // kolonnene blir så brede som innholdet krever
        autoWidth: true,
      });
      settOppPiltastNavigasjon();
    }
    // Antall rader + utførelsestid (pgAdmin4-stil: «X rader, Y ms»)
    const raderTekst = rader.length + " " + (window.dbAppTekster ? window.dbAppTekster().rader : "rader");
    resultatStatus.textContent = (tidMs != null && tidMs >= 0)
      ? raderTekst + ", " + formaterTid(tidMs)
      : raderTekst;
    // Resultat vises → feilmeldingen skjules
    feilMelding.style.display = "none";
    resultatInnhold.style.display = "";
  }

  // Formaterer utførelsestid: < 1 s → ms, ellers s med én desimal
  function formaterTid(tidMs) {
    if (tidMs < 1000) return tidMs + " ms";
    return (tidMs / 1000).toFixed(1) + " s";
  }

  function visFeil(melding, erTilkoblingsfeil) {
    // Feilmeldingen vises i resultatpanelet (der tabellen ellers ville stått)
    skjulResultat();
    feilMelding.innerHTML = "";
    const span = document.createElement("span");
    settTekst(span, melding);
    feilMelding.appendChild(span);
    // Lenken til tilkoblingsinfo vises KUN for feil som har med tilkobling å gjøre
    if (erTilkoblingsfeil) {
      feilMelding.appendChild(document.createElement("br"));
      const lenke = document.createElement("a");
      lenke.href = "/setup";
      settTekst(lenke, "Oppdater tilkoblingsinformasjonen →");
      feilMelding.appendChild(lenke);
    }
    feilMelding.style.display = "block";
    resultatInnhold.style.display = "none";
  }

  function skjulFeil() {
    feilMelding.innerHTML = "";
    feilMelding.style.display = "none";
    resultatInnhold.style.display = "";
  }

  function skjulResultat() {
    resultatInnhold.innerHTML = "";
    if (dataTable) {
      dataTable.destroy();
      dataTable = null;
    }
  }

  // Viser en feilmelding KUN hvis feltet ikke allerede har innhold
  // (server-rendret feil skal ikke overskrives av JS-feil)
  function visFeilHvisIkkeSatt(melding) {
    if (feilMelding.textContent.trim() === "") {
      visFeil(melding, false);
    }
  }

  // ================= Trestruktur =================
  const SYSTEMNAVN = {
    postgres: "PostgreSQL",
    microsoft: "Microsoft SQL",
    oracle: "Oracle",
    mysql: "MySQL",
    sqlite: "SQLite",
  };

  function byggTre() {
    tre.innerHTML = "";
    // Rot: databasesystemet
    const rotNode = lagNode({
      tekst: SYSTEMNAVN[rdbms] || rdbms,
      ikon: "🗄️",
      gren: true,
      åpen: true,
    });
    tre.appendChild(rotNode);

    const rotBarn = document.createElement("div");
    rotBarn.className = "tre-barn";
    tre.appendChild(rotBarn);

    // For SQLite: list brukerens databaser
    if (rdbms === "sqlite") {
      hentJson(`/rest/get/dblist/sqlite`)
        .then((liste) => {
          if (!Array.isArray(liste)) return;
          liste.forEach((dbNavn) => {
            leggTilDbNode(rotBarn, dbNavn, `/rest/get/tablelist/sqlite/${encodeURIComponent(dbNavn)}`);
          });
        })
        .catch(() => {});
      return;
    }

    // Vanlige RDBMS: dblist → per database: tabeller/views gruppert på skjema
    hentJson(`/rest/get/dblist/${rdbms}`)
      .then((liste) => {
        if (!Array.isArray(liste)) {
          if (typeof liste === "string") visFeilHvisIkkeSatt(liste);
          return;
        }
        liste.forEach((dbNavn) => {
          leggTilDbNode(rotBarn, dbNavn, `/rest/get/tablelist/${rdbms}/${encodeURIComponent(dbNavn)}`);
        });
      })
      .catch(() => {});
  }

  // Legger til én database-node i treet med lazy-load av tabeller/views.
  // Klikk: velger databasen og toggler åpen/lukket.
  function leggTilDbNode(rotBarn, dbNavn, tablelistUrl) {
    const dbNode = document.createElement("div");
    dbNode.className = "tre-node tre-gren";
    const ik = document.createElement("span");
    ik.className = "tre-ikon";
    ik.textContent = dbNavn === db.value ? "▾" : "▸";
    const lab = document.createElement("span");
    lab.textContent = dbNavn;
    dbNode.appendChild(ik);
    dbNode.appendChild(lab);
    if (dbNavn === db.value) dbNode.classList.add("tre-valgt");
    rotBarn.appendChild(dbNode);

    const dbBarn = document.createElement("div");
    dbBarn.className = "tre-barn";
    rotBarn.appendChild(dbBarn);

    const åpen = dbNavn === db.value;
    if (!åpen) dbBarn.style.display = "none";
    let lastet = åpen;

    if (åpen) {
      hentJson(tablelistUrl)
        .then((rader) => byggTabellGrener(dbBarn, rader, dbNavn))
        .catch(() => {});
    }

    dbNode.addEventListener("click", () => {
      // Velg databasen (uten å bygge om hele treet)
      if (db.value !== dbNavn) {
        db.value = dbNavn;
        faneDb[aktivFaneId] = dbNavn; // husk valgt database for denne fanen
        oppdaterSchema(dbNavn);
        document.querySelectorAll("#tre .tre-node.tre-valgt")
          .forEach((n) => n.classList.remove("tre-valgt"));
        dbNode.classList.add("tre-valgt");
      }
      // Toggle åpen/lukket
      if (dbBarn.style.display === "none") {
        dbBarn.style.display = "";
        ik.textContent = "▾";
        if (!lastet) {
          lastet = true;
          hentJson(tablelistUrl)
            .then((rader) => {
              dbBarn.innerHTML = "";
              byggTabellGrener(dbBarn, rader, dbNavn);
            })
            .catch(() => {});
        }
      } else {
        dbBarn.style.display = "none";
        ik.textContent = "▸";
      }
    });
  }

  // Bygger schema → (tabeller | views)-nivåene i treet
  function byggTabellGrener(container, rader, dbNavn) {
    if (!Array.isArray(rader) || rader.length === 0) return;

    // Grupper på skjema (rad[0]) — PostgreSQL/MySQL har skjema, SQLite ikke
    const grupper = new Map(); // skjema → {tabeller: [], views: []}
    rader.forEach((rad) => {
      const skjema = rad[0] || dbNavn;
      const navn = rad[1] == null ? "" : String(rad[1]);
      const type = (rad[2] || "").toLowerCase();
      if (!grupper.has(skjema)) grupper.set(skjema, { tabeller: [], views: [] });
      const g = grupper.get(skjema);
      // Tomme skjema-rader (CREATE SCHEMA uten tabeller ennå): beholder
      // skjema-noden, men legger ikke til noen tom tabell
      if (!navn) return;
      if (type.includes("view")) g.views.push(navn);
      else g.tabeller.push(navn);
    });

    grupper.forEach((g, skjema) => {
      const erEneste = grupper.size === 1;
      // PostgreSQL: vis ALLTID skjema-nivået (public, andre skjemaer osv.)
      // mellom database og tabeller/views — som pgAdmin4
      const visSkjemaNivaa = rdbms === "postgres" || !erEneste;
      if (!visSkjemaNivaa) {
        // Kun ett skjema (f.eks. MySQL/SQLite) → vis tabeller/views direkte
        byggTabellViewGrener(container, g);
      } else {
        const schemaNode = lagNode({
          tekst: skjema,
          ikon: "📂",
          gren: true,
          åpen: true,
        });
        container.appendChild(schemaNode);
        const schemaBarn = document.createElement("div");
        schemaBarn.className = "tre-barn";
        container.appendChild(schemaBarn);
        byggTabellViewGrener(schemaBarn, g);
      }
    });
  }

  function byggTabellViewGrener(container, g) {
    if (g.tabeller.length > 0) {
      const tNode = lagNode({ tekst: `Tabeller (${g.tabeller.length})`, ikon: "📋", gren: true, åpen: true });
      container.appendChild(tNode);
      const tBarn = document.createElement("div");
      tBarn.className = "tre-barn";
      container.appendChild(tBarn);
      g.tabeller.forEach((navn) => {
        tBarn.appendChild(lagTabellNode(navn, "📄"));
      });
    }
    if (g.views.length > 0) {
      const vNode = lagNode({ tekst: `Views (${g.views.length})`, ikon: "👁️", gren: true, åpen: true });
      container.appendChild(vNode);
      const vBarn = document.createElement("div");
      vBarn.className = "tre-barn";
      container.appendChild(vBarn);
      g.views.forEach((navn) => {
        vBarn.appendChild(lagTabellNode(navn, "👁️"));
      });
    }
  }

  // Tabell/view-node som kan utvides: klikk viser feltnavn + datatype.
  // Klikk igjen setter inn navnet i editoren? Nei — først klikk utvider,
  // dobbeltklikk setter inn navnet i editoren.
  // Returnerer en wrapper der tabellnavnet står øverst og kolonnene
  // kommer UNDER det, med innrykk (søsken, ikke barn av noden).
  function lagTabellNode(navn, ikon) {
    const wrapper = document.createElement("div");

    const div = document.createElement("div");
    div.className = "tre-node tre-tabell";
    const ik = document.createElement("span");
    ik.className = "tre-ikon";
    ik.textContent = "▸";
    const lab = document.createElement("span");
    lab.textContent = navn;
    div.appendChild(ik);
    div.appendChild(lab);
    wrapper.appendChild(div);

    const barn = document.createElement("div");
    barn.className = "tre-barn";
    barn.style.display = "none";
    wrapper.appendChild(barn);

    let lastet = false;
    div.addEventListener("click", () => {
      if (barn.style.display === "none") {
        barn.style.display = "";
        ik.textContent = "▾";
        if (!lastet) {
          lastet = true;
          hentKolonner(navn).then((kolonner) => {
            barn.innerHTML = "";
            kolonner.forEach(([felt, type]) => {
              const feltNode = document.createElement("div");
              feltNode.className = "tre-node tre-kolonne";
              const fik = document.createElement("span");
              fik.className = "tre-ikon";
              fik.textContent = "•";
              const flab = document.createElement("span");
              flab.textContent = felt + "  (" + type + ")";
              feltNode.appendChild(fik);
              feltNode.appendChild(flab);
              // Dobbeltklikk på feltet → sett inn i SQL-editoren på markøren
              feltNode.addEventListener("dblclick", (e) => {
                e.stopPropagation();
                settInnIEditor(felt);
              });
              barn.appendChild(feltNode);
            });
          }).catch(() => {
            barn.textContent = "Kunne ikke hente kolonner";
          });
        }
      } else {
        barn.style.display = "none";
        ik.textContent = "▸";
      }
    });
    div.addEventListener("dblclick", (e) => {
      e.stopPropagation();
      settInnIEditor(navn);
    });
    return wrapper;
  }

  // Henter [navn, type]-par for en tabell i den valgte databasen
  function hentKolonner(tabell) {
    const url = `/rest/get/columns/${rdbms}/${encodeURIComponent(db.value || "")}`;
    return fetch(url).then((r) => {
      if (!r.ok) throw new Error("HTTP " + r.status);
      return r.json();
    }).then((data) => {
      const k = (data || {})[tabell];
      return Array.isArray(k) ? k : [];
    });
  }

  // Piltast-navigasjon i resultat-tabellen: pil opp/ned flytter en markør
  // mellom radene (datatables.net-stil). Enter velger raden.
  function settOppPiltastNavigasjon() {
    const tabell = document.querySelector("#resultTable");
    if (!tabell) return;
    let aktiv = -1;

    tabell.addEventListener("keydown", (e) => {
      const rader = [...tabell.querySelectorAll("tbody tr")];
      if (rader.length === 0) return;
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        rader.forEach((r) => r.classList.remove("navigert"));
        if (e.key === "ArrowDown") {
          aktiv = Math.min(aktiv + 1, rader.length - 1);
        } else {
          aktiv = Math.max(aktiv - 1, 0);
        }
        rader[aktiv].classList.add("navigert");
        rader[aktiv].scrollIntoView({ block: "nearest" });
      } else if (e.key === "Enter" && aktiv >= 0) {
        e.preventDefault();
        rader[aktiv].click();
      }
    });
    tabell.tabIndex = 0;
  }

  // Setter inn navnet (tabell/felt) i editoren på markørens posisjon.
  // Hvis navnet består av flere ord eller inneholder punktum, omsluttes
  // det med doble anførselstegn ("…") slik at SQL-en blir gyldig.
  function settInnIEditor(navn) {
    const inn = /[\s.]/.test(navn) ? '"' + navn + '"' : navn;
    const cursor = editor.state.selection.main.head;
    editor.dispatch({
      changes: { from: cursor, insert: inn },
      selection: { anchor: cursor + inn.length },
    });
    editor.focus();
  }

  // Lager én node i treet (XSS-sikkert via textContent)
  function lagNode({ tekst, ikon, gren, tabell, åpen, onClick }) {
    const div = document.createElement("div");
    div.className = "tre-node" + (gren ? " tre-gren" : "") + (tabell ? " tre-tabell" : "");
    const ik = document.createElement("span");
    ik.className = "tre-ikon";
    ik.textContent = ikon || (gren ? (åpen ? "▾" : "▸") : "");
    const lab = document.createElement("span");
    lab.textContent = tekst;
    div.appendChild(ik);
    div.appendChild(lab);
    if (gren && onClick) {
      div.addEventListener("click", () => {
        // toggle åpen/lukket hvis noden har barn
        const barn = div.nextElementSibling;
        if (barn && barn.classList.contains("tre-barn")) {
          barn.style.display = barn.style.display === "none" ? "" : "none";
          ik.textContent = barn.style.display === "none" ? "▸" : "▾";
        }
        onClick();
      });
    } else if (onClick) {
      div.addEventListener("click", onClick);
    }
    return div;
  }

  function hentJson(url) {
    return fetch(url).then((r) => {
      if (!r.ok) throw new Error("HTTP " + r.status);
      return r.json();
    });
  }

  // ================= Slutt trestruktur =================

  // Hent kolonner per tabell for valgt database → oppdater intellisense-schema
  function oppdaterSchema(database) {
    if (!database || database === "Velg Database") return;
    const url = `/rest/get/columns/${rdbms}/${encodeURIComponent(database)}`;
    fetch(url)
      .then((response) => {
        if (!response.ok) throw new Error("Kunne ikke hente kolonner");
        return response.json();
      })
      .then((data) => {
        // data = {tabell: [[navn, type], ...], ...} — intellisense bruker bare navnene
        schema = {};
        Object.entries(data || {}).forEach(([tabell, kolonner]) => {
          schema[tabell] = (Array.isArray(kolonner) ? kolonner : []).map((k) =>
            Array.isArray(k) ? k[0] : k
          );
        });
        editor.dispatch({
          effects: sqlCompartment.reconfigure(
            sqlLang({ dialect, schema, upperCaseKeywords: true })
          ),
        });
      })
      .catch((err) => {
        console.warn("Intellisense-schema ikke tilgjengelig:", err.message);
      });
  }

  // Bygger database-nedtrekksmenyen (nå i toppmenyen)
  // Keymap: ctrl+enter = kjør, shift+enter = formater (høy prioritet,
  // slik at den overstyrer CodeMirrors egne Enter-bindinger)
  const ekstraFont = (delta) => {
    const scroller = editorContainer.querySelector(".cm-scroller");
    if (!scroller) return true;
    const gjeldende = parseFloat(getComputedStyle(scroller).fontSize) || 16;
    const ny = Math.min(Math.max(gjeldende + delta, 10), 28);
    scroller.style.fontSize = ny + "px";
    editorContainer.style.setProperty("--editor-font", ny + "px");
    localStorage.setItem("editorFont", String(ny));
    editor.requestMeasure?.();
    return true;
  };

  // Justerer HØYDEN på SQL-feltet (Ctrl+Alt+PilOpp/Ned) — i tillegg til
  // drag-splitteren. Endringen huskes i localStorage.
  const justerEditorHoyde = (delta) => {
    const hoyre = editorContainer.closest(".select-hoyre");
    if (!hoyre) return true;
    const rect = hoyre.getBoundingClientRect();
    const gjeldende = editorContainer.offsetHeight;
    const ny = Math.min(
      // Minimum = én linje kode (~40px), slik at resultat-tabellen kan få
      // nesten all høyden
      Math.max(gjeldende + delta, 40),
      rect.height - 40
    );
    editorContainer.style.height = ny + "px";
    editor.requestMeasure?.();
    return true;
  };

  const extraKeymap = Prec.high(keymap.of([
    { key: "Ctrl-Enter", run: kjørSQL },
    { key: "Shift-Enter", run: formaterSQL },
    { key: "Ctrl-Shift-n", run: nyFane },
    { key: "Ctrl-Shift-x", run: lukkAktivFane },
    { key: "Ctrl-Shift-ArrowUp", run: () => ekstraFont(2) },
    { key: "Ctrl-Shift-ArrowDown", run: () => ekstraFont(-2) },
    { key: "Ctrl-Alt-ArrowUp", run: () => justerEditorHoyde(40) },
    { key: "Ctrl-Alt-ArrowDown", run: () => justerEditorHoyde(-40) },
  ]));

  // Resultat-feltets fontstørrelse: Alt+Shift+PilOpp (større) / PilNed (mindre).
  // (Ctrl+Shift+PgUp/PgDn kræsjet med Firefox sine interne kommandoer)
  // Viktig: capture=true (tredje arg) slik at denne kjører FØR CodeMirror
  // fanger Shift-Alt-pilene (som kopierer linjer i editoren) — og vi stopper
  // spredningen så CodeMirror aldri ser tastetrykket.
  const justerResultatFont = (delta) => {
    const gjeldende = parseFloat(
      getComputedStyle(resultatInnhold).getPropertyValue("--resultat-font") || "0.85"
    ) || 0.85;
    const ny = Math.min(Math.max(gjeldende + delta, 0.6), 1.4);
    resultatInnhold.style.setProperty("--resultat-font", ny + "rem");
    localStorage.setItem("resultatFont", String(ny));
    return true;
  };

  document.addEventListener("keydown", (e) => {
    if (e.altKey && e.shiftKey && (e.key === "ArrowUp" || e.key === "ArrowDown")) {
      e.preventDefault();
      e.stopPropagation(); // CodeMirror skal ikke kopiere linjen
      justerResultatFont(e.key === "ArrowUp" ? 0.05 : -0.05);
    } else if (e.altKey && e.shiftKey && (e.key === "ArrowRight" || e.key === "ArrowLeft")) {
      e.preventDefault();
      e.stopPropagation(); // CodeMirror skal ikke markere ord
      e.key === "ArrowRight" ? nesteFane() : forrigeFane();
    }
  }, true);

  const editor = new EditorView({
    state: EditorState.create({
      doc: query.value || "",
      extensions: [
        grunnOppsett,
        sqlCompartment.of(sqlLang({ dialect, schema, upperCaseKeywords: true })),
        temaCompartment.of(MØRK_TEMA),
        extraKeymap,
        EditorView.lineWrapping,
      ],
    }),
    parent: editorContainer,
  });

  // Eksponer editoren globalt (debugging + tester)
  window.dbDemoEditor = editor;
  window.dbDemoKjørSQL = kjørSQL;

  // Markør skal alltid starte på første tegn, første linje (posisjon 0)
  editor.dispatch({
    selection: { anchor: 0, head: 0 },
    scrollIntoView: true,
  });
  editor.focus();

  // Bytt tema (mørk/lys) — gjelder HELE appen via [data-tema] på html/body
  const settTema = (verdi) => {
    const mørk = verdi !== "light";
    document.documentElement.dataset.tema = mørk ? "mork" : "lys";
    document.body.dataset.tema = mørk ? "mork" : "lys";
    editor.dispatch({
      effects: temaCompartment.reconfigure(mørk ? MØRK_TEMA : LYS_TEMA),
    });
  };

  // Dra-splitter: justerer høyden mellom kode-feltet og resultatpanelet.
  // Dra oppover → kode mindre, resultat større (og omvendt).
  function settOppDragSplitter() {
    if (!editorSplitter) return;
    let dragging = false;

    editorSplitter.addEventListener("mousedown", (e) => {
      dragging = true;
      document.body.style.cursor = "row-resize";
      document.body.style.userSelect = "none";
      e.preventDefault();
    });

    window.addEventListener("mousemove", (e) => {
      if (!dragging) return;
      const hoyre = editorContainer.closest(".select-hoyre");
      if (!hoyre) return;
      const rect = hoyre.getBoundingClientRect();
      // Editorhøyde = avstand fra toppen av kolonnen til musen (minus splitter)
      const nyHoyde = Math.min(
        Math.max(e.clientY - rect.top - 8, 120),
        rect.height - 120
      );
      editorContainer.style.height = nyHoyde + "px";
      editor.requestMeasure?.();
    });

    window.addEventListener("mouseup", () => {
      if (!dragging) return;
      dragging = false;
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    });
  }

  // Dra-splitter: justerer BREDDEN på venstrespalten.
  // Dra høyre → bredere (lange feltnavn får plass), dra venstre → smalere.
  function settOppTreBreddeSplitter() {
    const splitter = document.getElementById("treSplitter");
    const trePanel = document.getElementById("trePanel");
    if (!splitter || !trePanel) return;
    let dragging = false;

    splitter.addEventListener("mousedown", (e) => {
      dragging = true;
      document.body.style.cursor = "col-resize";
      document.body.style.userSelect = "none";
      e.preventDefault();
    });

    window.addEventListener("mousemove", (e) => {
      if (!dragging) return;
      const layout = trePanel.closest(".select-layout");
      if (!layout) return;
      const rect = layout.getBoundingClientRect();
      // Bredde = avstand fra venstre kant av layout til musen
      const nyBredde = Math.min(
        Math.max(e.clientX - rect.left - 10, 160),
        rect.width - 400
      );
      trePanel.style.width = nyBredde + "px";
    });

    window.addEventListener("mouseup", () => {
      if (!dragging) return;
      dragging = false;
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    });
  }

  // Bytte databasesystem fra venstrespalten — gjelder KUN den aktive fanen.
  // Ingen side-last: de andre fanene beholder sitt system, sin database og
  // sine SQL-setninger uberørt.
  function byttSystem() {
    const nytt = systemSelect.value;
    if (!nytt || nytt === rdbms) return;
    // Terminal-fanen har ikke noe databasesystem — avvis bytte og sett
    // nedtrekksmenyen tilbake til forrige verdi
    if (faneTyper[aktivFaneId] === "terminal") {
      systemSelect.value = rdbms;
      return;
    }
    // Lagre innholdet i den aktive fanen før vi bytter system
    faneDokumenter[aktivFaneId] = editor.state.doc.toString();
    rdbms = nytt;
    dialect = DIALECT[rdbms] || PostgreSQL;
    faneRdbms[aktivFaneId] = rdbms;
    faneDb[aktivFaneId] = "";
    db.value = "";
    schema = {};
    oppdaterEditorSprak();
    byggTre();
    renderFaner();
    editor.focus();
  }

  // Rekonfigurerer CodeMirror-språket til det aktive systemet (dialekt +
  // autocomplete-schema). Kall etter system- eller schema-endring.
  function oppdaterEditorSprak() {
    editor.dispatch({
      effects: sqlCompartment.reconfigure(
        sqlLang({ dialect, schema, upperCaseKeywords: true })
      ),
    });
  }

  // ===== Faner: flere SQL-faner — hver med EGEN database + databasesystem =====
  // Hver fane har eget innhold (doc), eget system (faneRdbms) og egen valgt
  // database (faneDb), lagret i minnet. Å bytte system i nedtrekksmenyen
  // påvirker kun den aktive fanen; de andre beholder sin tilkobling til sin
  // RDBMS helt til fanen lukkes.
  let faneIdTeller = 1;
  let aktivFaneId = 1;
  const faneDokumenter = { 1: query.value || "" };
  const faneNavn = { 1: "Fane 1" };
  const faneRdbms = { 1: rdbms };
  const faneDb = { 1: db.value || "" };
  // Fanetype: "sql" (SQL-editor) eller "terminal" (xterm). Terminal-fanen
  // har ingen SQL-doc — den viser terminalpanelet i stedet for editoren.
  const faneTyper = { 1: "sql" };

  // Bytter editorens innhold til en fanes doc (bevarer fane-referanser)
  function byttFaneDoc(doc) {
    editor.dispatch({
      changes: { from: 0, to: editor.state.doc.length, insert: doc },
      selection: { anchor: 0, head: 0 },
      scrollIntoView: true,
    });
    editor.requestMeasure?.();
  }

  // Tegner fanelinjen på nytt
  function renderFaner() {
    if (!faneListe) return;
    faneListe.innerHTML = "";
    Object.keys(faneDokumenter).forEach((idStr) => {
      const id = Number(idStr);
      const fane = document.createElement("div");
      fane.className = "fane" + (id === aktivFaneId ? " fane-aktiv" : "");
      fane.title = "Klikk for å aktivere";
      const navn = document.createElement("span");
      if (faneTyper[id] === "terminal") {
        // Terminal-fane: eget ikon + navn
        const ik = document.createElement("i");
        ik.className = "bi bi-terminal-fill";
        ik.style.marginRight = "4px";
        fane.appendChild(ik);
        navn.textContent = faneNavn[id] || "Terminal";
      } else {
        // SQL-fane: vis systemet i fanenavnet, f.eks. «Fane 2 · PostgreSQL» —
        // slik at man ser hvilken RDBMS hver fane er knyttet til
        const sysNavn = SYSTEMNAVN[faneRdbms[id]] || "";
        navn.textContent = (faneNavn[id] || "Fane " + id) + (sysNavn ? " · " + sysNavn : "");
      }
      fane.appendChild(navn);
      const lukk = document.createElement("span");
      lukk.className = "fane-lukk";
      lukk.textContent = "×";
      lukk.title = "Lukk fane (Ctrl+Shift+X)";
      lukk.addEventListener("click", (e) => {
        e.stopPropagation();
        lukkFane(id);
      });
      fane.appendChild(lukk);
      fane.addEventListener("click", () => aktiverFane(id));
      faneListe.appendChild(fane);
    });
    // «+»-knapp for ny fane
    const ny = document.createElement("div");
    ny.className = "fane-ny";
    ny.textContent = "+";
    ny.title = "Ny fane (Ctrl+Shift+N)";
    ny.addEventListener("click", nyFane);
    faneListe.appendChild(ny);
  }

  // Oppretter en ny fane (tom) og aktiverer den — arver aktiv fanes system
  function nyFane() {
    // Lagre innholdet i den aktive fanen
    faneDokumenter[aktivFaneId] = editor.state.doc.toString();
    faneIdTeller++;
    const id = faneIdTeller;
    faneDokumenter[id] = "";
    faneNavn[id] = "Fane " + id;
    faneRdbms[id] = rdbms; // ny fane starter i samme system som den aktive
    faneDb[id] = "";
    faneTyper[id] = "sql";
    aktivFaneId = id;
    db.value = "";
    byttFaneDoc("");
    renderFaner();
    editor.focus();
    return true;
  }

  // Lukker en fane; hvis det er den aktive, aktiveres nabofanen
  function lukkFane(id) {
    const antall = Object.keys(faneDokumenter).length;
    if (antall <= 1) return true; // behold alltid minst én fane
    const idListe = Object.keys(faneDokumenter).map(Number);
    const idx = idListe.indexOf(id);
    if (idx === -1) return true;
    const erTerminal = faneTyper[id] === "terminal";
    delete faneDokumenter[id];
    delete faneNavn[id];
    delete faneRdbms[id];
    delete faneDb[id];
    delete faneTyper[id];
    if (erTerminal) {
      // Terminal-fanen lukkes → rydd xterm + WebSocket
      lukkTerminal();
      terminalFaneId = null;
    }
    if (id === aktivFaneId) {
      const nabo = idListe[Math.max(idx - 1, 0)];
      aktivFaneId = nabo;
      if (faneTyper[nabo] === "terminal") {
        visTerminalModus();
      } else {
        byttFaneDoc(faneDokumenter[nabo] || "");
        gjenopprettFaneKontekst(nabo);
        visEditorModus();
      }
    }
    renderFaner();
    editor.focus();
    return true;
  }

  // Lukker den aktive fanen (tastatursnarvei)
  function lukkAktivFane() {
    return lukkFane(aktivFaneId);
  }

  // Gjenoppretter system + database for en fane (kalles ved fanebytte).
  // For terminal-fanen: vis terminalpanelet (systemet er irrelevant der).
  function gjenopprettFaneKontekst(id) {
    if (faneTyper[id] === "terminal") {
      visTerminalModus();
      return;
    }
    const fanensSystem = faneRdbms[id] || rdbms;
    if (fanensSystem !== rdbms) {
      rdbms = fanensSystem;
      dialect = DIALECT[rdbms] || PostgreSQL;
      if (systemSelect) systemSelect.value = rdbms;
      schema = {};
      oppdaterEditorSprak();
    }
    const fanensDb = faneDb[id] || "";
    db.value = fanensDb;
    byggTre();
    visEditorModus();
  }

  // Aktiverer en fane: lagrer gjeldende innhold, bytter til valgt fane
  // — og gjenoppretter fanens system + database (eller terminalmodus)
  function aktiverFane(id) {
    if (id === aktivFaneId) return;
    faneDokumenter[aktivFaneId] = editor.state.doc.toString();
    aktivFaneId = id;
    if (faneTyper[id] === "terminal") {
      // Terminal-fane: ingen SQL-doc å bytte, bare vis terminalen
      visTerminalModus();
      renderFaner();
      return;
    }
    byttFaneDoc(faneDokumenter[id] || "");
    gjenopprettFaneKontekst(id);
    renderFaner();
    editor.focus();
  }

  // Neste fane (Alt+Shift+PilHøyre) — syklisk
  function nesteFane() {
    const idListe = Object.keys(faneDokumenter).map(Number);
    if (idListe.length <= 1) return true;
    const idx = idListe.indexOf(aktivFaneId);
    aktiverFane(idListe[(idx + 1) % idListe.length]);
    return true;
  }

  // Forrige fane (Alt+Shift+PilVenstre) — syklisk
  function forrigeFane() {
    const idListe = Object.keys(faneDokumenter).map(Number);
    if (idListe.length <= 1) return true;
    const idx = idListe.indexOf(aktivFaneId);
    aktiverFane(idListe[(idx - 1 + idListe.length) % idListe.length]);
    return true;
  }

  // Eksponer fane-funksjoner for testing
  window.dbDemoFaner = {
    nyFane,
    lukkFane,
    lukkAktivFane,
    aktiverFane,
    nesteFane,
    forrigeFane,
    renderFaner,
    apneTerminal,
    lukkTerminal,
    getAntall: () => Object.keys(faneDokumenter).length,
    getAktiv: () => aktivFaneId,
    getDokumenter: () => ({ ...faneDokumenter }),
    getRdbms: () => ({ ...faneRdbms }),
    getDb: () => ({ ...faneDb }),
    getTyper: () => ({ ...faneTyper }),
    getAktivRdbms: () => rdbms,
    getTerminalFaneId: () => terminalFaneId,
  };

  const handleOnSkinSelectChange = function handleOnSkinChange() {
    localStorage.setItem("skin", skinSelect.value);
    settTema(skinSelect.value);
    editor.focus();
  };

  skinSelect.onchange = handleOnSkinSelectChange;
  if (systemSelect) systemSelect.onchange = byttSystem;
  if (window.settOppSprakVelger) settOppSprakVelger();
  settOppDragSplitter();
  settOppTreBreddeSplitter();
  byggTre();
  renderFaner(); // tegn den første fanen

  // ▶ Kjør-knapp (nettbrett/berøring uten Ctrl-tast) — kjører SQL-en
  const kjorKnapp = document.getElementById("kjorKnapp");
  if (kjorKnapp) {
    kjorKnapp.addEventListener("click", (ev) => {
      ev.preventDefault();
      kjørSQL();
      editor.focus();
    });
  }

  // ===== Terminal-emulator (xterm.js + WebSocket → bash med PTY) =====
  // Terminalen lever i EN EGEN FANE (faneTyper[id] === "terminal") — den
  // forstyrrer ikke SQL-fanene; man kan bytte mellom dem fritt. Terminalen
  // lukkes først når terminal-fanen lukkes (eller siden lastes på nytt).
  let terminalWs = null;
  let xtermInstans = null;
  let terminalFaneId = null; // fanen som inneholder terminalen (null = ingen)
  const terminalPanel = document.getElementById("terminalPanel");
  const terminalKnapp = document.getElementById("terminalKnapp");
  const terminalLukk = document.getElementById("terminalLukk");
  const terminalContainer = document.getElementById("terminalContainer");
  const formElement = document.getElementById("sql");

  // Viser terminalpanelet (skjuler editor + resultat) — kalles når
  // terminal-fanen aktiveres
  function visTerminalModus() {
    if (!terminalPanel) return;
    terminalPanel.style.display = "flex";
    terminalPanel.style.flexDirection = "column";
    terminalPanel.style.flex = "1";
    terminalPanel.style.minHeight = "0";
    if (formElement) formElement.style.display = "none";
    if (editorSplitter) editorSplitter.style.display = "none";
    if (resultatPanel) resultatPanel.style.display = "none";
    if (xtermInstans) xtermInstans.focus();
    // Etter at panelet er synlig igjen, oppdater PTY-størrelsen (xterm har
    // null-dimensjoner mens panelet var skjult)
    setTimeout(sendTerminalResize, 50);
  }

  // Viser editor + resultat (skjuler terminalen) — kalles når en SQL-fane
  // aktiveres. Terminalen (xterm + WS) lever videre i bakgrunnen.
  function visEditorModus() {
    if (terminalPanel) terminalPanel.style.display = "none";
    if (formElement) formElement.style.display = "";
    if (editorSplitter) editorSplitter.style.display = "";
    if (resultatPanel) resultatPanel.style.display = "";
    if (editor) editor.focus();
  }

  // Oppretter xterm.js + WebSocket — kun første gang terminalen åpnes
  function opprettTerminal() {
    if (!terminalPanel || !window.Terminal || xtermInstans) return;

    // xterm.js — mørkt tema som matcher appen
    xtermInstans = new Terminal({
      cursorBlink: true,
      fontSize: 13,
      fontFamily: 'Menlo, Consolas, "Courier New", monospace',
      theme: { background: "#1a1d23", foreground: "#e6e9ef" },
      convertEol: false,
    });
    xtermInstans.open(terminalContainer);
    xtermInstans.focus();

    // Ctrl+Shift+PilOpp/Ned = fontstørrelse (samme som i SQL-vinduet)
    xtermInstans.attachCustomKeyEventHandler((e) => {
      if (e.ctrlKey && e.shiftKey && (e.key === "ArrowUp" || e.key === "ArrowDown")) {
        const ny = xtermInstans.options.fontSize + (e.key === "ArrowUp" ? 2 : -2);
        if (ny >= 8 && ny <= 28) {
          xtermInstans.options.fontSize = ny;
        }
        return false; // ikke send til serveren
      }
      return true;
    });

    // WebSocket til serveren (samme origin — krever innlogging)
    const proto = location.protocol === "https:" ? "wss" : "ws";
    terminalWs = new WebSocket(`${proto}://${location.host}/ws/terminal`);
    terminalWs.onopen = () => {
      xtermInstans.writeln("\x1b[32mTerminal klar — lokal shell i containeren.\x1b[0m");
      xtermInstans.writeln("\x1b[33mKoble til f.eks.: ssh bruker@vert  |  psql -h vert -U bruker\x1b[0m");
      xtermInstans.writeln("");
      // Send terminalens faktiske størrelse → PTY-en blir like stor som
      // vinduet (ellers er den bare skrivbar i 80×24)
      sendTerminalResize();
    };
    terminalWs.onmessage = (ev) => xtermInstans.write(ev.data);
    terminalWs.onclose = () => {
      if (xtermInstans) xtermInstans.writeln("\r\n\x1b[31mTerminal-tilkoblingen ble lukket.\x1b[0m");
    };
    terminalWs.onerror = () => {
      if (xtermInstans) xtermInstans.writeln("\r\n\x1b[31mKunne ikke koble til terminal-serveren.\x1b[0m");
    };

    // Tastatur-input → WebSocket (inkl. escape-sekvenser for piler/tab)
    xtermInstans.onData((data) => {
      if (terminalWs && terminalWs.readyState === WebSocket.OPEN) {
        terminalWs.send(data);
      }
    });
    // Ved vindustørrelse-endring: oppdater PTY-en så hele vinduet er skrivbart
    xtermInstans.onResize(({ cols, rows }) => {
      if (terminalWs && terminalWs.readyState === WebSocket.OPEN) {
        terminalWs.send(JSON.stringify({ type: "resize", cols, rows }));
      }
    });
  }

  function sendTerminalResize() {
    if (!xtermInstans || !terminalWs || terminalWs.readyState !== WebSocket.OPEN) return;
    const dims = xtermInstans._core ? xtermInstans._core._renderService.dimensions : null;
    if (dims && dims.actualCellWidth > 0 && dims.actualCellHeight > 0) {
      const w = terminalContainer.clientWidth;
      const h = terminalContainer.clientHeight;
      const cols = Math.max(20, Math.floor(w / dims.actualCellWidth));
      const rows = Math.max(5, Math.floor(h / dims.actualCellHeight));
      terminalWs.send(JSON.stringify({ type: "resize", cols, rows }));
    }
  }

  // Lukker terminalen helt (kalles når terminal-fanen lukkes) — xterm +
  // WebSocket ryddes; neste åpning starter en fersk terminal
  function lukkTerminal() {
    if (terminalWs) {
      terminalWs.close();
      terminalWs = null;
    }
    if (xtermInstans) {
      xtermInstans.dispose();
      xtermInstans = null;
    }
    if (terminalContainer) terminalContainer.innerHTML = "";
    terminalFaneId = null;
    visEditorModus();
  }

  // Åpner terminalen i EN NY FANE — eksisterende SQL-faner forstyrres ikke.
  // Hvis terminal-fanen allerede finnes, aktiveres den i stedet.
  function apneTerminal() {
    if (!terminalPanel || !window.Terminal) return;
    if (terminalFaneId != null && faneDokumenter[terminalFaneId] !== undefined) {
      aktiverFane(terminalFaneId);
      return;
    }
    // Lagre innholdet i den aktive fanen
    faneDokumenter[aktivFaneId] = editor.state.doc.toString();
    faneIdTeller++;
    const id = faneIdTeller;
    faneDokumenter[id] = "";
    faneNavn[id] = "Terminal";
    faneRdbms[id] = rdbms; // arver aktivt system (kun for visning)
    faneDb[id] = "";
    faneTyper[id] = "terminal";
    terminalFaneId = id;
    aktivFaneId = id;
    opprettTerminal();
    renderFaner();
    visTerminalModus();
  }

  if (terminalKnapp) {
    terminalKnapp.addEventListener("click", (ev) => {
      ev.preventDefault();
      apneTerminal();
    });
  }
  if (terminalLukk) {
    terminalLukk.addEventListener("click", () => {
      // «✕ Lukk terminal» lukker terminal-fanen (og dermed terminalen)
      if (terminalFaneId != null) lukkFane(terminalFaneId);
      else lukkTerminal();
    });
  }
  // Last lagrede fontstørrelser (Ctrl+Shift+PilOpp/Ned og PgUp/PgDn)
  const lagretEditorFont = localStorage.getItem("editorFont");
  if (lagretEditorFont) {
    const scroller = editorContainer.querySelector(".cm-scroller");
    if (scroller) scroller.style.fontSize = lagretEditorFont + "px";
    editorContainer.style.setProperty("--editor-font", lagretEditorFont + "px");
  }
  const lagretResultatFont = localStorage.getItem("resultatFont");
  if (lagretResultatFont) {
    resultatInnhold.style.setProperty("--resultat-font", lagretResultatFont + "rem");
  }
  // Server-rendret feil (f.eks. «ikke konfigurert») vises i resultatpanelet;
  // tomt felt skjules (whitespace ignoreres)
  if (feilMelding.textContent.trim() !== "") {
    feilMelding.style.display = "block";
    resultatInnhold.style.display = "none";
    resultatStatus.textContent = "Feil";
  } else {
    feilMelding.style.display = "none";
    resultatInnhold.style.display = "";
  }
  skinSelect.value = localStorage.getItem("skin") ? localStorage.getItem("skin") : "dark";
  settTema(skinSelect.value);
  if (db.value) {
    // Forhåndsvelg db fra config (databasen markeres i trestrukturen)
    oppdaterSchema(db.value);
  }

  // ===== Lagre/åpne SQL-filer (per bruker, egen mappe på serveren) =====
  const lagreSqlKnapp = document.getElementById("lagreSqlKnapp");
  const apneSqlKnapp = document.getElementById("apneSqlKnapp");
  const sqlFilListe = document.getElementById("sqlFilListe");

  // Hent innholdet i editoren som tekst
  function hentEditorTekst() {
    return editor.state.doc.toString();
  }

  // Sett innholdet i editoren (bevarer fane-dokumentet)
  function settEditorTekst(tekst) {
    editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: tekst } });
  }

  // Last inn fil-listen i åpne-menyen
  function lastSqlFilListe() {
    fetch("/rest/sqlfil", { headers: { "X-CSRF-TOKEN": csrfToken } })
      .then((r) => r.json())
      .then((filer) => {
        sqlFilListe.innerHTML = "";
        if (!Array.isArray(filer) || filer.length === 0) {
          const li = document.createElement("li");
          li.className = "dropdown-item text-muted disabled";
          li.textContent = window.dbAppTekster ? window.dbAppTekster().ingenFiler : "Ingen lagrede filer";
          sqlFilListe.appendChild(li);
          return;
        }
        filer.forEach((navn) => {
          const li = document.createElement("li");
          const a = document.createElement("a");
          a.className = "dropdown-item d-flex justify-content-between align-items-center gap-2";
          a.href = "#";
          const span = document.createElement("span");
          span.textContent = "🗎 " + navn + ".sql";
          a.appendChild(span);
          // Slett-knapp per fil
          const slette = document.createElement("span");
          slette.className = "badge text-danger";
          slette.textContent = "🗑";
          slette.style.cursor = "pointer";
          slette.title = "Slett fil";
          slette.addEventListener("click", (ev) => {
            ev.preventDefault();
            ev.stopPropagation();
            if (!confirm("Slett «" + navn + ".sql»?")) return;
            fetch("/rest/sqlfil/" + encodeURIComponent(navn), {
              method: "DELETE",
              headers: { "X-CSRF-TOKEN": csrfToken },
            }).then(() => lastSqlFilListe());
          });
          a.appendChild(slette);
          a.addEventListener("click", (ev) => {
            ev.preventDefault();
            fetch("/rest/sqlfil/" + encodeURIComponent(navn), { headers: { "X-CSRF-TOKEN": csrfToken } })
              .then((r) => r.json())
              .then((data) => {
                if (data && data.innhold != null) {
                  settEditorTekst(data.innhold);
                  visStatusMelding("📂 Åpnet " + navn + ".sql", true);
                } else {
                  visStatusMelding("⚠️ Kunne ikke åpne " + navn + ".sql", false);
                }
              })
              .catch(() => visStatusMelding("⚠️ Kunne ikke åpne " + navn + ".sql", false));
          });
          li.appendChild(a);
          sqlFilListe.appendChild(li);
        });
      })
      .catch(() => {});
  }

  // Viser en tydelig suksess-/infomelding i statuslinjen (grønn, forsvinner selv)
  function visStatusMelding(tekst, suksess) {
    resultatStatus.textContent = tekst;
    resultatStatus.classList.remove("status-suksess", "status-info");
    resultatStatus.classList.add(suksess ? "status-suksess" : "status-info");
    clearTimeout(window.__statusTimer);
    window.__statusTimer = setTimeout(() => {
      resultatStatus.classList.remove("status-suksess", "status-info");
    }, 4000);
  }

  if (lagreSqlKnapp) {
    lagreSqlKnapp.addEventListener("click", () => {
      const innhold = hentEditorTekst();
      if (!innhold.trim()) {
        visStatusMelding("Ingenting å lagre — skriv SQL først", false);
        return;
      }
      const navn = prompt("Lagre SQL-fil som (uten .sql):", "sporring1");
      if (!navn) return;
      const rent = navn.trim().replace(/\.sql$/i, "");
      fetch("/rest/sqlfil", {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrfToken },
        body: JSON.stringify({ filnavn: rent, innhold }),
      })
        .then((r) => r.json())
        .then((data) => {
          if (data.ok) {
            visStatusMelding("✅ Lagret " + rent + ".sql", true);
            lastSqlFilListe();
          } else {
            visStatusMelding("⚠️ " + (data.feil || "Kunne ikke lagre"), false);
          }
        })
        .catch(() => visStatusMelding("⚠️ Kunne ikke lagre filen", false));
    });
  }
  if (apneSqlKnapp) {
    apneSqlKnapp.addEventListener("click", lastSqlFilListe);
  }

  // ===== Lagre resultat-tabellen som CSV-fil =====
  const lagreCsvKnapp = document.getElementById("lagreCsvKnapp");

  // CSV-escaping: sitér verdier med komma, anførselstegn eller linjeskift
  function csvCelle(verdi) {
    const s = String(verdi == null ? "" : verdi);
    if (/[",\n\r]/.test(s)) {
      return '"' + s.replace(/"/g, '""') + '"';
    }
    return s;
  }

  // Eksporterer gjeldende resultat-tabell til en CSV-fil (nedlasting)
  function eksporterCsv() {
    const tabell = document.getElementById("resultTable");
    if (!tabell) {
      resultatStatus.textContent = window.dbAppTekster ? window.dbAppTekster().ingenResultat : "Ingen resultat-tabell å lagre";
      return;
    }
    const rader = [];
    // Header: hopp over #-kolonnen (rad-nr-kol)
    const headerCeller = tabell.querySelectorAll("thead th");
    rader.push([...headerCeller]
      .filter((th) => !th.classList.contains("rad-nr-kol"))
      .map((th) => csvCelle(th.textContent))
      .join(";"));
    // Datarader: hopp over første celle (rad-nummer)
    tabell.querySelectorAll("tbody tr").forEach((tr) => {
      const celler = [...tr.querySelectorAll("td")]
        .filter((td) => !td.classList.contains("rad-nr-kol"))
        .map((td) => csvCelle(td.textContent));
      rader.push(celler.join(";"));
    });
    const csv = "\uFEFF" + rader.join("\r\n"); // BOM for Excel + CRLF

    const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = (rdbms || "resultat") + "-" + new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-") + ".csv";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    resultatStatus.textContent = (window.dbAppTekster ? window.dbAppTekster().csvLagret : "CSV lastet ned") +
      " (" + (rader.length - 1) + " rader)";
  }

  if (lagreCsvKnapp) {
    lagreCsvKnapp.addEventListener("click", eksporterCsv);
  }

  // Ctrl+S = lagre, Ctrl+O = åpne-meny (kun når editoren har fokus)
  editorContainer.addEventListener("keydown", (ev) => {
    if (ev.ctrlKey && ev.key === "s") {
      ev.preventDefault();
      lagreSqlKnapp?.click();
    } else if (ev.ctrlKey && ev.key === "o") {
      ev.preventDefault();
      lastSqlFilListe();
      apneSqlKnapp?.click();
    }
  });
}

document.addEventListener("DOMContentLoaded", handleOnDocumentLoaded);
