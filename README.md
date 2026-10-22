# IPTV Player TV

Appli Google TV / Android TV du lecteur IPTV, compagnon de l'appli PC (`app_iptv_pc`) : même catalogue Xtream,
mêmes profils, favoris, reprises de lecture, « Vu récemment », épisodes vus, langues et catégories masquées,
synchronisés par le compte du foyer (Firebase).
Kotlin + Jetpack Compose for TV (interface à la télécommande) + libVLC (lecture, même moteur que sur PC).

## Organisation du projet
```
app_iptv_tv/
├─ version.properties       version de l'appli (une ligne, comme __init__.py côté PC)
├─ build_release.bat        créer l'APK publié dist\IPTVPlayerTV-<version>.apk (signé)
├─ keystore.properties      mots de passe de la clé de signature (HORS dépôt, voir plus bas)
├─ app/build.gradle.kts     dépendances, signature (lit version.properties)
└─ app/src/main/java/io/github/pauljean445/iptvtv/
   ├─ MainActivity.kt       écrans (pile, touche Retour), mise à jour proposée, messages
   ├─ AppModel.kt           état de l'appli : compte, profils, catalogue, favoris, synchro toutes les 30 s
   ├─ IptvApp.kt            chargeur d'images (cache limité : les TV ont peu de stockage)
   ├─ data/                 Item, Xtream (API), Playlist (M3U), CatalogCache, Store (réglages, profils),
   │                        Library (favoris, reprises…), Cloud (Firebase REST), Sync (fusion), Vault (chiffrement),
   │                        PairingServer (connexion par QR code)
   ├─ player/               PlayerScreen (lecteur VLC), Tracks (langues et sous-titres)
   ├─ ui/                   écrans : connexion, « Qui regarde ? », accueil, onglets, fiche, replays, réglages
   └─ update/               Updater (releases GitHub, installation), UpdateDialog
```
Les formats de données (library.json, documents Firestore, chiffrement de la source IPTV) sont **exactement ceux de
l'appli PC** : toute évolution doit être faite des deux côtés (voir `sync.py` / `library.py` côté PC).

## Prérequis (PC de développement)
Sans droits administrateur, dans `%LOCALAPPDATA%\Android` : JDK 17 (Temurin, `jdk-17`), SDK Android (`Sdk` :
plateforme 35, build-tools, platform-tools pour `adb`). Variables d'utilisateur `JAVA_HOME` et `ANDROID_HOME`.
`local.properties` (hors dépôt) contient `sdk.dir=…/Android/Sdk`. Gradle est téléchargé par `gradlew.bat`.

## Compiler, installer sur la TV
- Version publiée (optimisée, à utiliser même pour les essais : la version de développement est très lente) :
  `gradlew.bat assembleRelease` → `app\build\outputs\apk\release\app-release.apk`.
- Installer sur la TV par le réseau : sur la TV, Paramètres → Système → À propos → appuyer 7 fois sur « Build »,
  puis Options pour les développeurs → « Débogage ADB » (Sony). Ensuite :
  `adb connect <ip-tv>:5555`, `adb install -r app-release.apk`, accepter « Autoriser le débogage » sur la TV.
- Journal de l'appli : `adb logcat -s IPTV:* AndroidRuntime:E`. Capture d'écran : `adb exec-out screencap -p > tv.png`.
- TV presque pleine (`INSTALL_FAILED_INSUFFICIENT_STORAGE`) : `adb shell pm trim-caches 2000M` vide les caches
  des applis (sans perte de comptes ni de réglages).

## Publier une version (mise à jour proposée dans l'appli)
1. augmenter la version dans `version.properties` (ex. `version=1.0.2`) : le numéro interne d'Android
   (`versionCode`) en est déduit (1.0.2 → 10002), toujours croissant ;
