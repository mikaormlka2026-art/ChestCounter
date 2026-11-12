# ChestCounter : rapport d'architecture

Détection de coffres par **vision classique uniquement** (OpenCV), à partir des frames MediaProjection.
Aucun modèle IA, aucun réseau de neurones, aucune lecture mémoire du jeu, aucune détection de joueurs.

## Ce qui est mesuré et ce qui ne l'est pas
- **Aucune précision n'est annoncée.** Aucune capture réelle de GeForce NOW n'a été utilisée pour régler les seuils.
- Le code a été écrit sans pouvoir être compilé dans l'environnement de rédaction : la compilation et les tests
  sont à lire dans l'onglet Actions de GitHub (workflow « Build APK »).
- Aucune mesure de performance sur appareil n'existe encore. Voir CALIBRATION.md.

## Chaîne de traitement
FrameSource (MediaProjection) → ChestDetector (template matching multi-échelle, plusieurs candidats)
→ analyse classique des meilleurs candidats (RoiAnalyzer) → fusion des preuves (EvidenceFusionEngine)
→ garde-fou anti-faux-positifs → ChestEngine (ChestTracker avec Kalman, ChestPhaseTracker, anti-double comptage)
→ analyse temporelle des coffres suivis (TemporalAnalyzer) → journal et diagnostic.

## Les 10 techniques
1. **Template matching** (`ChestDetector`) : modèles fermé et ouvert, trois échelles, extraction de plusieurs pics, boîte englobante, suppression des doublons.
2. **HSV** (`HsvAnalyzer`) : part de pixels de la couleur cible, répartition sur une grille 3x3, pixels sombres ; une zone uniforme est pénalisée. Preuve secondaire, jamais comptée comme appui.
3. **Canny + contours** (`ContourAnalyzer`) : densité de contours, couverture et continuité du contour principal, rapport largeur/hauteur, remplissage, solidité.
4. **Moments de Hu** (`HuShapeAnalyzer`, `HuMath`) : silhouette comparée aux références, tolérante à l'échelle.
5. **HOG** (`HogAnalyzer`) : descripteur de la ROI normalisée, similarité cosinus avec les références. Aucun classifieur.
6. **ORB** (`OrbAnalyzer`) : points, appariement Hamming, test du ratio, une correspondance par point de référence, vérification géométrique par RANSAC.
7. **Différence d'images** (`TemporalAnalyzer`, `TemporalMath`) : ROI comparée à l'observation précédente du même coffre, après compensation du déplacement.
8. **SSIM** (`Ssim`) : changement structurel entre deux ROI recalées.
9. **Flux optique** (`OpticalFlowAnalyzer`) : Lucas-Kanade pyramidal ; sépare le mouvement d'ensemble (caméra) du mouvement local. Jamais un détecteur.
10. **Kalman** (`KalmanBoxFilter`, `ChestTracker`) : état (x, y, largeur, hauteur, vx, vy) ; prédit la position pour l'association, prolonge un coffre caché (plafonné), stabilise l'identité. Jamais un détecteur.

## Fusion des preuves (`EvidenceFusionEngine`)
Pas d'addition de scores. Les canaux sont : modèle, ORB, HOG, Hu, contours (Canny + contours + géométrie regroupés pour ne pas compter trois fois la même information), HSV, suivi.
Moyenne pondérée des canaux **disponibles** (poids renormalisés). Statuts REJECTED / POSSIBLE / PROBABLE / CONFIRMED selon les seuils de `DetectionConfig` (profils strict, équilibré, permissif).
Règles : le modèle seul ne confirme pas ; il faut au moins deux appuis hors HSV ; trois canaux d'apparence qui contredisent un modèle inférieur à 90 % rejettent le candidat.
Les preuves temporelles ne sont pas dans ce moteur : l'absence de changement est normale pour un coffre fermé ; elles servent aux transitions.

## Suivi et états
`ChestTracker` : association IoU sur la position prédite par Kalman, repli sur le centre, piste confirmée après 2 mesures, perte tolérée 1,5 s. `Track` : id, boîte, score, vitesse, âge, images manquantes.
`ChestPhaseTracker` : DETECTED → OPENING → OPENED → TREASURE_FOUND → COOLDOWN, ou LOST.
Un coffre vu pour la première fois déjà ouvert n'est jamais compté. `TemporalTransitions` cherche un changement visible autour du passage fermé → ouvert (exigence optionnelle, désactivée par défaut).

## Anti-double comptage
Par identifiant de piste (un comptage par piste), par état (il faut avoir vu « fermé »), et par position et temps (`CountDeduplicator`, 3 s par défaut).

## Diagnostic
Écran Diagnostic → « Analyse par candidat » : modèle, couleur, Canny, contour, géométrie, Hu, HOG, ORB, différence, SSIM, flux, Kalman, confiance finale, boîte, piste, état, temps. Journal structuré (`PipelineLog`) dans logcat (étiquette `ChestPipeline`) si `PipelineConfig.logEnabled`.

## Configuration centralisée
`DetectionConfig` (seuils et profils), `ClassicalConfig` (réglages des méthodes), `PipelineConfig` (mode de fusion, budget, exigences), `TrackerConfig`/`KalmanConfig`, `StateConfig`/`PhaseConfig`.

## Tests
Tests unitaires JVM sans OpenCV : formules (`ScoreMath`, `HuMath`, `FeatureMath`, `TemporalMath`, `Ssim`), `KalmanBoxFilter`, `ChestTracker`, `EvidenceFusionEngine`, `TemporalTransitions`, `CountDeduplicator`, garde-fous du moteur, mise en forme du diagnostic, plus les tests existants.
**Non testés automatiquement** (OpenCV natif requis, donc sur appareil) : `HsvAnalyzer`, `ContourAnalyzer`, `HogAnalyzer`, `OrbAnalyzer`, `OpticalFlowAnalyzer`, `TemporalAnalyzer`, `RoiAnalyzer`, `ChestDetector`.
Les 14 scénarios du cahier des charges (coffre petit, partiellement masqué, compression, caméra en mouvement, etc.) demandent de vraies captures : voir CALIBRATION.md.

## Limites connues
- Seuils non calibrés ; références Hu, HOG et ORB tirées d'un seul modèle fermé et d'un seul modèle ouvert.
- Traitement sur le fil de capture (HandlerThread) : pas de fils séparés détection / suivi.
- Dédoublonnage par position inopérant si la caméra a beaucoup bougé entre deux comptages.
- Le flux optique et le SSIM sont peu fiables sur un coffre très petit ou peu texturé : le résultat est alors « non mesurable », jamais un faux changement.
- Pas de test instrumenté Android.
