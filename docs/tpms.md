# Récepteurs TPMS USB

Analyse (jadx) de cinq apps fournies avec les récepteurs TPMS USB pour autoradios Android :
`USB_TPMS_V1.2` et `USB K03` (`com.cz.usbserial.tpms`), `TPMS-V1.7`, `app-1.5` et `tpms-fyt-android10`
(`com.syt.tmps`). Toutes parlent le même protocole.

## Matériel
Un adaptateur USB-série dans le récepteur, **19200 bauds 8N1**. Identifiants acceptés par les apps
(`res/xml/device_filter.xml`) : FTDI `0403:6001` / `0403:6015`, Arduino `2341:*`, `16C0:0483`, Silicon Labs
CP210x `10C4:EA60`, Prolific PL2303 `067B:2303`, **WCH CH340 `1A86:7523`**.
Les apps `syt` savent aussi lire `/dev/ttyS1` (récepteur intégré de certains autoradios FYT) : **sur l'UJC201,
`/dev/ttyS1` est la MCU, à ne jamais ouvrir pour le TPMS.**

## Trames
```
55 AA LEN TYPE … CS        LEN = longueur totale de la trame, CS = XOR de tous les octets précédents
```
| Sens | Trame | Signification |
|---|---|---|
| ← | `55 AA 0A POS P T F BAT ? CS` (ou `55 AA 08 POS P T F CS`) | pneu : pression `P × 3,44` kPa, température `T − 50` °C, drapeaux `F` (bit 3 fuite, bit 4 alerte pression, bit 5 signal perdu), batterie `BAT × 0,1` V (trames de 10 octets) |
| ← | `55 AA 06 18 POS CS` | appairage réussi pour `POS` |
| ← | `55 AA 09 IDX ID0 ID1 ID2 ID3 CS` | ID du capteur (`IDX` 1 AVG, 2 AVD, 3 ARG, 4 ARD, 5 secours) |
| ← | `55 AA 06 A5 X CS` / `55 AA 06 B5 X CS` | réponses à la « poignée de main » (anti-copie, sans effet sur les données) |
| → | `55 AA 06 19 00 E0` | battement / demande de données (envoyé périodiquement) |
| → | `55 AA 06 01 POS CS` | appairer la roue `POS` |
| → | `55 AA 06 06 00 CS` | arrêter l'appairage |
| → | `55 AA 06 07 00 CS` | lire les ID des capteurs |
| → | `55 AA 07 03 A B CS` | permuter deux roues |
| → | `55 AA 06 5A T CS` / `55 AA 06 5B T CS` | poignée de main (graine `T`) |
| → | `55 AA 06 58 55 CS` | réinitialiser le récepteur |

Positions `POS` : `00` avant gauche, `01` avant droit, `10` arrière gauche, `11` arrière droit, `05` roue de secours.

Les apps `syt` retirent 15 kPa à la pression affichée ; les apps `cz` non. Le lanceur affiche la valeur brute
(`P × 3,44`), comme `cz`.

Les apps `syt` contiennent aussi un ancien protocole (`AA … somme`, classe `FrameDecode`), jamais utilisé : elles
instancient toujours `FrameDecode3`.