2. double-cliquer sur `build_release.bat` → `dist\IPTVPlayerTV-1.0.2.apk` ;
3. commiter / pousser, puis sur GitHub : *Releases* > *Draft a new release*, tag `v1.0.2`, joindre l'APK, décrire les
   nouveautés (affichées dans l'appli), *Publish release*.

Le dépôt GitHub `pauljean445/app_iptv_tv` doit être **public** (constante `REPO` dans `update/Updater.kt`).
L'appli cherche une nouvelle version 8 s après son ouverture (et via Réglages → « Rechercher une mise à jour ») ;
la fenêtre n'apparaît jamais pendant une lecture. Elle télécharge l'APK (après avoir vérifié la place libre) et le
confie à l'installeur d'Android. La première fois, Android demande d'autoriser l'appli à « installer des applis
inconnues » (bouton « Autoriser » de la fenêtre).

## Clé de signature (à sauvegarder !)
L'APK publié est signé par `%USERPROFILE%\.android-keys\iptvtv-release.jks` (alias `iptvtv`), dont les mots de passe
sont dans `keystore.properties` à la racine du projet. **Ces deux fichiers ne sont pas dans le dépôt** et doivent être
sauvegardés ailleurs : sans eux, aucune mise à jour ne peut s'installer sur les TV existantes (il faudrait
désinstaller l'appli partout). Une version de développement (`assembleDebug`, autre signature) ne peut pas non plus
remplacer une version publiée sans désinstallation.

## Connexion d'une TV au compte du foyer
Au premier lancement, la TV affiche un QR code. Le téléphone (même Wi-Fi) l'ouvre et reçoit de la TV un formulaire :
email et mot de passe du compte du foyer. La TV :
- se connecte (le mot de passe n'est pas enregistré : seulement une session, chiffrée par le Keystore d'Android) ;
- calcule la clé de chiffrement de la source IPTV à partir du mot de passe (PBKDF2) et récupère l'abonnement IPTV
  du foyer, stocké **chiffré** dans le compte (AES-256-GCM : ni Google ni Firebase ne peuvent le lire) ;
- si le compte n'a pas encore d'abonnement, le téléphone le demande (étape 2), puis il est partagé chiffré.

Règle de l'abonnement partagé (identique sur PC) : le compte sert à **configurer les appareils qui n'en ont pas**
(nouvelle TV, nouveau PC, profil sans abonnement). Un profil qui a déjà son abonnement sur l'appareil le **garde**
(chaque appareil peut avoir le sien : le fournisseur donne par exemple un lien « TV » différent de celui du PC). Un
changement d'abonnement fait sur un appareil met à jour la copie du compte (pour les futurs appareils) sans être
imposé aux autres.

Le mini-serveur de la TV n'existe que pendant cet écran, n'écoute que sur le réseau local, refuse toute requête sans
le code aléatoire du QR (128 bits) et s'arrête dès que la connexion réussit. Réglages → « Changer d'abonnement IPTV »
rouvre l'étape 2 seule.

## Synchronisation
Comme sur PC (voir le README de l'appli PC) : la liste des profils et, pour chaque profil, favoris (ordre compris),
lectures en cours, « Vu récemment », épisodes vus, langues et catégories masquées. Pour chaque entrée, la
modification la plus récente gagne (suppressions comprises). Toutes les 30 s (liste des profils toutes les 2 min),
au changement de profil et quand l'appli passe en arrière-plan. Serveur injoignable : l'appli continue et rattrape.

## Utilisation (télécommande)
- Onglets en haut ; à droite, le profil (☰) ouvre les réglages : profils (créer, modifier, réinitialiser, supprimer),
  catalogue, abonnement IPTV, compte, mise à jour.
- Appui long sur une chaîne / un film / une série : favoris, « Vu récemment ». Sur l'accueil : retirer.
- « Vu récemment » : un film / une série y entre dès que l'image démarre ; la position de reprise n'est gardée
  qu'après 15 s (même règle que le PC). Série lancée moins de 15 s : relancée au même épisode, depuis le début.
- Appui long sur une catégorie : la masquer (rangée dans « Autres », en bas de la liste) ou la réafficher.
- Lecture : OK pause / lecture (en direct : différé, « Revenir au direct »), ← → ±10 s (replay ±1 min),
  ↑ ↓ chaîne précédente / suivante en direct ; commandes affichées : barre de temps (← → puis OK), langue et
  sous-titres (choix mémorisé par film / par série), épisode ou programme suivant.
- Fiche d'un film / d'une série : lecture, reprise, favoris, langue et sous-titres, saisons et épisodes
  (appui long sur un épisode : vu / non vu, lire depuis le début).
