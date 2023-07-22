hljs.highlightElement(queryText);

document.getElementById("hent").addEventListener("click",function ()  {
if (document.getElementById("selectDB").value=="Velg Database") {
   document.getElementById("feilmelding").innerHTML="Velg en database å hente data fra ...";
} else if (document.getElementById("queryText").innerHTML=="") {
   document.getElementById("feilmelding").innerHTML="Skriv en SQL-setning å hente data med...";
} else {
  let renQueryText = strip(document.getElementById("queryText").innerHTML)
  document.getElementById("query").value = renQueryText;
  document.getElementById("sql").submit();
}
});

document.getElementById("queryText").addEventListener("keyup", function (event) {

if (event.ctrlKey && event.keyCode == 13) {
if (document.getElementById("selectDB").value=="Velg Database") {
   document.getElementById("feilmelding").innerHTML="Velg en database å hente data fra ...";
} else if (document.getElementById("queryText").innerHTML=="") {
     document.getElementById("feilmelding").innerHTML="Skriv en SQL-setning å hente data med...";
} else {
  let renQueryText = strip(document.getElementById("queryText").innerHTML)
    document.getElementById("query").value = renQueryText;
  document.getElementById("sql").submit();
}
} else if(event.shiftKey && event.keyCode==13) {

hljs.highlightElement(queryText);

}
});

// Select-meny for velging av database og henting av databasens tabeller
document.getElementById("selectDB").addEventListener("change", function () {
  document.getElementById("feilmelding").innerHTML="";
  document.getElementById("db").value = document.getElementById("selectDB").value;
  document.getElementById("queryText").focus();

  fetch("/rest/get/tablelist/"+document.getElementById("selectDB").value)
  .then(tabeller => tabeller.text())
  .then(liste => document.getElementById("tabellListe").innerHTML=liste );

});

function strip(html){
   let doc = new DOMParser().parseFromString(html, 'text/html');
   return doc.body.textContent || "";
}