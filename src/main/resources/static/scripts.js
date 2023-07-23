hljs.highlightElement(queryText);

document.getElementById("hent").addEventListener("click",function ()  {
if (document.getElementById("selectDB").value=="Velg Database") {
   document.getElementById("feilmelding").innerHTML="Velg en database å hente data fra ...";
} else if (document.getElementById("queryText").innerHTML=="") {
   document.getElementById("feilmelding").innerHTML="Skriv en SQL-setning å hente data med...";
} else {
  let renQueryText = strip(document.getElementById("queryText").innerHTML);
  document.getElementById("query").value = renQueryText;
  document.getElementById("sql").action = document.getElementById("rdbms_sti").innerHTML
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
  fetch("/rest/get/tablelist/"+document.getElementById("selectDB").value)
  .then(tabeller => tabeller.text())
  .then(liste => document.getElementById("tabellListe").innerHTML=liste );
}

function strip(html){
   let doc = new DOMParser().parseFromString(html, 'text/html');
   return doc.body.textContent || "";
}

function redigerSQL() {
let rdbms_sti = document.getElementById("rdbms_sti").innerHTML;
let sql = document.getElementById("sql").innerHTML;
sql = sql.replace("&lt;","<");
sql = sql.replace("&gt;",">");
let db = document.getElementById("db").innerHTML;
document.location.href='/select/'+rdbms_sti+'?db='+db+'&sql='+sql;
}

function nySQL() {
let rdbms_sti = document.getElementById("rdbms_sti").innerHTML;
document.location.href='/select/'+rdbms_sti;
}

const UrlParam = new URLSearchParams(window.location.search);

if( UrlParam.get('sql')) {
let sql = UrlParam.get('sql');
document.getElementById("queryText").innerHTML = sql;
hljs.highlightElement(queryText);
}

if(UrlParam.get('db')) {
let db = UrlParam.get('db');
document.getElementById("selectDB").value = db;
document.getElementById("db").value = db;
fetchTableList();
}
