// Montee en charge par paliers : 20, 50, 100 puis 200 utilisateurs simultanes,
// 90 secondes chacun. Chaque utilisateur virtuel joue un membre : connexion,
// lectures de son espace, parfois une demande d'epargne ou la liste complete
// des membres. Les comptes sont crees avant le test par creer_comptes.sh.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const API = __ENV.API || 'http://127.0.0.1:8080';
const MOT_DE_PASSE = 'MonMot2Passe!';
const comptes = new SharedArray('comptes', () => JSON.parse(open('/charge/comptes.json')));

const PALIERS = [
  { nom: 'palier_020', vus: 20, debut: '0s' },
  { nom: 'palier_050', vus: 50, debut: '100s' },
  { nom: 'palier_100', vus: 100, debut: '200s' },
  { nom: 'palier_200', vus: 200, debut: '300s' },
];

const scenarios = {};
// Un seuil par palier et par type de requete force k6 a calculer ces sous-mesures
// dans le resume ; les seuils eux-memes ne servent qu'a les rendre visibles.
const thresholds = {};
for (const p of PALIERS) {
  scenarios[p.nom] = { executor: 'constant-vus', vus: p.vus, duration: '90s', startTime: p.debut, gracefulStop: '10s' };
  thresholds[`http_req_duration{scenario:${p.nom}}`] = ['p(95)<1500'];
  thresholds[`http_req_failed{scenario:${p.nom}}`] = ['rate<0.01'];
  thresholds[`http_reqs{scenario:${p.nom}}`] = ['count>0'];
  thresholds[`http_req_duration{scenario:${p.nom},nom:connexion}`] = ['p(95)<2000'];
  thresholds[`http_req_duration{scenario:${p.nom},type:lecture}`] = ['p(95)<800'];
}
for (const nom of ['connexion', 'exercice_en_cours', 'parametres', 'solde_total', 'versements_membre', 'liste_membres', 'demande_epargne']) {
  thresholds[`http_req_duration{nom:${nom}}`] = ['p(95)<2000'];
}

export const options = {
  scenarios,
  thresholds,
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const requete = (nom, type) => ({ tags: { nom, type }, headers: { 'Content-Type': 'application/json' } });

export default function () {
  const c = comptes[(__VU - 1) % comptes.length];
  const r = http.post(`${API}/api/auth/login-membre`, JSON.stringify({ login: c.couriel, password: MOT_DE_PASSE }), requete('connexion', 'connexion'));
  check(r, { 'connexion acceptee': (x) => x.status === 200 });
  http.get(`${API}/api/exercices/en-cours`, requete('exercice_en_cours', 'lecture'));
  http.get(`${API}/api/parametres`, requete('parametres', 'lecture'));
  http.get(`${API}/api/membres/${c.id}/solde-total`, requete('solde_total', 'lecture'));
  http.get(`${API}/api/versements/membre/${c.id}`, requete('versements_membre', 'lecture'));
  if (Math.random() < 0.2) {
    http.post(`${API}/api/versements/epargne`, JSON.stringify({ membreId: c.id, montant: 1000 }), requete('demande_epargne', 'ecriture'));
  }
  if (Math.random() < 0.05) {
    http.get(`${API}/api/membres`, requete('liste_membres', 'lecture_lourde'));
  }
  sleep(1);
}

export function handleSummary(data) {
  return { '/charge/resume.json': JSON.stringify(data) };
}
