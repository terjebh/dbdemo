const editQueryForm = document.getElementById("editQueryForm");
const rdbms_sti = document.getElementById("rdbms_sti");
const dbHidden = document.getElementById("db");

function redigerSQL() {
  // Bevar valgt database ved redigering
  editQueryForm.submit();
}

function nySQL() {
  // Ny SQL: beholder valgt database (om noen), tomt query-felt
  const db = dbHidden ? dbHidden.value : "";
  if (rdbms_sti.value === "sqlite") {
    // SQLite-editoren ligger på /sqlite/{navn}, ikke /select/sqlite
    document.location.href = db ? `/sqlite/${encodeURIComponent(db)}` : "/sqlite";
  } else {
    document.location.href = `/select/${rdbms_sti.value}${db ? "?db=" + encodeURIComponent(db) : ""}`;
  }
}
