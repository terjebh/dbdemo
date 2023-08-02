const editQueryForm = document.getElementById("editQueryForm");
const rdbms_sti = document.getElementById("rdbms_sti");

function redigerSQL() {
  editQueryForm.submit();
}

function nySQL() {
  document.location.href = `/select/${rdbms_sti.value}`;
}
