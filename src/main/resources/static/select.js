function handleOnDocumentLoaded() {
  const feilMelding = document.getElementById("feilmelding");
  const hent = document.getElementById("hent");
  const selectDB = document.getElementById("selectDB");
  const queryText = document.getElementById("queryText");
  const query = document.getElementById("query");
  const sql = document.getElementById("sql");
  const rdbms_sti = document.getElementById("rdbms_sti");
  const db = document.getElementById("db");
  const tabellListe = document.getElementById("tabellListe");
  const skinCSS = document.getElementById('skin');
  const skinSelect = document.getElementById('skinSelect');
  const handleOnHentClick = function hentData() {
  const hasSelectedDB = selectDB.value !== "Velg Database";
  const hasQuery = queryText.innerHTML !== "";
  const hasFeilmelding = feilMelding.innerHTML !=="";

    feilMelding.innerHTML = !hasSelectedDB
      ? "Velg en database å hente data fra ..."
      : !hasQuery
      ? "Skriv en SQL-setning å hente data med..."
      : "";

    if (feilMelding.innerHTML) {
    feilMelding.style.visibility = "visible";
    return;
    } else {
        feilMelding.style.visibility = "hidden";
    }


    const renQueryText = strip(queryText.innerHTML);
    query.value = renQueryText;
    sql.action = `/select/${rdbms_sti.value}`;
    sql.submit();
  };

  const handleOnQueryKeyUp = function handleOnQueryKeyUp(event) {
    const isEnterKey = event.key === "Enter";
    const isControlKey = event.ctrlKey;
    const isShiftKey = event.shiftKey;


    if (!isEnterKey) return;

    if (isControlKey) {
      handleOnHentClick();
      return;
    }

    if (isShiftKey) {
      formaterOgHighlight();
      return;
    }

    event.preventDefault();
    settInnLinjeskift();
    return;
  };

  // Erstattning for deprecated document.execCommand("insertHTML")
  function settInnLinjeskift() {
    const sel = window.getSelection();
    if (!sel.rangeCount) return;
    const range = sel.getRangeAt(0);
    range.deleteContents();
    const br = document.createElement("br");
    range.insertNode(br);
    // Plasser markøren etter <br>
    const nyRange = document.createRange();
    nyRange.setStartAfter(br);
    nyRange.collapse(true);
    sel.removeAllRanges();
    sel.addRange(nyRange);
    // Re-highlight
    hljs.highlightElement(queryText);
  }

  // Formaterer SQL med sql-formatter (hvis tilgjengelig) og re-highlighter
  function formaterOgHighlight() {
    const ren = strip(queryText.innerHTML).trim();
    if (ren && window.sqlFormatter) {
      const språk = { postgres: "postgresql", microsoft: "tsql", oracle: "plsql", mysql: "sql" }[rdbms_sti.value] || "sql";
      const format = (sql, lang) => sqlFormatter.format(sql, { language: lang });
      const valg = språk === "plsql" || språk === "tsql" ? format(ren, språk) : format(ren, språk);
      queryText.textContent = valg;
    }
    hljs.highlightElement(queryText);
    queryText.focus();
  }

  const handleOnSelectDBChange = function handleOnDBChange() {
    feilMelding.innerHTML = "";
    feilMelding.style.visibility = "hidden";
    db.value = selectDB.value;
    queryText.focus();
    fetchTableList(selectDB.value);
  };

  const handleOnSkinSelectChange = function handleOnSkinChange() {
     skinCSS.href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/styles/"+skinSelect.value+".min.css";
     localStorage.setItem('skin', skinSelect.value);
     queryText.focus();
  }

  function fetchTableList(database) {
    if (!db.value && selectDB.value == "Velg Database" ) return;
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

  // Bygger tabell-listen XSS-sikkert: all tekst settes via textContent
  function byggTabellListe(rader) {
    tabellListe.innerHTML = "";
    if (!Array.isArray(rader) || rader.length === 0) {
      tabellListe.textContent = "Ingen tabeller funnet";
      return;
    }
    const table = document.createElement("table");
    table.id = "tabeller";
    table.className = "table table-sm table-striped";
    const thead = document.createElement("thead");
    const headRow = document.createElement("tr");
    ["Skjema", "Navn", "Type"].forEach((h) => {
      const th = document.createElement("th");
      th.textContent = h;
      headRow.appendChild(th);
    });
    thead.appendChild(headRow);
    table.appendChild(thead);

    const tbody = document.createElement("tbody");
    rader.forEach((rad) => {
      const tr = document.createElement("tr");
      rad.forEach((celle) => {
        const td = document.createElement("td");
        td.textContent = celle == null ? "" : String(celle);
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    tabellListe.appendChild(table);
  }

  function strip(html) {
    const doc = new DOMParser().parseFromString(html, "text/html");
    return doc.body.textContent || "";
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

  hent.onclick = handleOnHentClick;
  queryText.onkeyup = handleOnQueryKeyUp;
  selectDB.onchange = handleOnSelectDBChange;
  skinSelect.onchange = handleOnSkinSelectChange;
  byggDBListe();
  fetchTableList(db.value);
  feilMelding.innerHTML? feilMelding.style.visibility = "visible" : feilMelding.style.visibility = "hidden";
  skinSelect.value = localStorage.getItem('skin')? localStorage.getItem('skin') : "agate";
  skinCSS.href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/styles/"+skinSelect.value+".min.css";


}

document.addEventListener("DOMContentLoaded", handleOnDocumentLoaded);
