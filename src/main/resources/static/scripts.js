
document.getElementById("hent").addEventListener("click",function() {
if (document.getElementById("query").value=="") {
   alert("Ingen SQL å hente data fra...");
} else {
  document.getElementById("sql").submit();
}
});