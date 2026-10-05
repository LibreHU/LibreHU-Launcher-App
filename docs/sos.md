# SOS

Écran d'urgence du launcher (`SosActivity`).

- **Ouverture** : bouton rouge dans la barre et/ou sur le tableau de bord (appui long par défaut), widget « SOS »,
  action `org.librehu.action.SOS` (touche remappée : `adb shell am start -a org.librehu.action.SOS`).
- **Compte à rebours** (0, 5, 10 ou 20 s, annulable) puis appel du numéro d'urgence (112 par défaut).
- **Appels** : par le téléphone connecté en Bluetooth (profil mains libres). Android interdit à une appli d'appeler
  directement un numéro d'urgence avec `ACTION_CALL` : sans installation privilégiée (`CALL_PRIVILEGED`), le numéro
  est **composé** et il reste à appuyer sur « appeler ». Les contacts d'urgence (5 max) sont appelés directement si
  `CALL_PHONE` est accordée.
- **Position** : GPS (et réseau si disponible), en décimal et en degrés-minutes-secondes, précision et âge.
- **Informations pour les secours** : nom, infos médicales, véhicule (modèle, couleur, plaque), à lire à
  l'opérateur.
- Le menu **Tester l'écran** l'ouvre sans compte à rebours.

Limites : sans téléphone connecté (ou sans appli téléphone sur l'autoradio), aucun appel ne part : l'écran le dit.
Ce n'est pas un eCall (les voitures homologuées dans l'UE depuis le 31/03/2018 en ont un, déclenché par les
airbags) : ici, rien n'est automatique en cas de choc.
