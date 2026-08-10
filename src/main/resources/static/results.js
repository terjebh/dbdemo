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
  document.location.href = `/select/${rdbms_sti.value}${db ? "?db=" + encodeURIComponent(db) : ""}`;
}
