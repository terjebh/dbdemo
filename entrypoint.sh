#!/bin/sh
set -e

# ============================================================
# DBDemo entrypoint
# Sørger for at volume-mappene eies av appuser (uid 1001) uansett
# hvem som eier dem på verten (docker run -v ./data:/home/appuser/.dbdemo
# monterer vertens mappe med vertens eierskap — ofte root).
# Deretter kjører appen som appuser (ikke-root).
# ============================================================

for DIR in /home/appuser/.dbdemo /app/logs; do
  if [ -d "$DIR" ]; then
    chown -R appuser:appuser "$DIR" 2>/dev/null || true
  fi
done

# Dropp til appuser og start applikasjonen
exec setpriv --reuid=1001 --regid=1001 --init-groups \
    java -jar /app/dbdemo.jar
