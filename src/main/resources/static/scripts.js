document.getElementById("hent").addEventListener("click",function ()  {
if (document.getElementById("selectDB").value=="Velg Database") {
   document.getElementById("feilmelding").innerHTML="Velg en database å hente data fra ...";
} else if (document.getElementById("queryText").innerHTML=="") {
   document.getElementById("feilmelding").innerHTML="Skriv en SQL-setning å hente data med...";
} else {
  let renQueryText = strip(document.getElementById("queryText").innerHTML);
  document.getElementById("query").value = renQueryText;
  document.getElementById("sql").action = "/select/"+document.getElementById("rdbms_sti").value
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
 document.getElementById("sql").action = "/select/"+document.getElementById("rdbms_sti").value
    document.getElementById("query").value = renQueryText;
  document.getElementById("sql").submit();
}
} else if(event.shiftKey && event.keyCode==13) {

hljs.highlightElement(queryText);

} else if(event.keyCode==13) {
   document.execCommand('insertHTML', false, ' \n');
   event.preventDefault();
   return false;
}
});

// Select-meny for velging av database og henting av databasens tabeller
document.getElementById("selectDB").addEventListener("change", function () {
  document.getElementById("feilmelding").innerHTML="";
  document.getElementById("db").value = document.getElementById("selectDB").value;
  document.getElementById("queryText").focus();
  fetchTableList();

});

function fetchTableList() {
  fetch("/rest/get/tablelist/"+document.getElementById("rdbms_sti").value+"/"+document.getElementById("selectDB").value)
  .then(tabeller => tabeller.text())
  .then(liste => document.getElementById("tabellListe").innerHTML=liste );
}

function strip(html){
   let doc = new DOMParser().parseFromString(html, 'text/html');
   return doc.body.textContent || "";
}

function redigerSQL() {
document.getElementById("editQueryForm").submit();
}

function nySQL() {
let rdbms_sti = document.getElementById("rdbms_sti").value;
document.location.href='/select/'+rdbms_sti;
}

function byggDBListe() {

let rdbms_sti = document.getElementById("rdbms_sti").value;

   let select = document.getElementById("selectDB");

  fetch("/rest/get/dblist/"+rdbms_sti)
  .then(dbs => dbs.json())
  .then(liste => liste.forEach((item) => {
         let option = document.createElement("option");
         option.innerText = item;
         option.value = item;
         if(item == document.getElementById("db").value) {
         option.selected = true;
         }
         select.appendChild(option);
         }));
}

byggDBListe();
