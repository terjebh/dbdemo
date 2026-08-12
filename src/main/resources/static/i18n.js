// ===== Enkel i18n for DBApp (start): Norsk / Engelsk =====
// Språket lagres i localStorage («no» default). Velges med flagg-knappen
// i toppmenyen. DataTables-språk + viktige UI-tekster følger valget.
(function () {
  window.dbAppLang = localStorage.getItem("lang") || "no";

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
    },
  };

  window.dbAppTekster = () => TEKSTER[window.dbAppLang] || TEKSTER.no;

  // Sett flagg-knappen + bytt språk (idempotent — kalles fra både meny-JS
  // og select.js, men listeners skal bare settes ÉN gang)
  window.settOppSprakVelger = function () {
    const knapp = document.getElementById("sprakKnapp");
    if (!knapp) return;
    if (knapp.dataset.sprakSatt === "ja") return;
    knapp.dataset.sprakSatt = "ja";
    const oppdaterIkon = () => {
      knapp.textContent = window.dbAppLang === "no" ? "🇳🇴" : "🇬🇧";
      knapp.title = window.dbAppLang === "no" ? "Switch to English" : "Bytt til norsk";
    };
    knapp.addEventListener("click", () => {
      window.dbAppLang = window.dbAppLang === "no" ? "en" : "no";
      localStorage.setItem("lang", window.dbAppLang);
      oppdaterIkon();
      // DataTables finnes allerede → oppdater språket
      if (window.DataTable) {
        document.querySelectorAll(".dataTable").forEach((t) => {
          const dt = window.DataTable.get(t);
          if (dt) dt.language(window.dbAppTekster().datatables);
        });
      }
      // Last innholdet på nytt så alle tekster bytter
      if (window.location.pathname.includes("/select/") || window.location.pathname.includes("/sqlite/")) {
        window.location.reload();
      }
    });
    oppdaterIkon();
  };
})();
