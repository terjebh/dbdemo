
document.getElementById("hent").addEventListener("click",() -> {
if (document.getElementById("query").value=="") {
   alert("Ingen SQL å hente data fra...");
} else {
  document.getElementById("sql").submit();
}
});

document.getElementById("query").addEventListener("keyup", () -> {
if (event.keyCode === 13) {
if (document.getElementById("query").value=="") {
   alert("Ingen SQL å hente data fra...");
} else {
  document.getElementById("sql").submit();
}
}
})