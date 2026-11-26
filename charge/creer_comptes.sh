#!/usr/bin/env bash
# Cree les comptes fictifs de la montee en charge, par la route d'enregistrement
# du secretaire general, et les ecrit dans charge/comptes.json pour k6.
set -u
API=${API:-http://127.0.0.1:8080}
NB=${NB_COMPTES:-200}
echo "[" > charge/comptes.json
crees=0
for i in $(seq 1 "$NB"); do
  n=$(printf '%04d' "$i")
  tel=$(printf '61%07d' "$i")
  id=$(curl -s -H 'Content-Type: application/json' \
    -d "{\"nom\":\"Charge\",\"prenom\":\"Compte $n\",\"couriel\":\"charge$n@charge.test\",\"telephone\":\"$tel\"}" \
    "$API/api/membres" | jq -r '.id // empty')
  if [ -n "$id" ]; then
    [ "$crees" -gt 0 ] && echo "," >> charge/comptes.json
    printf '{"id":"%s","couriel":"charge%s@charge.test"}' "$id" "$n" >> charge/comptes.json
    crees=$((crees + 1))
  fi
done
echo "]" >> charge/comptes.json
echo "$crees comptes crees sur $NB"
