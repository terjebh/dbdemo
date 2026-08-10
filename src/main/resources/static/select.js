// dbdemo SQL-editor basert på CodeMirror 6
// - Live syntax-highlighting mens man skriver
// - Intellisense: SQL-nøkkelord + tabellnavn + kolonnenavn (per valgt database)
// - ctrl+enter kjører, shift+enter formaterer

import { EditorView, keymap, lineNumbers, highlightActiveLine, drawSelection, dropCursor, rectangularSelection, crosshairCursor } from "@codemirror/view";
import { EditorState, Compartment, EditorSelection, Prec } from "@codemirror/state";
import { history, defaultKeymap, historyKeymap, indentWithTab } from "@codemirror/commands";
import { syntaxHighlighting, defaultHighlightStyle, bracketMatching, indentOnInput, foldGutter, foldKeymap } from "@codemirror/language";
import { closeBrackets, autocompletion, closeBracketsKeymap, completionKeymap } from "@codemirror/autocomplete";
import { highlightSelectionMatches, searchKeymap } from "@codemirror/search";
import { sql as sqlLang, PostgreSQL, MySQL, MSSQL, PLSQL, SQLite } from "@codemirror/lang-sql";
import { oneDark, oneDarkHighlightStyle } from "@codemirror/theme-one-dark";

// Manuelt oppsett (tilsvarer basicSetup) — bygget med individuelle 6.x-pakker
// slik at ALLE deler samme instanser (unngår "multiple instances"-feil).
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
  indentOnInput(),
  bracketMatching(),
  closeBrackets(),
  autocompletion(),
  keymap.of([
    ...closeBracketsKeymap,
    ...defaultKeymap,
    ...searchKeymap,
    ...historyKeymap,
    ...foldKeymap,
    ...completionKeymap,
    indentWithTab,
  ]),
];

// Tema-kombinasjoner: oneDark-temaet må parres med sin egen highlight-stil,
// og lyst tema med defaultHighlightStyle — ellers blir ingen tokens farget.
const MØRK_TEMA = [
  oneDark,
  syntaxHighlighting(oneDarkHighlightStyle, { fallback: true }),
];
const LYS_TEMA = [
  syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
];

