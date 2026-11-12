# Calibration et mesures

Aucun seuil de ce projet n'est calibré. Voici comment le faire avec de vraies captures.

## 1. Rassembler des données
Active « Collecte de captures » (Réglages) pendant que tu joues, puis trie les images à la main :
- coffres fermés bien visibles, petits, éloignés, partiellement masqués ;
- coffres ouverts, avec et sans butin ;
- faux candidats : objets dorés, interface, texte, particules ;
- passages de caméra rapides ; la même scène en luminosité différente.
Note pour chaque image ce qu'elle contient réellement. Sans cette vérité terrain, aucun taux de précision n'est possible.

## 2. Observer avant de décider
Dans `PipelineConfig`, mets `fusionMode = FusionMode.OBSERVE` : tout est mesuré et affiché (écran Diagnostic) mais rien n'est écarté.
Active `logEnabled = true` pour lire le journal dans logcat (étiquette `ChestPipeline`).
Pour chaque coffre réel, relève les scores par méthode ; pour chaque faux candidat aussi.

## 3. Régler, méthode par méthode
- **HSV** : ajuste `hsvHueMin/Max`, `hsvSatMin`, `hsvValMin` (`ClassicalConfig`) pour que la couleur du coffre réel donne un score élevé et une zone dorée quelconque un score bas.
- **Canny / contours** : `cannyLow/High`, `edgeDensity*`, `aspect*` selon les proportions réelles du coffre.
- **Hu / HOG / ORB** : vérifie que les scores sont élevés sur le coffre réel à plusieurs distances ; refais le modèle si besoin. `huScale`, `hogCosMin/Full`, `orbMinMatches`.
- **Fusion** : `DetectionConfig` (seuils, profil), `FusionWeights`, `FusionRules`.
- **Temps** : `diffPixelThreshold`, `ssimSame/ssimChanged`, `flowResidualFullPx`. Vérifie qu'un coffre qui s'ouvre donne un changement et qu'un simple mouvement de caméra n'en donne pas.

## 4. Activer le garde-fou
Quand les faux candidats sont bien séparés des vrais : `fusionMode = FusionMode.VETO`.
Si des coffres réels sont écartés (voir « REJETÉ » dans le diagnostic), repasse en `OBSERVE` et reprends l'étape 3.
Option : `requireTemporalForCount = true` n'accepte un comptage que si un changement visible accompagne l'ouverture.

## 5. Mesurer
Rejoue des séquences réelles et compare au décompte manuel : coffres comptés à tort, coffres manqués, doubles comptages.
Mesure aussi le temps d'analyse (`Traitement` dans le diagnostic) sur ton appareil. Réduis `maxAnalyzedPerFrame`, `hogEveryNFrames` ou `orbEveryNFrames` si c'est trop lent.
