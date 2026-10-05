# Horloge de veille

## Ce que faisait Jancar (ivi-services 3.0.0)

- **Touche power, appui court** : action configurable (`IVIKey.PowerKeyAction`) ; les actions 4
  (« muet + fermer les applis + horloge ») et 6 (« muet + pause média + horloge ») coupent le son, suspendent les
  médias, puis envoient `EventScreenOperate(CLOSE, from = APP_SCREEN_CLOCK)` : les clients `ISystemCallback`
  reçoivent `onCloseScreen(2)` et `com.jancar.services.action.response_close_screen` est diffusé. L'horloge
  elle-même est dessinée par le launcher / SystemUI de Jancar ; le rétroéclairage reste allumé, les touches sont
  bloquées (« Screen clock lock ») sauf power.
- **« Screen protection »** (`ScreenProtectionUtil`) : minuterie d'inactivité (secondes, `IVIConfig`
  `[ScreenProtection]`), relancée à chaque événement du périphérique tactile ; à l'échéance,
  `onScreenProtection(true)` aux clients.

## LibreHU Launcher

- `StandbyActivity` plein écran, fond noir, `FLAG_KEEP_SCREEN_ON`, affichable écran verrouillé ; un toucher ou une
  touche (sauf volume) la ferme.
- Déclencheurs : inactivité sur l'accueil (1 à 30 min), toucher l'heure de la barre, action
  `org.librehu.action.STANDBY_CLOCK` (BtnRemap, touches de façade de LibreHU-service, `am start -a …`),
  **économiseur d'écran Android** (`StandbyDream`, Paramètres Android → Affichage → Économiseur d'écran).
- Améliorations : numérique ou analogique, secondes, date, titre en cours (sessions média), couleur d'accent,
  luminosité propre à la fenêtre (rien n'est changé dans les réglages système), décalage de quelques dp chaque minute,
  pause des médias **et reprise** à la sortie (seulement s'ils jouaient).

Différence voulue : pas de blocage des touches ni de coupure du son par défaut (l'option « mettre en pause »
reproduit l'action 6 de Jancar).

Exemple : `adb shell am start -a org.librehu.action.STANDBY_CLOCK`.
