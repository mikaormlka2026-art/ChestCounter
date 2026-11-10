# Chest Counter

Application Android qui compte les coffres ouverts dans Fortnite pendant une partie jouée via GeForce NOW. Objectif : arriver à 1000 coffres.

> **État : projet jamais compilé ni testé sur un appareil.** Rien ci-dessous ne doit être considéré comme fonctionnel tant que ce n'est pas validé.

## Ce que l'appli est censée faire

- Capturer l'écran avec MediaProjection (service au premier plan).
- Repérer à l'image un coffre fermé, puis un coffre ouvert (OpenCV, comparaison avec des modèles).
- Compter un coffre une seule fois grâce à une machine à états (pas de doublons).
- Enregistrer les comptes dans une base Room.
- Afficher un HUD flottant et un tableau de bord sombre/violet avec les statistiques.

## Ce que l'appli ne fait pas

- Elle ne touche pas au jeu et n'envoie aucune commande.
- Elle ne contourne aucune protection du jeu ni de GeForce NOW.
- Elle ne fait que regarder l'écran et compter.

## Technique

- Kotlin, Jetpack Compose, Room, OpenCV
- Compilation automatique avec GitHub Actions (workflow « Build APK »)

## Structure

- `app/src/main/java/com/chestcounter/detect/` : détection et machine à états
- `app/src/main/java/com/chestcounter/capture/` : service de capture d'écran
- `app/src/main/java/com/chestcounter/data/` : base de données
- `app/src/main/java/com/chestcounter/ui/` : HUD et écran principal
- `app/src/test/` : tests de la machine à états

## Obtenir l'APK

1. Onglet **Actions** du dépôt, workflow **Build APK**.
2. Lancer le workflow (Run workflow), attendre la fin.
3. Télécharger l'APK dans les artefacts du run.

## À valider sur l'appareil

- MediaProjection capte-t-il bien GeForce NOW ?
- Les modèles « fermé / ouvert » sont-ils bien calibrés ?
- Le HUD flottant s'affiche-t-il par-dessus l'appli ?
