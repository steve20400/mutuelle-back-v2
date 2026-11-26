"""Resume la montee en charge : un tableau par palier dans le resume de l'execution,
et des annotations publiques (lisibles sans connexion a GitHub)."""
import json
import os

CONFIG = os.environ.get("CONFIG", "?")
DEMARRAGE = os.environ.get("DEMARRAGE", "?")
DEBUT_K6 = int(open("charge/debut_k6.txt").read().strip())
PALIERS = [("palier_020", 20, 0), ("palier_050", 50, 100), ("palier_100", 100, 200), ("palier_200", 200, 300)]
ROUTES = ["connexion", "exercice_en_cours", "parametres", "solde_total", "versements_membre", "liste_membres", "demande_epargne"]

metriques = json.load(open("charge/resume.json"))["metrics"]


def valeur(nom, stat):
    return (metriques.get(nom) or {}).get("values", {}).get(stat)


def ms(x):
    return "-" if x is None else f"{x:.0f}"


def pourcent(x, decimales=2):
    return "-" if x is None else f"{x:.{decimales}f} %"


def mo(x):
    return "-" if x is None else f"{x:.0f} Mo"


def memoire_en_mo(texte):
    """« 512.3MiB » ou « 1.2GiB » -> mégaoctets."""
    texte = texte.strip()
    if texte.endswith("GiB"):
        return float(texte[:-3]) * 1024
    if texte.endswith("MiB"):
        return float(texte[:-3])
    return 0.0


# CPU (en % d'un coeur : 200 % = deux coeurs pleins) et memoire, releves toutes les 5 s
releves = []
for ligne in open("charge/stats.csv"):
    try:
        t, cpu, mem = ligne.strip().split(",", 2)
        releves.append((int(t), float(cpu.rstrip("%")), memoire_en_mo(mem.split("/")[0])))
    except ValueError:
        continue

journal = dict(l.strip().split("=", 1) for l in open("charge/journal.txt") if "=" in l)

lignes = []
for nom, vus, debut in PALIERS:
    nb = valeur(f"http_reqs{{scenario:{nom}}}", "count") or 0
    echecs = valeur(f"http_req_failed{{scenario:{nom}}}", "rate")
    fenetre = [r for r in releves if DEBUT_K6 + debut <= r[0] <= DEBUT_K6 + debut + 90]
    lignes.append({
        "vus": vus,
        "requetes": int(nb),
        "debit": nb / 90,
        "echecs": None if echecs is None else echecs * 100,
        "p95": valeur(f"http_req_duration{{scenario:{nom}}}", "p(95)"),
        "p95_connexion": valeur(f"http_req_duration{{scenario:{nom},nom:connexion}}", "p(95)"),
        "p95_lecture": valeur(f"http_req_duration{{scenario:{nom},type:lecture}}", "p(95)"),
        "cpu": max((r[1] for r in fenetre), default=None),
        "mem": max((r[2] for r in fenetre), default=None),
    })

with open(os.environ.get("GITHUB_STEP_SUMMARY", "/dev/stdout"), "a") as s:
    s.write(f"## Montee en charge - serveur {CONFIG}\n\n")
    s.write(f"- Demarrage de l'API : {DEMARRAGE} s\n")
    s.write(f"- Erreurs dans le journal du serveur : {journal.get('erreurs_serveur')}\n")
    s.write(f"- Arret pour manque de memoire (OOM) : {journal.get('oom')}\n\n")
    s.write("| Utilisateurs | Requetes | Req/s | Echecs | p95 global (ms) | p95 connexion (ms) | p95 lectures (ms) | CPU max | Memoire max |\n")
    s.write("|---|---|---|---|---|---|---|---|---|\n")
    for l in lignes:
        s.write(f"| {l['vus']} | {l['requetes']} | {l['debit']:.1f} | {pourcent(l['echecs'])} | {ms(l['p95'])} "
                f"| {ms(l['p95_connexion'])} | {ms(l['p95_lecture'])} | {pourcent(l['cpu'], 0)} | {mo(l['mem'])} |\n")
    s.write("\n### Temps de reponse par route (tous paliers, ms)\n\n| Route | Mediane | p95 | p99 | Max |\n|---|---|---|---|---|\n")
    for route in ROUTES:
        k = f"http_req_duration{{nom:{route}}}"
        s.write(f"| {route} | {ms(valeur(k, 'med'))} | {ms(valeur(k, 'p(95)'))} | {ms(valeur(k, 'p(99)'))} | {ms(valeur(k, 'max'))} |\n")

paliers_txt = " | ".join(
    f"{l['vus']} u: {l['debit']:.1f} req/s, echecs {pourcent(l['echecs'])}, p95 {ms(l['p95'])} ms "
    f"(connexion {ms(l['p95_connexion'])}, lectures {ms(l['p95_lecture'])}), CPU {pourcent(l['cpu'], 0)}, mem {mo(l['mem'])}"
    for l in lignes)
routes_txt = " | ".join(
    f"{r}: med {ms(valeur(f'http_req_duration{{nom:{r}}}', 'med'))} / p95 {ms(valeur(f'http_req_duration{{nom:{r}}}', 'p(95)'))}"
    f" / max {ms(valeur(f'http_req_duration{{nom:{r}}}', 'max'))}"
    for r in ROUTES)
print(f"::notice title=Charge {CONFIG} - paliers::demarrage {DEMARRAGE} s ; erreurs journal {journal.get('erreurs_serveur')} ; "
      f"OOM {journal.get('oom')} || {paliers_txt}")
print(f"::notice title=Charge {CONFIG} - routes (ms)::{routes_txt}")
