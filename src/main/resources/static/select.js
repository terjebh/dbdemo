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

  const rdbms = rdbms_sti.value;
  const dialect = DIALECT[rdbms] || PostgreSQL;

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
      visFeil("SQL-spørringen er tom");
      return true;
    }
    query.value = q;

    const url = rdbms === "sqlite"
      ? `/rest/kjor/sqlite`
      : `/rest/kjor/${rdbms}`;
    const body = JSON.stringify({ db: db.value, query: q });

    resultatStatus.textContent = "Kjører …";
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
          visFeil(data.feil || "Kunne ikke kjøre spørringen");
          return;
        }
        skjulFeil();
        visResultat(data.header || [], data.rows || []);
      })
      .catch((err) => {
        console.error("[kjørSQL] feil:", err);
        visFeil("Nettverksfeil: " + err.message);
      });
    return true;
  }

  // Viser resultatet i panelet under editoren (pgAdmin4-stil)
  let dataTable = null;
  function visResultat(header, rader) {
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
    table.className = "table table-striped table-bordered";

    const thead = document.createElement("thead");
    const trh = document.createElement("tr");
    header.forEach((h) => {
      const th = document.createElement("th");
      settTekst(th, h);
      trh.appendChild(th);
    });
    thead.appendChild(trh);
    table.appendChild(thead);

    const tbody = document.createElement("tbody");
    rader.forEach((rad) => {
      const tr = document.createElement("tr");
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
        order: false,
        lengthMenu: [[10, 25, 50, -1], [10, 25, 50, "Alle"]],
        pageLength: 10,
      });
    }
    resultatStatus.textContent = rader.length + " rader";
    // Resultat vises → feilmeldingen skjules
    feilMelding.style.display = "none";
    resultatInnhold.style.display = "";
  }

  function visFeil(melding) {
    // Feilmeldingen vises i resultatpanelet (der tabellen ellers ville stått)
    skjulResultat();
    feilMelding.innerHTML = "";
    const span = document.createElement("span");
    settTekst(span, melding);
    feilMelding.appendChild(span);
    feilMelding.appendChild(document.createElement("br"));
    const lenke = document.createElement("a");
    lenke.href = "/setup";
    settTekst(lenke, "Oppdater tilkoblingsinformasjonen →");
    feilMelding.appendChild(lenke);
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
      visFeil(melding);
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
      if (type.includes("view")) g.views.push(navn);
      else g.tabeller.push(navn);
    });

    grupper.forEach((g, skjema) => {
      const erEneste = grupper.size === 1;
      if (erEneste) {
        // Kun ett skjema → vis tabeller/views direkte
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
  function lagTabellNode(navn, ikon) {
    const div = document.createElement("div");
    div.className = "tre-node tre-tabell";
    const ik = document.createElement("span");
    ik.className = "tre-ikon";
    ik.textContent = "▸";
    const lab = document.createElement("span");
    lab.textContent = navn;
    div.appendChild(ik);
    div.appendChild(lab);

    const barn = document.createElement("div");
    barn.className = "tre-barn";
    barn.style.display = "none";

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
    div.appendChild(barn);
    div.addEventListener("dblclick", (e) => {
      e.stopPropagation();
      settInnIEditor(navn);
    });
    return div;
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

  // Setter inn tabellnavnet i editoren på markørens posisjon
  function settInnIEditor(navn) {
    const cursor = editor.state.selection.main.head;
    editor.dispatch({
      changes: { from: cursor, insert: navn },
      selection: { anchor: cursor + navn.length },
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
  const extraKeymap = Prec.high(keymap.of([
    { key: "Ctrl-Enter", run: kjørSQL },
    { key: "Shift-Enter", run: formaterSQL },
  ]));

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

  // Bytt tema (mørk/lys) — gjelder HELE appen via [data-tema] på body
  const settTema = (verdi) => {
    const mørk = verdi !== "light";
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

  // Bytte databasesystem fra venstrespalten
  function byttSystem() {
    const nytt = systemSelect.value;
    if (!nytt || nytt === rdbms) return;
    if (nytt === "sqlite") {
      window.location.href = "/sqlite";
    } else {
      window.location.href = `/select/${nytt}`;
    }
  }

  const handleOnSkinSelectChange = function handleOnSkinChange() {
    localStorage.setItem("skin", skinSelect.value);
    settTema(skinSelect.value);
    editor.focus();
  };

  skinSelect.onchange = handleOnSkinSelectChange;
  if (systemSelect) systemSelect.onchange = byttSystem;
  settOppDragSplitter();
  byggTre();
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
}

document.addEventListener("DOMContentLoaded", handleOnDocumentLoaded);
