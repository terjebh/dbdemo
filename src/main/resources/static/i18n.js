// ===== i18n for DBApp: Norsk / Engelsk =====
// Språket styres AV SERVEREN via CookieLocaleResolver (cookie «lang»).
// Flagg-knappen setter cookien og reloader — da rendrer Thymeleaf alle
// tekster på nytt (meny, hjelp, om, brukere, sqlite, setup).
// Denne filen håndterer kun: flagg-knappens utseende + DataTables-språk
// + et par dynamiske statusmeldinger i select.js.
(function () {
  // Les locale fra serveren (satt i <html lang="...">) eller cookie
  window.dbAppLang =
    document.documentElement.lang === "en" ? "en" : "no";

  const TEKSTER = {
    no: {
      datatables: {
        lengthMenu: "Vis _MENU_ rader",
        zeroRecords: "Ingen rader funnet",
        info: "Viser _START_–_END_ av _TOTAL_ rader",
        infoEmpty: "Ingen rader",
        infoFiltered: "(filtrert fra _MAX_ rader totalt)",
        search: "Søk:",
        paginate: { first: "Første", last: "Siste", next: "Neste", previous: "Forrige" },
        emptyTable: "Ingen data i tabellen",
      },
      rader: "rader",
      feilTom: "SQL-spørringen er tom",
      kjorer: "Kjører …",
      ingenKolonner: "Spørringen ble utført (ingen rader returnert)",
      tittel: "Bytt til engelsk",
    },
    en: {
      datatables: {
        lengthMenu: "Show _MENU_ rows",
        zeroRecords: "No matching records found",
        info: "Showing _START_–_END_ of _TOTAL_ rows",
        infoEmpty: "No rows",
        infoFiltered: "(filtered from _MAX_ total rows)",
        search: "Search:",
        paginate: { first: "First", last: "Last", next: "Next", previous: "Previous" },
        emptyTable: "No data in table",
      },
      rader: "rows",
      feilTom: "SQL query is empty",
      kjorer: "Running …",
      ingenKolonner: "Query executed (no rows returned)",
      tittel: "Switch to Norwegian",
    },
  };

  window.dbAppTekster = () => TEKSTER[window.dbAppLang] || TEKSTER.no;

  // Setter en cookie (ingen path-restriksjon — hele appen)
  function settCookie(navn, verdi) {
    document.cookie = navn + "=" + encodeURIComponent(verdi) +
      "; path=/; max-age=" + (60 * 60 * 24 * 365) + "; SameSite=Lax";
  }

  // Sett flagg-knappen + bytt språk (idempotent — kalles fra både meny-JS
  // og select.js, men listeners skal bare settes ÉN gang)
  window.settOppSprakVelger = function () {
    const knapp = document.getElementById("sprakKnapp");
    if (!knapp) return;
    if (knapp.dataset.sprakSatt === "ja") return;
    knapp.dataset.sprakSatt = "ja";
    const oppdaterIkon = () => {
      knapp.textContent = window.dbAppLang === "no" ? "🇳🇴" : "🇬🇧";
      knapp.title = window.dbAppTekster().tittel;
    };
    knapp.addEventListener("click", () => {
      // Bytt språk: sett cookie «lang» og last siden på nytt — serveren
      // rendrer alle tekster i valgt språk (Spring MessageSource).
      const nytt = window.dbAppLang === "no" ? "en" : "no";
      settCookie("lang", nytt);
      window.location.reload();
    });
    oppdaterIkon();
  };
})();