const DIALECT = {
  postgres: PostgreSQL,
  microsoft: MSSQL,
  oracle: PLSQL,
  mysql: MySQL,
  sqlite: SQLite,
};

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
  const selectDB = document.getElementById("selectDB");
  const query = document.getElementById("query");
  const sql = document.getElementById("sql");
  const rdbms_sti = document.getElementById("rdbms_sti");
  const db = document.getElementById("db");
  const tabellListe = document.getElementById("tabellListe");
  const skinSelect = document.getElementById("skinSelect");
  const editorContainer = document.getElementById("editorContainer");

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

  function kjørSQL() {
    query.value = editor.state.doc.toString();
    sql.action = `/select/${rdbms_sti.value}`;
    sql.submit();
    return true;
  }

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

  // Markør skal alltid starte på første tegn, første linje (posisjon 0)
  editor.dispatch({
    selection: { anchor: 0, head: 0 },
    scrollIntoView: true,
  });
  editor.focus();

  // Bytt tema (mørk/lys)
  const settTema = (verdi) => {
    const mørk = verdi !== "light";
    editor.dispatch({
      effects: temaCompartment.reconfigure(mørk ? MØRK_TEMA : LYS_TEMA),
    });
  };

  // Hent kolonner per tabell for valgt database → oppdater intellisense-schema
  function oppdaterSchema(database) {
    if (!database || database === "Velg Database") return;
    const url = `/rest/get/columns/${rdbms_sti.value}/${encodeURIComponent(database)}`;
    fetch(url)
      .then((response) => {
        if (!response.ok) throw new Error("Kunne ikke hente kolonner");
        return response.json();
      })
      .then((data) => {
        schema = data || {};
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

  const handleOnHentClick = function hentData() {
    const hasSelectedDB = selectDB.value !== "Velg Database";
    const hasQuery = editor.state.doc.toString().trim() !== "";

    feilMelding.innerHTML = !hasSelectedDB
      ? "Velg en database å hente data fra ..."
      : !hasQuery
      ? "Skriv en SQL-setning å hente data med..."
      : "";

    if (feilMelding.innerHTML) {
      feilMelding.style.visibility = "visible";
      return;
    }
    feilMelding.style.visibility = "hidden";
    kjørSQL();
  };

  const handleOnSelectDBChange = function handleOnDBChange() {
    feilMelding.innerHTML = "";
    feilMelding.style.visibility = "hidden";
    db.value = selectDB.value;
    oppdaterSchema(selectDB.value);
    fetchTableList(selectDB.value);
    editor.focus();
  };

  const handleOnSkinSelectChange = function handleOnSkinChange() {
    localStorage.setItem("skin", skinSelect.value);
    settTema(skinSelect.value);
    editor.focus();
  };

  function fetchTableList(database) {
    if (!db.value && selectDB.value == "Velg Database") return;
    const url = `/rest/get/tablelist/${rdbms_sti.value}/${encodeURIComponent(database)}`;
    fetch(url)
      .then((response) => {
        if (!response.ok) throw new Error("Kunne ikke hente tabelliste");
        return response.json();
      })
      .then(byggTabellListe)
      .catch((err) => {
        tabellListe.innerHTML = `<div class="alert alert-danger">${err.message}</div>`;
      });
  }

  // Bygger tabell-listen XSS-sikkert: all tekst settes via textContent.
  // Tabeller og views vises i to tabeller side om side.
  function byggTabellListe(rader) {
    tabellListe.innerHTML = "";
    if (!Array.isArray(rader) || rader.length === 0) {
      tabellListe.textContent = "Ingen tabeller funnet";
      return;
    }

    const tabeller = rader.filter((rad) => String(rad[2]).toUpperCase().includes("TABLE"));
    const views = rader.filter((rad) => String(rad[2]).toUpperCase().includes("VIEW"));

    const wrapper = document.createElement("div");
    wrapper.style.display = "flex";
    wrapper.style.gap = "1rem";
    wrapper.style.width = "100%";

    const lagTabell = (tittel, data) => {
      const kolonne = document.createElement("div");
      kolonne.style.flex = "1";
      kolonne.style.minWidth = "0";
      const heading = document.createElement("div");
      heading.className = "text-center fw-bold small mb-1";
      heading.textContent = tittel + " (" + data.length + ")";
      kolonne.appendChild(heading);
      const table = document.createElement("table");
      table.className = "table table-sm table-striped";
      const tbody = document.createElement("tbody");
      data.forEach((rad) => {
        const tr = document.createElement("tr");
        // Vis skjema.navn (kort form) — XSS-sikkert via textContent
        const td = document.createElement("td");
        td.textContent = rad[1] == null ? "" : String(rad[1]);
        tr.appendChild(td);
        tbody.appendChild(tr);
      });
      table.appendChild(tbody);
      kolonne.appendChild(table);
      return kolonne;
    };

    if (tabeller.length > 0) {
      wrapper.appendChild(lagTabell("Tabeller", tabeller));
    }
    if (views.length > 0) {
      wrapper.appendChild(lagTabell("Views", views));
    }
    if (tabeller.length === 0 && views.length === 0) {
      wrapper.textContent = "Ingen tabeller funnet";
    }
    tabellListe.appendChild(wrapper);
  }

  function byggDBListe() {
    const url = `/rest/get/dblist/${rdbms_sti.value}`;
    const tilJSON = (response) => {
      if (!response.ok) throw new Error("Kunne ikke hente databaseliste");
      return response.json();
    };

    const fyllSelect = (liste) => {
      if (!Array.isArray(liste)) {
        feilMelding.innerHTML = String(liste);
        feilMelding.style.visibility = "visible";
        return;
      }
      liste.forEach((item) => {
        const option = document.createElement("option");
        option.innerText = item;
        option.value = item;
        option.selected = item == db.value;
        selectDB.appendChild(option);
      });
    };

    fetch(url).then(tilJSON).then(fyllSelect).catch((err) => {
      feilMelding.innerHTML = err.message;
      feilMelding.style.visibility = "visible";
    });
  }

  selectDB.onchange = handleOnSelectDBChange;
  skinSelect.onchange = handleOnSkinSelectChange;
  byggDBListe();
  fetchTableList(db.value);
  feilMelding.innerHTML ? (feilMelding.style.visibility = "visible") : (feilMelding.style.visibility = "hidden");
  skinSelect.value = localStorage.getItem("skin") ? localStorage.getItem("skin") : "dark";
  settTema(skinSelect.value);
  if (db.value) {
    // Forhåndsvelg db fra config i nedtrekksmenyen
    selectDB.value = db.value;
    oppdaterSchema(db.value);
  }
}

document.addEventListener("DOMContentLoaded", handleOnDocumentLoaded);
