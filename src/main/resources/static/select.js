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
  let dbsti = "";

  const handleOnHentClick = function hentData() {
    const hasSelectedDB = selectDB.value !== "Velg Database";
    const hasQuery = queryText.innerHTML !== "";

    feilMelding.innerHTML = !hasSelectedDB
      ? "Velg en database å hente data fra ..."
      : !hasQuery
      ? "Skriv en SQL-setning å hente data med..."
      : "";

    if (feilMelding.innerHTML) return;

    const renQueryText = strip(queryText.innerHTML);
    query.value = renQueryText;
    sql.action = `/select/${rdbms_sti.value}`;
    sql.submit();
  };

  const handleOnQueryKeyUp = function handleOnQueryKeyUp(event) {
    const isEnterKey = event.key === "Enter";
    const isControlKey = event.ctrlKey;
    const isShiftKey = event.shiftKey;
    const isSpaceKey = event.code == "Space";

    // console.log(cursorPosition);

  /*  // Flytter også markøren til begynnelsen av linjen - derfor kommentert ut
    if(isSpaceKey) {
      hljs.highlightElement(queryText);
      const nedpil = new KeyboardEvent('keydown', { key : 40 } );
      queryText.dispatchEvent(nedpil);
      return;
    }
*/
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
    db.value = selectDB.value;
    queryText.focus();
    fetchTableList(selectDB.value);
  };

  function fetchTableList(dbsti) {
    if (!rdbms_sti.value || !dbsti) return;
    const url = `/rest/get/tablelist/${rdbms_sti.value}/${dbsti}`;
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
  byggDBListe();
  fetchTableList(db.value);
}

document.addEventListener("DOMContentLoaded", handleOnDocumentLoaded);
