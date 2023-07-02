document.getElementById("hent").addEventListener("click",function ()  {
if (document.getElementById("query").value=="") {
   alert("Ingen SQL å hente data fra...");
} else {
  document.getElementById("sql").submit();
}
});

document.getElementById("query").addEventListener("keyup", function (event) {
if (event.ctrlKey && event.keyCode == 13) {
if (document.getElementById("query").value=="") {
   alert("Ingen SQL å hente data fra...");
} else {
  document.getElementById("sql").submit();
}
}
});

document.getElementById("selectDB").addEventListener("change", function () {
  document.getElementById("DBHead").innerHTML="DB: "+document.getElementById("selectDB").value;
  document.getElementById("db").value = document.getElementById("selectDB").value;
  document.getElementById("query").focus();
});
