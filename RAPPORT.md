# Rapport d'état : Chest Counter

Date : 10 novembre 2026

## Résumé

Le projet est entièrement écrit (16 fichiers) mais **n'a jamais été compilé ni testé**. Aucune fonction ne doit être considérée comme fonctionnelle pour l'instant.

## Fichiers du projet

| Fichier | Rôle | Statut |
|---|---|---|
| `.github/workflows/build.yml` | Compilation automatique | Non testé |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` | Configuration Gradle (racine) | Non testé |
| `app/build.gradle.kts` | Dépendances et réglages de l'appli | Non testé |
| `AndroidManifest.xml`, `strings.xml` | Déclarations de l'appli | Non testé |
| `detect/ChestStateMachine.kt` | Machine à états anti-doublons | Non testé |
| `detect/ChestDetector.kt` | Détection OpenCV fermé/ouvert | Non testé |
| `data/Db.kt` | Base Room (comptes, stats) | Non testé |
| `capture/CaptureService.kt` | Capture d'écran MediaProjection | Non testé |
| `ui/Hud.kt` | HUD flottant | Non testé |
| `ui/MainActivity.kt` | Tableau de bord | Non testé |
| `ChestStateMachineTest.kt` | Tests de la machine à états | Non lancé |
| `README.md`, `RAPPORT.md` | Documentation | Rédigé |

## Principe de la machine à états

Un coffre est compté seulement si l'appli voit d'abord un coffre fermé sur plusieurs images de suite, puis un coffre ouvert sur plusieurs images de suite. Un délai minimum entre deux comptes évite les doublons. Le coffre reste « compté » tant qu'il est visible ouvert.

## Points à vérifier

1. Le build GitHub Actions passe-t-il ? (erreurs de compilation possibles, jamais testé)
2. Les tests de la machine à états passent-ils ?
3. MediaProjection capte-t-il bien l'image de GeForce NOW sur la tablette ?
4. Les modèles « fermé / ouvert » fonctionnent-ils avec la vraie image du jeu ? (calibrage nécessaire)
5. Le HUD flottant s'affiche-t-il par-dessus GeForce NOW ?
6. Le service tient-il en arrière-plan sans être arrêté par Android ?

## Limites connues

- Les modèles de détection dépendent de la résolution et de la qualité du flux GeForce NOW.
- Le comptage est une estimation faite à partir de l'image, il peut se tromper.
- Aucune mesure de fiabilité n'existe pour l'instant.

## Principes

- L'appli regarde l'écran et compte, rien d'autre.
- Aucune commande envoyée au jeu, aucun contournement de protection.

## Prochaines étapes

1. Lancer le workflow « Build APK », corriger les erreurs avec le log.
2. Installer l'APK sur la tablette.
3. Test physique : capture de GeForce NOW, puis calibrage de la détection.
