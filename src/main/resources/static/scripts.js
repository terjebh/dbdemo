document.getElementById("hent").addEventListener("click",function ()  {
if (document.getElementById("selectDB").value=="") {
   document.getElementById("feilmelding").innerHTML="Velg en database å hente data fra ...";
} else if (document.getElementById("query").value=="") {
   document.getElementById("feilmelding").innerHTML="Skriv en SQL-setning å hente data med...";
} else {
  document.getElementById("sql").submit();
}
});

document.getElementById("query").addEventListener("keyup", function (event) {
if (event.ctrlKey && event.keyCode == 13) {
if (document.getElementById("selectDB").value=="") {
   document.getElementById("feilmelding").innerHTML="Velg en database å hente data fra ...";
} else if (document.getElementById("query").value=="") {
     document.getElementById("feilmelding").innerHTML="Skriv en SQL-setning å hente data med...";
} else {
  document.getElementById("sql").submit();
}
}
});

document.getElementById("selectDB").addEventListener("change", function () {
  document.getElementById("DBHead").innerHTML="DB: "+document.getElementById("selectDB").value;
  document.getElementById("feilmelding").innerHTML="";
  document.getElementById("db").value = document.getElementById("selectDB").value;
  document.getElementById("query").focus();
});
