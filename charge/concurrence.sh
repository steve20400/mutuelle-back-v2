#!/usr/bin/env bash
# Test de concurrence : 20 validations simultanees du meme versement d'epargne.
# Un service correct n'en accepte qu'une et ne credite l'epargne qu'une fois.
set -u
API=${API:-http://127.0.0.1:8080}
CONFIG="$1"
M=$(curl -s -H 'Content-Type: application/json' \
  -d '{"nom":"Concurrence","prenom":"Test","couriel":"concurrence@charge.test","telephone":"620000001"}' \
  "$API/api/membres" | jq -r '.id')
V=$(curl -s -H 'Content-Type: application/json' -d "{\"membreId\":\"$M\",\"montant\":10000}" \
  "$API/api/versements/epargne" | jq -r '.id')
avant=$(curl -s "$API/api/membres/$M" | jq -r '.epargne')
seq 20 | xargs -P 20 -I{} curl -s -o /dev/null -w '%{http_code}\n' -X POST "$API/api/versements/$V/accepter" > charge/concurrence.txt
apres=$(curl -s "$API/api/membres/$M" | jq -r '.epargne')
acceptes=$(grep -c '^200$' charge/concurrence.txt)
echo "acceptes=$acceptes avant=$avant apres=$apres" > charge/concurrence_resultat.txt
echo "::notice title=Concurrence $CONFIG::20 validations simultanees du meme versement de 10000 FCFA : $acceptes acceptee(s) (attendu 1). Epargne avant $avant, apres $apres (attendu +10000)."
