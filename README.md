# LibreHU Launcher

Lanceur (écran d'accueil) pour autoradios Android, inspiré des interfaces voiture à tableau de bord : thème
sombre, grandes cibles tactiles, rail de raccourcis à gauche, cartes arrondies. Projet indépendant, sans lien avec
Google ni Android Auto, et sans aucun de leurs éléments graphiques.

- **Rail** : tableau de bord, applis épinglées (par défaut : navigation, radio, musique, téléphone), toutes les
  applis, volume − / +, heure.
- **Tableau de bord** : grand emplacement de widget (horloge et date tant qu'il est vide), carte « en cours de
  lecture » (n'importe quelle app avec une `MediaSession` : radio, musique, projection…) avec précédent / lecture /
  suivant, second emplacement de widget avec ajout du widget de [LibreHU FM](https://github.com/LibreHU/LibreHU-FM-App)
  en un geste.
- **Toutes les applis** : grille ; appui long pour épingler, retirer ou réordonner dans le rail.
- **Pneus (TPMS)** : récepteur TPMS USB (les modèles vendus avec les apps « USB TPMS ») lu directement, carte
  sur le tableau de bord, écran détaillé (pression, température, batterie, ID), appairage, permutation, seuils et
  notification d'alerte. Protocole : [docs/tpms.md](docs/tpms.md).
- Barres système masquées (balayer depuis le bord pour les afficher).

## Branches

Cette branche : **`ivi`** (Jancar ivi-services).


| Branche | Volume du rail |
|---|---|
| `main` | volume média Android |
| `ivi` | Jancar **ivi-services** (`IAudio`, puce audio + barre de volume Jancar) |
| `librehu-service` | [LibreHU-service](https://github.com/LibreHU/LibreHU-service) (puce audio) |

## Installation

1. Installer l'APK (artefact de l'Action **Build**), appuyer sur Accueil et choisir **LibreHU Launcher** → Toujours.
2. Carte média : la toucher et autoriser l'**accès aux notifications** (nécessaire pour lire les sessions média).
3. Widgets : Android demande l'autorisation la première fois ; en app privilégiée (`BIND_APPWIDGET`), aucune
   question.

Non testé sur l'autoradio à ce stade.
