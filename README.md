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
  notification d'alerte, **widget « Pneus »** (4 pneus autour de la voiture, rouge en alerte, ouvre l'écran TPMS)
  utilisable dans les emplacements du launcher ou sur n'importe quel autre écran d'accueil. Protocole :
  [docs/tpms.md](docs/tpms.md).
- **Fonds d'écran** (Paramètres) : aucun, animations intégrées à la couleur d'accent (aurore, étoiles, vagues),
  image fixe, **GIF / WebP animé** (`AnimatedImageDrawable`), **vidéo** MP4 en boucle et muette (ne prend pas le
  focus audio), ou **fond Android / live wallpaper** affiché derrière le launcher (`FLAG_SHOW_WALLPAPER`). Les
  animations se mettent en pause dès que le launcher n'est plus au premier plan.
- **Fond « Spectre »** : vagues lumineuses du fond animé « Music visualization » d'AOSP (Apache 2.0), dans l'esprit de
  [Neospectro](https://github.com/danielnavarrowo/Neospectro) (réécrit en OpenGL ES 2, pas de code repris : le dépôt
  Neospectro n'a pas de licence). 7 palettes dont la couleur d'accent ; option **« suivre la musique »** (spectre du
  son joué via le `Visualizer` d'Android, autorisation micro, rien n'est enregistré).
- **Barre de raccourcis à gauche ou en bas** (Paramètres → Personnalisation).
- **Horloge de veille** : grande horloge numérique ou analogique sur fond noir, date, titre en cours, couleur
  d'accent, luminosité réduite, léger décalage chaque minute (marquage de l'écran), pause de la lecture et reprise à
  la sortie. Déclenchée après N minutes sans toucher l'accueil, par un appui long sur l'heure de la barre, par l'action
  `org.librehu.action.STANDBY_CLOCK` (touche remappée) ou comme **économiseur d'écran Android** (DreamService). Voir
  [docs/standby.md](docs/standby.md).
- **SOS** : bouton d'urgence (barre, tableau de bord et/ou widget, appui long), compte à rebours annulable puis appel
  du numéro d'urgence (112 par défaut), contacts d'urgence, position GPS (décimal + degrés-minutes-secondes) et
  informations pour les secours (nom, infos médicales, véhicule) ; action `org.librehu.action.SOS`. Voir
  [docs/sos.md](docs/sos.md).
- **Assistant de premier démarrage** : apparence et barre, accès Android (accueil par défaut, notifications,
  position / appels), horloge de veille, SOS ; relançable depuis Paramètres → Général.
- **État près de l'horloge** : téléphone connecté en Bluetooth (barres de réseau, batterie, opérateur, lus sur le
  profil mains libres HFP) et **GPS de l'autoradio** (gris éteint, jaune en recherche avec le nombre de satellites
  visibles, vert position acquise avec les satellites utilisés). Désactivables (Paramètres → Personnalisation).
- **Bouton marche/arrêt** : verrouiller, horloge de veille, redémarrer, éteindre (installation privilégiée ou root),
  redémarrer **via le MCU** (branches `ivi` : `ISystem.reboot()` ; `librehu-service` : API 5 `resetSoc()`).
- **Écran de verrouillage** : horloge ou écran noir, glisser ou code ; action `org.librehu.action.LOCK` pour une
  touche (bouton power de la façade tactile, remappage), la même touche déverrouille sans code ; revient par-dessus
  l'accueil tant qu'il n'est pas déverrouillé.
- **Centre de contrôle** (appui sur l'heure de la barre) : heure, date, état du téléphone, luminosité (autorisation
  « Modifier les paramètres système »), volume, Wi-Fi, Bluetooth, thème, horloge de veille, verrouillage, menu
  marche/arrêt, réglages du launcher et d'Android.
- **Tiroir d'applis personnalisable** (Paramètres du launcher → Personnalisation) : taille des icônes, noms, tri
  (A → Z, Z → A, épinglées d'abord), recherche, applis masquées (appui long sur une appli) ; boutons de volume de la
  barre masquables.
- **Téléphone** : « Pas de service » quand le réseau est perdu ; charge déduite (téléphone sur l'USB de l'autoradio,
  ou batterie qui remonte) : le profil HFP ne transmet ni l'état de charge ni le type de réseau (2G…5G).
- Actions `org.librehu.action.POWER_MENU` et `org.librehu.action.ALL_APPS` (touches de façade de LibreHU-service).
- Barres système masquées (balayer depuis le bord pour les afficher).

## Branches

Cette branche : **`librehu-service`** (installer LibreHU-service avant le lanceur).


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
