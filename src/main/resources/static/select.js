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
      hljs.highlightElement(queryText);
      return;
    }

    event.preventDefault();
    document.execCommand("insertHTML", false, " \n");
    return;
  };

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
    const url = `/rest/get/tablelist/${rdbms_sti.value}/${database}`;
    const tilTekst = (response) => response.text();
    const lagListe = (liste) => (tabellListe.innerHTML = liste);
    fetch(url).then(tilTekst).then(lagListe);
  }

  function strip(html) {
    const doc = new DOMParser().parseFromString(html, "text/html");
    return doc.body.textContent || "";
  }

  function byggDBListe() {
    const url = `/rest/get/dblist/${rdbms_sti.value}`;
    const tilJSON = (response) => response.json();

    const fyllSelect = (liste) => {
      liste.forEach((item) => {
        const option = document.createElement("option");
        option.innerText = item;
        option.value = item;
        option.selected = item == db.value;
        selectDB.appendChild(option);
      });
    };

    fetch(url).then(tilJSON).then(fyllSelect);
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
