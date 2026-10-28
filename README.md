# LibreHU Launcher

Lanceur (écran d'accueil) pour autoradios Android, inspiré des interfaces voiture à tableau de bord : thème
sombre, grandes cibles tactiles, rail de raccourcis à gauche, cartes arrondies. Projet indépendant, sans lien avec
Google ni Android Auto, et sans aucun de leurs éléments graphiques.

- **Rail** : tableau de bord, applis épinglées (par défaut : navigation, radio, musique, téléphone), toutes les
  applis, volume − / +, heure.
- **Tableau de bord** : grand emplacement de widget (horloge et date tant qu'il est vide), carte « en cours de
  lecture » (n'importe quelle app avec une `MediaSession` : radio, musique, projection…) avec précédent / lecture /
  suivant, second emplacement de widget avec ajout du widget de [LibreHU FM](https://github.com/LibreHU/LibreHU-FM-App)
  en un geste. Le launcher signale au service LibreHU quand le tableau de bord est affiché
  (`org.librehu.action.HOME_VISIBLE`). Le widget « Vue arrière » de LibreHU-service est affiché **en vrai flux** :
  le launcher pose sa propre vue vidéo sur le widget et le service y fait dessiner la caméra directement (pleine
  cadence, `ui/RearLive.kt`) ; un toucher ouvre ou ferme la vue arrière plein écran.
- **Android Auto** ([Open Headunit](https://github.com/andreknieriem/headunit-revived) ou son fork LibreHU) : pendant
  une session, carte sur le tableau de bord (connexion, à l'écran, USB / Wi-Fi ; toucher = afficher la projection,
  bouton déconnecter) et, avec Open Headunit LibreHU, **la prochaine manœuvre de la navigation** (direction,
  distance, route, heure d'arrivée ; diffusion `NAVIGATION_UPDATE` réservée aux applis signées avec la clé LibreHU).
  **Image d'Android Auto sur le tableau de bord** (expérimental, Réglages → apparence, désactivé par défaut) :
  pendant une projection, la carte montre l'image en direct et tactile (boutons plein écran et déconnecter). La
  surface de la carte est confiée au décodeur d'Open Headunit LibreHU (`LibreHuVideoService`, permission signature
  `org.librehu.headunit.permission.CONFIGURE`) tant que son écran de projection n'est pas affiché.
  **Directions en popup** (Réglages → apparence, activé par défaut, désactivable) : chaque nouvelle direction de la
  navigation, puis un rappel à 200 m, affichée 6 s en haut de l'écran par-dessus les autres applis (radio, musique,
  accueil…) ; jamais sur l'écran d'Android Auto, qui l'affiche déjà (Open Headunit LibreHU l'indique dans chaque
  `NAVIGATION_UPDATE`, extra `org.librehu.projection_on_screen`) ; toucher = afficher Android Auto. Fenêtre de
  superposition (`headunit/TurnPopup.kt`) : permission accordée par le root (`appops set … SYSTEM_ALERT_WINDOW allow`)
  ou, sinon, demandée à l'activation.
  Réglages d'Android Auto : LibreHU-service, onglet Android Auto.
- **Toutes les applis** : grille ; appui long pour épingler, retirer ou réordonner dans le rail.
- **Pneus (TPMS)** : récepteur TPMS USB (les modèles vendus avec les apps « USB TPMS ») lu directement, carte
  sur le tableau de bord, écran détaillé (pression, température, batterie, ID), appairage, permutation, seuils et
  notification d'alerte, **widget « Pneus »** (4 pneus autour de la voiture, rouge en alerte, ouvre l'écran TPMS)
  utilisable dans les emplacements du launcher ou sur n'importe quel autre écran d'accueil. Protocole :
  [docs/tpms.md](docs/tpms.md).
- **Météo** (Paramètres → Conduite, désactivée par défaut) : température, temps, ressenti, min / max du jour à la
  position GPS de l'autoradio (arrondie à 0,01°, environ 1 km) par **Open-Meteo** (gratuit sans clé pour un usage non
  commercial, ≤ 10 000 appels / jour, données CC BY 4.0), actualisée toutes les 15 min à 2 h ou après 5 km ;
  **widget « Météo »** (toucher = actualiser) et symbole + température **dans la barre** près de l'heure (option).
  `weather/WeatherRepo.kt`.
- **Limitation de vitesse** (Paramètres → Conduite, désactivée par défaut) : vitesse maximale de la route lue dans
  **OpenStreetMap** (`maxspeed`, `maxspeed:forward` / `:backward` selon le sens, valeurs implicites `FR:urban` 50,
  `FR:rural` 80, `FR:motorway` 130, zones 30…) par l'API **Overpass** : routes à 500 m autour chargées d'un coup,
  rechargées 300 m avant d'en sortir (au plus toutes les 10 s, attente de 60 s si le serveur est saturé) ; la voiture
  est rattachée à la route la plus proche allant dans son sens (cap GPS), une autre route doit gagner deux fois de
  suite. **Alerte de dépassement** : marge en km/h et/ou en %, durée minimale, 6 sons au choix (bip, double, triple,
  carillon, alarme, son de notification) ou aucun, volume, une fois ou répétée, signal au changement de limitation,
  vitesse GPS ou OBD. **Widget « Limitation »** (panneau, rouge en dépassement), **mode « avec la limitation »** du
  widget Vitesse, et panneau **dans la barre** (option). Serveur Overpass au choix. Alerte de limitation seulement :
  **aucun radar** (signaler leur position est interdit en France, art. R413-15 du Code de la route). Données OSM
  participatives : les panneaux font foi. `speed/SpeedLimits.kt`, `speed/SpeedLimitManager.kt`.
- **Android Auto : vidéo basse latence** (Paramètres, à côté de la carte Android Auto) : réglage
  `debug-video-low-latency` d'Open Headunit LibreHU, lu et écrit par ses commandes de réglage (signature LibreHU) :
  `vdec-lowlatency` sur le décodeur MediaTek, en plein écran comme dans la carte (`data/AndroidAutoLink.kt`).
- **Appli téléphone LibreHU** : la page **Conduite** (météo, limitation de vitesse : interrupteurs, marge, son, essai)
  est aussi réglable depuis le téléphone, par LibreHU-service (fournisseur `org.librehu.launcher.bridge`, permission de
  signature `org.librehu.permission.BRIDGE` vérifiée dans `call()` même : Android ne contrôle pas la permission d'un
  fournisseur pour `call()`, `bridge/BridgeProvider.kt`).
- **OBD plein écran** (tiroir → OBD, ou l'action `org.librehu.launcher.action.SHOW_OBD`), comme l'écran Pneus : à
  gauche la **batterie de la voiture** (tension, charge estimée moteur arrêté avec sa barre, « en charge » / « ne
  charge pas » / « surtension » moteur tournant), à droite vitesse et régime en grand puis toutes les valeurs lues
  par l'adaptateur ELM327 de LibreHU-service (standard et **valeurs constructeur** de la voiture choisie, base
  ddt4all). Données : broadcast `org.librehu.action.OBD` du service (`obd/ObdLive.kt`), rien à interroger ; adaptateur,
  valeurs, voiture et alarmes batterie se règlent dans LibreHU-service → OBD (bouton « Réglages OBD »).
- **Carte média** : titre, pochette, ⏮ ⏯ ⏭ et **barre de progression** (temps écoulé / durée, déplaçable quand
  l'appli accepte l'avance rapide). **Musique Bluetooth** : l'AVRCP n'a pas de « aller à la position », seulement
  avance / retour rapides : le lanceur appuie dessus pas à pas en relisant la position, jusqu'à quelques secondes de
  la cible (approximatif ; s'arrête si le lecteur du téléphone les ignore). **LibreHU Disc** : bouton ⏏ **Éjecter**
  en plus (action `org.librehu.disc.action.EJECT` de sa session : arrête la lecture et ouvre le tiroir).
- **Menu power** : verrouiller, horloge de veille, redémarrer, éteindre, redémarrer par la MCU ; boutons centrés.
- **Fluidité** : fonds animés redessinés à 20 i/s (vagues 30, spectre 30) sans recomposer l'écran, arrêtés quand le
  lanceur n'est pas visible ; APK publié en build *release* (non débogable, compilé entièrement par Android).
- **Pages d'accueil** (Paramètres → apparence, 1 à 4) : on glisse sur le côté du tableau de bord vers des pages de
  quatre widgets chacune (points sous les pages).
- **Taille et disposition des widgets** (Paramètres → Personnalisation, désactivé par défaut) : le bouton de
  modification de chaque emplacement règle sa **largeur** et sa **hauteur** (grand widget contre la colonne d'à côté,
  hauteur du second, largeur dans la rangée et hauteur de la rangée sur les pages) et le **déplace** à l'emplacement
  précédent ou suivant, d'une page à l'autre compris. Désactivé : tailles habituelles (les réglages sont gardés).
- **Fond d'écran internet** : image du jour de **Bing** (pays et langue au choix, ou ceux de l'appareil), de
  **Wikimedia Commons**, ou **photo au hasard** (Lorem Picsum, photos Unsplash) ; nouvelle image chaque jour, toutes les
  6 heures ou à chaque démarrage, et bouton « Nouvelle image maintenant ». Téléchargée seulement avec du réseau
  (environ 0,5 Mo), la dernière reste sans réseau ; titre et crédit de l'image en petit dans un coin (désactivable).
- **Style** (Paramètres → Personnalisation) : **Moderne** ou **Afficheur rétro** — les couleurs d'un afficheur
  d'autoradio (VFD, LCD, OEL) **tirées de la couleur d'accent** : la nuit un verre presque noir teinté et le texte
  dans la couleur, le jour un panneau pâle teinté ; jour / nuit et phares comme d'habitude ; la nuit **lignes de
  balayage** sur tout l'écran (un dessin en cache). Les trois styles rétro d'avant (ambre, vert, cassette) sont lus
  comme ce style.
- **Style « Modularer Infotainment-Baukasten »** (écran d'usine ; Paramètres → Personnalisation → Style) : l'allure d'un écran d'origine de voiture, fond
  noir et police Source Sans 3. **MENU** de 8 grandes tuiles (Radio, Média, Applis, Android Auto — appui long : CarPlay —,
  Téléphone, Véhicule, Navigation, Réglages), puis des pages de 8 applis épinglées terminées par une tuile **Ajouter**
  (glisser, points de page). Ailleurs, **barre d'état d'usine** : à gauche boussole et nom de la route (celui des
  alertes de vitesse quand elles sont activées, sinon « MENU » ; un toucher revient au MENU), au centre l'heure et le
  téléphone, à droite la météo. Écran **Média** : titre, artiste, pochette, barre de lecture (toucher ou glisser pour
  avancer) et **touches du bas** (Source, Parcourir, précédent, lecture / pause, suivant, Réglages) ; **Source** ouvre
  un cadre posé sur la touche, deux sources par ligne (radio FM, disque, Android Auto, CarPlay et les applis de
  musique). Écran **Applis** en tuiles, touches Recherche, Tout (applis masquées comprises), MENU et Réglages ; appui
  long sur une appli = son menu (épingler…). **Réglages** à la façon d'origine : liste des pages en barres gris clair à
  texte sombre, page avec touche retour encadrée et titre, interrupteurs en barres claires à case à cocher carrée,
  choix en touches encadrées ; barre d'état avec le globe à gauche de l'heure ; progression sur une ligne (écoulé,
  barre, temps restant). Fenêtres (menu d'une appli, marche / arrêt, confirmations) en cadre noir à filet blanc ;
  **Retour revient à l'écran précédent** (historique des écrans), pas toujours au MENU.
  **Véhicule** : titre entre deux touches encadrées ‹ › qui passent d'« État du véhicule » (OBD) aux Pneus, touches du
  bas Véhicule, Média et Réglages. Mise en page inspirée de TheCarLauncher (LazarC11), réécrite : aucun
  code ni dessin repris, icônes et couleurs de LibreHU. Les autres applis LibreHU le suivent pour l'instant comme le
  style moderne (`style` = `factory`).
- **Style « LHU-Link »** (écran ambiance ; Paramètres → Personnalisation → Style) : l'allure d'une tablette de voiture connectée
  (mise en page inspirée de l'interface R-Link 2 de Renault ; aucun logo, nom, icône, police ni image de Renault : tout
  est redessiné avec les icônes de LibreHU). Fond du noir au bleu nuit, **la couleur d'accent sert d'éclairage
  d'ambiance** (lueur montant du bas, cadres, boutons). **Barre du haut** : heure, raccourcis carte et audio encadrés,
  points de page, téléphone encadré, météo, filet de la couleur d'accent. **Pages d'accueil** dans un cadre éclairé :
  horloge analogique + date et lecture en cours (pochette, titre, précédent / lecture / suivant), puis les applis
  épinglées en petits widgets par quatre, terminées par « Ajouter une appli ». **Menu** de six tuiles (Navigation,
  Multimédia, Téléphone, Applis, Véhicule, Système) aux icônes « métal brossé », qui s'ouvrent en zoom, et
  **sous-menus** comme l'original : Navigation (Destination, Carte, Réglages), Multimédia (Radio, Média, Photo,
  Vidéo, Réglages du son), Téléphone (Répertoire, Historique des appels, Composer numéro, Smartphone, Réglages : onglets
  de LibreHU Téléphone), Apps (Mes apps, LibreHU Apps, Gestionnaire des apps). Là où l'original afficherait le logo
  de son constructeur (la tuile de sa boutique), LHU-Link montre **un cric** dessiné par LibreHU.
  **Véhicule** (Ambiance lumineuse, Aides à la conduite, Aides stationnement, Ordinateur de bord = OBD, Pneus) : la page
  **Ambiance lumineuse** propose les cinq couleurs de l'original (vert, rouge, bleu, violet, ambre) et en fait la couleur
  d'accent ; **Système** (Pages d'accueil, Affichage, Luminosité, Volume, Langue, Date et heure, Informations système). **Retour** remonte d'un niveau, appui
  long = accueil. Touche **Options** : menu surgissant encadré (accueil : modifier les pages, changer de thème, date et
  heure, écran noir ; Multimédia : sources, ouvrir le lecteur, réglages du son), grisée là où il n'y a rien.
  **Réglages** en lignes noires séparées par un filet dans un cadre d'accent, flèche ▶ vers chaque page, interrupteurs
  ON | OFF dont l'état en vigueur est marqué d'un trait rouge (le rouge de sélection de l'original).
  Dans les styles Modularer Infotainment-Baukasten et LHU-Link, les panneaux des écrans OBD et Pneus sont carrés. Fenêtres
  (menu d'une appli, marche / arrêt, confirmations) en bleu nuit à filet d'accent ; Retour revient à l'écran précédent
  (le sous-menu d'où l'on vient compris) ; **Mes apps** en tuiles encadrées quatre par ligne (appui long : menu de l'appli).
  **Multimédia** comme le lecteur d'origine : onglets Lecture / Sources ; Lecture = pochette, titre, artiste, barre de
  progression encadrée avec les temps de chaque côté, touches précédent / lecture / suivant côte à côte ; Sources = les
  lecteurs en tuiles ; barre du bas Retour / Parcourir / Options. **Barre de boutons en bas** : Menu / Retour, l'action principale
  de l'écran (Android Auto dans le menu, appui long : CarPlay ; Sources dans Multimédia) et Options. Écran
  **Multimédia** : pochette, titre, artiste, progression et boutons ronds. Les autres applis LibreHU le suivent comme
  le style moderne (`style` = `ambient`).
- **Couleur d'accent** : 8 couleurs ou la **roue RGB** (rouge, vert, bleu de 0 à 255, aperçu et valeur hexadécimale) ;
  elle colore le thème, le style rétro, les pixels des animations d'afficheur et toutes les applis LibreHU.
- **Police** : normale ou **afficheur à segments** (LibreHU Seg14 : DSEG14, celle de l'afficheur rétro de LibreHU-Disc,
  en majuscules sans accents comme sur un autoradio ; les lettres accentuées sont dessinées sans leur accent ; police
  générée par `tools/make_retro_font.py`, SIL OFL 1.1, `third_party/DSEG-LICENSE.txt`), dans tous les styles.
  **Style, police et palette suivis par toutes les applis LibreHU** (publiés par
  `content://org.librehu.launcher.theme/theme` : colonnes `style` (`modern` / `retro` / `factory` / `ambient`), `font` (`normal` /
  `segment`), `background`, `surface`, `surface_high`, `on_accent`, `text`, `text_dim` en plus de `dark` et `accent`,
  mêmes extras dans `THEME_CHANGED`).
  **Widgets compris** (ceux du launcher et ceux des applis) : couleurs rétro, et police à segments dessinée en image
  (`SegmentViews`, images `<id>_seg` ajoutées par `tools/segment_widgets.py` : un widget Android 9 ne peut pas
  utiliser la police d'une appli ; chaque texte est mis en page à la taille du widget, coupé avec « … » si besoin).
  Le launcher donne à chaque widget la taille de sa case (`updateAppWidgetSize`).
  **Écran de verrouillage et horloge de veille** (écran de veille compris) : en style rétro, les couleurs de nuit de
  l'afficheur (tirées de l'accent), la police choisie et les lignes de balayage ; sinon noir et blanc comme avant.
- **Fonds d'écran** (Paramètres) : aucun, animations intégrées à la couleur d'accent (aurore, étoiles, vagues),
  **afficheur d'autoradio des années 90-2000** en matrice de points fine (256 points de large, 8 niveaux de luminosité
  par point comme un écran OEL, formes lissées, points éteints visibles ; 15 images/s ; grille des points éteints
  dessinée une fois, points allumés en un appel par niveau, le tracé passant par l'accélération matérielle
  d'Android) dans la couleur d'accent : **dauphins** sautant dans une mer sous la pleine lune (reflet scintillant,
  éclaboussures, ronds dans l'eau ; dans l'esprit des autoradios Pioneer à écran organique EL du début des années
  2000 ; dessin original, aucune image de marque), **rallye old school** (vue arrière façon jeux d'arcade des années
  90 : piste en gravier dans une forêt de pins, glisse en contre-braquage, poussière, temps de la spéciale),
  **espace** (galaxie spirale qui tourne lentement, étoiles qui scintillent, planète à anneaux éclairée de côté,
  étoiles filantes, et toutes les 30 s un saut dans l'**hyperespace** : étoiles étirées en traînées), **analyseur de spectre** (32 bandes à segments, crêtes qui retombent, vu-mètres G / D), **cassette** (C-90 :
  bobines vues par la fenêtre, dents des moyeux qui tournent, bande vers les têtes, PLAY et compteur), **route de
  nuit** (virages, pointillés, feux d'une voiture devant) et **mode démo** qui les enchaîne toutes les 20 s ;
  image fixe, **GIF / WebP animé** (`AnimatedImageDrawable`), **vidéo** MP4 en boucle et muette (ne prend pas le
  focus audio), ou **fond Android / live wallpaper** affiché derrière le launcher (`FLAG_SHOW_WALLPAPER`). Les
  animations se mettent en pause dès que le launcher n'est plus au premier plan.
- **Accélération GPU** (Paramètres → Fond d'écran, sous l'animation choisie ; désactivée par défaut = le dessin
  habituel) : aurore, étoiles, vagues, vaporwave et toutes les animations d'afficheur dessinées par la **puce
  graphique** (shaders OpenGL ES 2, `ui/GlScenes.kt`) au lieu de forme par forme. Les animations d'afficheur calculent
  toujours leurs 256 points de large sur le processeur (sur le fil OpenGL), puis les donnent au GPU en petite
  texture ; images demandées 30 fois par seconde (15 pour l'afficheur), pas à 60 Hz ; arrêt quand le launcher n'est
  plus visible. Les palmiers du vaporwave y sont des triangles recalculés à chaque image : ils se balancent aussi.
- **Fond « Vaporwave »** : coucher de soleil néon façon pochettes et clips vaporwave / synthwave des années 2010
  (inspirés des années 80) — soleil à bandes qui défilent, montagnes en fil de fer cyan, sol quadrillé magenta qui
  avance vers soi, palmiers qui se balancent, étoiles qui scintillent ; dans ses propres couleurs (violet, magenta,
  cyan, jaune), dessiné dans le code (aucune image), 20 images/s.
- **Fond « Spectre »** : vagues lumineuses du fond animé « Music visualization » d'AOSP (Apache 2.0), dans l'esprit de
  [Neospectro](https://github.com/danielnavarrowo/Neospectro) (réécrit en OpenGL ES 2, pas de code repris : le dépôt
  Neospectro n'a pas de licence). 7 palettes dont la couleur d'accent ; option **« suivre la musique »** (spectre du
  son joué via le `Visualizer` d'Android, autorisation micro, rien n'est enregistré).
- **Barre de raccourcis à gauche ou en bas** (Paramètres → Personnalisation), avec un **fond réglable** : opacité
  0 à 100 % dans la couleur de surface du thème (70 % par défaut ; 0 % = fond d'écran visible tel quel).
- **Conduite à droite** (Paramètres → Personnalisation) : l'écran d'accueil en **miroir**, côté conducteur — barre de
  raccourcis à droite (en bas : accueil et premiers raccourcis à droite), grand widget à droite, rangées des pages
  depuis la droite ; textes et icônes restent dans le bon sens.
- **Horloge de veille** : grande horloge numérique ou analogique sur fond noir, date, titre en cours (centré sous
  l'heure, artiste en dessous), **batterie de la voiture** sous la date (tension, charge ou état de charge, quand
  l'adaptateur OBD de LibreHU-service est connecté ; désactivable), **jusqu'à 6 valeurs OBD au choix** (vitesse,
  régime, températures, carburant… et valeurs constructeur ddt4all ; dans l'ordre voulu, en ligne ou en tuiles,
  avec ou sans noms ; aussi sur l'écran de verrouillage), couleur
  d'accent, luminosité réduite, léger décalage chaque minute (marquage de l'écran), pause de la lecture et reprise à
  la sortie. Déclenchée après N minutes sans toucher l'accueil, par un appui long sur l'heure de la barre, par l'action
  `org.librehu.action.STANDBY_CLOCK` (touche remappée) ou comme **économiseur d'écran Android** (DreamService). Voir
  [docs/standby.md](docs/standby.md).
- **SOS** : bouton d'urgence (barre, tableau de bord et/ou widget, appui long), compte à rebours annulable puis appel
  du numéro d'urgence (112 par défaut), contacts d'urgence, position GPS (décimal + degrés-minutes-secondes),
  **numéro du téléphone** (transmis par le téléphone en Bluetooth, sinon tapé dans Général : à donner aux secours
  pour être rappelé) et
  informations pour les secours (nom, infos médicales, véhicule : marque, modèle, année, couleur et plaque repris
  de LibreHU-service) ; action `org.librehu.action.SOS` (applis signées LibreHU seulement). Voir [docs/sos.md](docs/sos.md).
- **Assistant de premier démarrage**, étape par étape (chacune peut être passée et se retrouve dans les paramètres) :
  1. **Bienvenue + root** : le type de root est détecté (`system/RootManager.kt` : appli du gestionnaire, sinon
     fichiers `/data/adb/ksud`, `/data/adb/ap`, `/data/adb/magisk`, `daemonsu`, sinon un `su` seul) et le texte
     s'adapte :
     - **SuperSU / Magisk** (demande à l'écran) : chaque demande d'une appli LibreHU doit être accordée pour toujours
       (« Se souvenir » sous SuperSU, pas « une seule fois » sous Magisk) ;
     - **KernelSU / KernelSU Next / APatch** (pas de demande : une appli non autorisée ne voit pas le root) : bouton
       **« Ouvrir KernelSU… »**, activer chaque appli LibreHU dans l'onglet Superuser du gestionnaire, puis vérifier ;
     - **su seul** (intégré au noyau ou à la ROM, sans gestionnaire) : rien à accorder, simple vérification ;
     - **aucun root** : ce qui ne marchera pas, et comment en installer un ;
     puis bouton **« Autoriser le root pour toutes les applis LibreHU »** : le launcher, puis LibreHU-service,
     LibreHU Apps, la radio FM, ViPER4Android, MCU Toolkit et Android Auto (ceux qui sont installés) demandent le root
     l'un après l'autre, donc toutes les demandes arrivent ici, une seule fois ; état de chaque appli
     (accordé, refusé, à mettre à jour = version sans le récepteur, détectée sans attendre, passée, pas de réponse) ;
     bouton « Passer cette appli » pendant l'attente ; les applis jamais ouvertes sont réveillées
     (`FLAG_INCLUDE_STOPPED_PACKAGES`). Protocole : diffusion
     `org.librehu.action.REQUEST_ROOT` envoyée au paquet (`reply_to` = le launcher), réponse
     `org.librehu.action.ROOT_RESULT` (`package`, `granted`) ; chaque appli lance seulement `su -c id`
     (`system/RootGrants.kt`, `RootRequestReceiver` dans chaque appli).
  2. **Langue et heure** : langue d'Android, fuseau horaire, heure / fuseau automatiques, format 24 h, date et heure à
     la main ; réglés en root par un assistant `app_process` (`LocalePicker.updateLocales`,
     `IAlarmManager.setTimeZone` / `setTime` d'Android 9), sans permission privilégiée en plus ; sans root, l'écran
     d'Android s'ouvre ; aussi dans Paramètres → Langue et heure.
  3. **Véhicule** : nom de la voiture (qui renomme Bluetooth, point d'accès et Wi-Fi Direct), marque, modèle, année,
     couleur, plaque, énergie, côté conducteur, gardés par LibreHU-service pour toutes les applis LibreHU (SOS,
     Android Auto, CarPlay).
  4. **Apparence et barre**, 5. **accès Android** (accueil par défaut, notifications, position / appels, mises à jour
     par LibreHU Apps).
  6. **Fonctions LibreHU** : **QR code de l'appli téléphone LibreHU** (dernière version, `latest-main` de
     LibreHU-bridge-app, généré sur place sans réseau) à scanner avec le téléphone ; **association de l'appli** avec
     LibreHU-service (fournisseur `org.librehu.service.bridgepair`, permission de signature
     `org.librehu.permission.BRIDGE`) : **QR code d'association** (jeton à usage unique, valable 5 min, décompte et
     empreinte de l'autoradio) en Wi-Fi, ou **association Bluetooth** (téléphone déjà appairé) dont la demande
     s'accepte ici, aussi proposée à l'étape Bluetooth ; téléphones associés et connectés affichés aussitôt ; puis
     raccourcis vers les réglages
     des autres applis à voir sur un autoradio neuf ou
     fraîchement flashé (chaque ligne n'apparaît que si l'appli est installée) : **restaurer une sauvegarde**
     (LibreHU-service → Système), **touches au volant** (BtnRemap → Mode test des touches, appuis longs,
     combinaisons), **menu son à la molette** (LibreHU-service → Audio, désactivé par défaut), **rétroéclairage des
     boutons** (LibreHU-service → Lumières).
  7. **Horloge de veille**, 8. **SOS**.
  9. **Appairer le téléphone** (façon « configuration rapide ») : nom Bluetooth de l'autoradio tel que le téléphone le
     verra (celui de la voiture), Bluetooth à activer, **rendre visible 2 min**, les étapes sur le téléphone (ouvrir le
     Bluetooth, choisir l'autoradio, vérifier le code des deux côtés, autoriser contacts et journal d'appels), les
     appareils appairés avec le téléphone connecté en premier, mis à jour en direct ; bouton vers les réglages
     Bluetooth d'Android (`ui/BluetoothSetup.kt`).
  10. **Fin.** Relançable depuis Paramètres → Général.
- **État près de l'horloge** : téléphone connecté en Bluetooth (barres de réseau, batterie, opérateur, lus sur le
  profil mains libres HFP) et **GPS de l'autoradio** (gris éteint, jaune en recherche avec le nombre de satellites
  visibles, vert position acquise avec les satellites utilisés). Désactivables (Paramètres → Personnalisation). Sous l'état
  du téléphone : opérateur, nom du téléphone, les deux (deux lignes centrées) ou rien ; batterie en % en option
  (paliers de 20 % : le profil HFP ne donne que 6 niveaux). L'horloge de veille peut aussi afficher l'état du téléphone.
- **Bouton marche/arrêt** : verrouiller, horloge de veille, redémarrer, éteindre (installation privilégiée ou root),
  redémarrer **via le MCU** (branches `ivi` : `ISystem.reboot()` ; `librehu-service` : API 5 `resetSoc()`).
- **Écran de verrouillage** : horloge ou écran noir, glisser ou code, **batterie de la voiture** (OBD, option de
  l'horloge de veille) ; action `org.librehu.action.LOCK` pour une
  touche (bouton power de la façade tactile, remappage), la même touche déverrouille sans code ; revient par-dessus
  l'accueil tant qu'il n'est pas déverrouillé.
- **Centre de contrôle** (appui sur l'heure de la barre) : heure, date, état du téléphone, luminosité (autorisation
  « Modifier les paramètres système »), **curseur de volume** (volume de la puce sur les branches `ivi` et
  `librehu-service`), Wi-Fi, Bluetooth, **partage de connexion** (point d'accès Wi-Fi ; l'allumer directement demande
  une installation privilégiée, sinon la tuile ouvre l'écran Android du partage de connexion), thème, horloge de
  veille, verrouillage, **retrouver le téléphone**, menu marche/arrêt, réglages du launcher et d'Android. **Appui long** sur une tuile ou sur le
  volume : ouvre le réglage Android correspondant (Wi-Fi, Bluetooth, son, affichage…).
- **Retrouver le téléphone** (tuile du centre de contrôle) : le Bluetooth n'a aucun profil pour faire sonner un
  téléphone ; l'autoradio envoie donc, par le téléphone lui-même (profil d'accès aux messages MAP), un SMS à son
  propre numéro : il sonne ou vibre selon ses réglages (pas en « ne pas déranger »). Numéro donné par le téléphone en
  mains libres (AT+CNUM) ou saisi dans Paramètres → Général. Le téléphone demande une fois l'accès aux messages ; un
  SMS peut être facturé. Le client MAP d'Android exige la permission **SEND_SMS** de l'appli qui envoie
  (`MapClientService.sendMessage`) : accordée en root au premier essai, sinon demandée par Android.
- **Sauvegarde des paramètres** (Paramètres → Général → Sauvegarde) : export / import dans un fichier JSON des
  réglages (thème, accueil, veille, SOS, verrouillage, TPMS) et des images importées (fond d'écran, voiture TPMS) ;
  le launcher redémarre après l'import. La disposition des widgets n'est pas incluse (leurs identifiants changent
  d'un appareil à l'autre).
- **Tiroir d'applis personnalisable** (Paramètres du launcher → Personnalisation) : taille des icônes, noms, tri
  (A → Z, Z → A, épinglées d'abord), recherche, applis masquées (bouton « Masquer des applis » du tiroir : un appui masque / réaffiche, liste à interrupteurs dans Réglages → Tiroir d'applis, ou appui long sur une appli ; « Masquées » les montre) ; boutons de volume de la
  barre masquables.
- **Icône et nom de chaque appli** (appui long sur une appli → « Changer l'icône », « Renommer », « Icône et nom
  d'origine ») : n'importe quelle image de l'autoradio (PNG transparent, JPEG…), recadrée au carré et gardée dans le
  launcher ; elle est affichée telle quelle, même avec les icônes thématiques. Pratique pour mettre ses propres logos
  sur les forks LibreHU d'Open Headunit (« Android Auto ») et de xcertplay (« CarPlay »).
- **Lecture / pause** : le dernier basculement est retenu 4 s, pour les lecteurs qui annoncent leur nouvel état en
  retard (YouTube, Tidal en Bluetooth : il ne faut plus appuyer deux fois).
- **Téléphone** : « Pas de service » quand le réseau est perdu ; charge déduite (téléphone sur l'USB de l'autoradio,
  ou batterie qui remonte) : le profil HFP ne transmet ni l'état de charge ni le type de réseau (2G…5G).
- **Widget « Vitesse »** (fourni par le launcher, emplacements ou tout écran d'accueil) : vitesse GPS de
  l'autoradio, vitesse OBD (diffusion `org.librehu.action.OBD` de LibreHU-service avec un ELM327), les deux côte à
  côte, ou la vitesse **avec le panneau de limitation** de la route (rouge en dépassement) ; un toucher change de vue.
  Aux couleurs du thème.
- **TPMS** : alerte sonore (bips sur le haut-parleur média, répétition réglable, bouton « Couper »), fréquence
  d'actualisation, et **capteurs Bluetooth LE** sans récepteur (formats ZEEPIN / TP630 et « BR » / SYTPMS) : onglet
  « Capteurs Bluetooth » pour les voir et les placer sur chaque pneu. Voiture vue de dessus dessinée, roues aux
  couleurs de l'état des pneus (clignotent en alerte), ou **image de sa propre voiture** (PNG importé, essieux
  réglables), aussi dans le widget ; image distincte pour le **mode sombre**, ou image unique affichée telle quelle,
  inversée en mode clair ou inversée en mode sombre. **Connexion tenue** : récepteur USB rouvert seul après une
  erreur de lecture (coupure USB au démarrage du moteur…) toutes les 3 à 5 s tant qu'il est branché, et reconnecté
  s'il ne répond plus pendant 90 s ; scan Bluetooth LE continu relancé toutes les 10 min (Android 7+ rend
  « opportuniste », presque muet, un scan de plus de 30 min) et réessayé toutes les 10 s quand le Bluetooth n'est pas
  encore allumé.
- **Icônes LibreHU aux couleurs du thème** : les applis LibreHU déclarent une icône monochrome (meta-data
  `org.librehu.themed_icon` sur l'application, ou sur l'activité quand une appli a plusieurs entrées, comme « CVBS IN »
  de LibreHU-service), que le launcher dessine sur la couleur d'accent ; elle suit le thème clair / sombre et la
  couleur en direct (désactivable). Les autres applis gardent leur icône.
- Actions `org.librehu.action.POWER_MENU` et `org.librehu.action.ALL_APPS` (touches de façade de LibreHU-service).
- Barres système masquées (balayer depuis le bord pour les afficher).

## Captures d'écran

| | |
|---|---|
| ![Assistant de premier démarrage](docs/screenshots/onboarding.png)<br>Assistant de premier démarrage | ![Accueil](docs/screenshots/home.png)<br>Accueil |
| ![Toutes les applis](docs/screenshots/drawer.png)<br>Toutes les applis | ![Menu marche / arrêt](docs/screenshots/power.png)<br>Menu marche / arrêt |
| ![Réglages : personnalisation](docs/screenshots/settings-look.png)<br>Réglages : personnalisation | ![Réglages : personnalisation (2)](docs/screenshots/settings-look-2.png)<br>Réglages : personnalisation (2) |
| ![Réglages : personnalisation (3)](docs/screenshots/settings-look-3.png)<br>Réglages : personnalisation (3) | ![Réglages : personnalisation (4)](docs/screenshots/settings-look-4.png)<br>Réglages : personnalisation (4) |
| ![Réglages : horloge de veille](docs/screenshots/settings-standby.png)<br>Réglages : horloge de veille | ![Réglages : horloge de veille (2)](docs/screenshots/settings-standby-2.png)<br>Réglages : horloge de veille (2) |
| ![Réglages : verrouillage](docs/screenshots/settings-lock.png)<br>Réglages : verrouillage | ![Réglages : verrouillage (2)](docs/screenshots/settings-lock-2.png)<br>Réglages : verrouillage (2) |
| ![Réglages : conduite](docs/screenshots/settings-driving.png)<br>Réglages : conduite | ![Réglages : conduite (2)](docs/screenshots/settings-driving-2.png)<br>Réglages : conduite (2) |
| ![Réglages : langue et heure](docs/screenshots/settings-region.png)<br>Réglages : langue et heure | ![Réglages : langue et heure (2)](docs/screenshots/settings-region-2.png)<br>Réglages : langue et heure (2) |
| ![Réglages : général](docs/screenshots/settings-general.png)<br>Réglages : général | ![Réglages : général (2)](docs/screenshots/settings-general-2.png)<br>Réglages : général (2) |
| ![Horloge de veille](docs/screenshots/standby.png)<br>Horloge de veille | ![Écran de verrouillage](docs/screenshots/lock.png)<br>Écran de verrouillage |
| ![Style afficheur rétro](docs/screenshots/settings-retro.png)<br>Style afficheur rétro | ![Accueil, style afficheur rétro](docs/screenshots/home-retro-dolphins.png)<br>Accueil, style afficheur rétro |
| ![Toutes les applis, rétro](docs/screenshots/drawer-retro.png)<br>Toutes les applis, rétro | ![Horloge de veille, rétro](docs/screenshots/standby-retro.png)<br>Horloge de veille, rétro |
| ![Verrouillage, rétro](docs/screenshots/lock-retro.png)<br>Verrouillage, rétro | ![Fond aurore](docs/screenshots/home-aurora.png)<br>Fond aurore |
| ![Pneus (TPMS)](docs/screenshots/tpms.png)<br>Pneus (TPMS) |   |
| ![Style LHU-Link, accueil](docs/screenshots/style-lhu-link-home.png)<br>Style LHU-Link, accueil | ![Style LHU-Link, menu](docs/screenshots/style-lhu-link-menu.png)<br>Style LHU-Link, menu |
| ![Style LHU-Link, multimédia](docs/screenshots/style-lhu-link-media.png)<br>Style LHU-Link, multimédia | ![Style Modularer Infotainment-Baukasten, MENU](docs/screenshots/style-mib-menu.png)<br>Style Modularer Infotainment-Baukasten, MENU |

Captures des styles LHU-Link et Modularer Infotainment-Baukasten : rendu Compose sur la JVM (Paparazzi, 1280×720, 160 dpi), sans téléphone ni média. Autres captures sur l'émulateur Android 9 (1280×720, 160 dpi comme l'autoradio), sans autoradio branché : les valeurs du véhicule et de la MCU sont vides. Refaites-les avec l'action **Screenshots** de [LibreHU-OTA-app](https://github.com/LibreHU/LibreHU-OTA-app/tree/main/screenshots). Galerie : [librehu.github.io/screenshots/launcher.html](https://librehu.github.io/screenshots/launcher.html).

## Branches

Cette branche : **`ivi`** (Jancar ivi-services).

| Branche | Volume du rail et du centre de contrôle |
|---|---|
| `main` | volume média Android |
| `ivi` | Jancar **ivi-services** (`IAudio`, puce audio + barre de volume Jancar) |
| `librehu-service` | [LibreHU-service](https://github.com/LibreHU/LibreHU-service) (puce audio) |

## Installation

1. Installer l'APK avec [LibreHU Apps (OTA)](https://github.com/LibreHU/LibreHU-OTA-app), ou l'APK de la release `latest-<branche>` du dépôt (build *release* signé avec la clé commune des applis LibreHU ; les anciens APK de debug avaient chacun leur clé : désinstaller une fois avant), appuyer sur Accueil et choisir **LibreHU Launcher** → Toujours.
2. Carte média : la toucher et autoriser l'**accès aux notifications** (nécessaire pour lire les sessions média).
3. Widgets : Android demande l'autorisation la première fois ; en app privilégiée (`BIND_APPWIDGET`), aucune
   question.

Non testé sur l'autoradio à ce stade.

## Appli téléphone (LibreHU Bridge)

Réglable depuis le téléphone par l'appli LibreHU Bridge (Android et iPhone), à travers LibreHU-service : apparence (thème, style, police, couleur RGB), fond d'écran, accueil (pages, taille et disposition des widgets, conduite à droite, barre), tiroir, horloge de veille, verrouillage, SOS, conduite (météo, limites) et pneus.
Fournisseur `<paquet>.bridge` (`bridge/BridgeProvider.kt`), réservé aux applis signées avec la clé LibreHU (permission
`org.librehu.permission.BRIDGE`, vérifiée dans `call()` lui-même) ; format décrit dans
[LibreHU-service, docs/bridge.md](https://github.com/LibreHU/LibreHU-service/blob/main/docs/bridge.md).

## Optimisation (R8)

L'APK publié par la CI (`assembleRelease`) est **optimisé par R8** : code et ressources réduits et optimisés, ce qui
rend Jetpack Compose nettement plus fluide ([Compose performance](https://developer.android.com/develop/ui/compose/performance)),
**sans renommage** (`-dontobfuscate` dans `app/proguard-rules.pro`) : les traces des journaux restent lisibles. Les
règles gardent aussi ce qui n'est appelé que par son nom (méthodes natives, helpers root lancés par `app_process`). Si
une fonction plante avec « class / method not found », regarder `usage.txt` dans l'artefact *R8 reports* de la CI (ce
que R8 a retiré) et ajouter une règle `-keep`.

Police rétro : DSEG © keshikan, SIL Open Font License 1.1 (`third_party/DSEG-LICENSE.txt`), modifiée et renommée
« LibreHU Seg14 » (nom réservé « DSEG »).

Police du style Modularer Infotainment-Baukasten : Source Sans 3 © Adobe, SIL Open Font License 1.1 (`third_party/SourceSans3-LICENSE.md`).

## Soutenir le projet

LibreHU est développé bénévolement, sur le temps libre, et toutes ses applis restent gratuites. Si le projet vous est
utile, vous pouvez le soutenir par un don sur [Liberapay](https://liberapay.com/DokT/donate) (association française à
but non lucratif, dons récurrents) :

[![Faire un don avec Liberapay](https://liberapay.com/assets/widgets/donate.svg)](https://liberapay.com/DokT/donate)
[![Donateurs sur Liberapay](https://img.shields.io/liberapay/patrons/DokT.svg?logo=liberapay)](https://liberapay.com/DokT/)
