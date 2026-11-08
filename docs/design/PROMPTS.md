# Prompts pour Claude Code

À coller dans Claude Code depuis la racine du projet Android Ontelevis, une étape à la fois.

## Étape 0 : installer le pack

```
Ce dossier contient un pack de handoff design (design/, app/src/...). Lis d'abord design/DESIGN_HANDOFF.md en entier.
Copie les fichiers Kotlin dans le module app en adaptant le package com.ontelevis au namespace réel du projet (fichiers, imports et import de R).
Copie les tests dans app/src/test.
Résume-moi en 5 lignes ce que tu as compris et ce qui entre en conflit avec le code actuel. Ne modifie rien d'autre pour l'instant.
```

## Étape 1 : dépendances, polices, thème

```
Vérifie que le projet a Jetpack Compose et androidx.tv:tv-material (dernière version stable) ; ajoute-les via le version catalog si besoin.
Ajoute les polices Inter et Inter Tight dans res/font avec les noms listés dans res/font/README.md, puis supprime ce README.
Enveloppe l'application dans OntelevisTheme. Lance ./gradlew :app:compileDebugKotlin et ./gradlew :app:testDebugUnitTest, et corrige toute erreur dans ui/theme, ui/components, ui/guide et core : ce code n'a jamais été compilé (seuls ChannelName et EpgLayout ont été validés).
Montre-moi le diff avant de continuer.
```

## Étape 2 : rail et panneau Catégories

```
Remplace la navigation actuelle de l'écran Télé par le rail replié de 72 dp (qui se déploie au focus) et le panneau Catégories de 208 dp, selon le §4 de design/DESIGN_HANDOFF.md.
Référence visuelle : design/mockups/tele_guide.png (1920×1080 px, diviser par 2 pour les dp). Valeurs exactes : design/mockups/tele_guide.html.
Garde la logique et les ViewModels existants, ne change que la présentation. Utilise parseChannelName pour les libellés des catégories.
```

## Étape 3 : aperçu vidéo et panneau d'info

```
Refais la zone aperçu + panneau d'info selon le §4 (Lecteur, Panneau d'info), branchée sur le lecteur existant (ExoPlayer / Media3).
OK sur l'aperçu ou sur « Plein écran » ouvre le lecteur plein écran ; ses contrôles se masquent après 3 s sans touche.
```

## Étape 4 : guide EPG

```
Refais le guide selon le §4 (Guide) avec un seul LazyColumn de rangées complètes.
Utilise EpgWindow, spanOf, programState, progressOf et minutesLeft de ui/guide/EpgLayout.kt, et EpgProgramBlock / EpgNowLine de OntComponents.kt.
Gère le cas des chaînes sans EPG avec le bloc « Diffusion continue ». La ligne « maintenant » se met à jour chaque minute.
```

## Étape 5 : navigation D-pad et finitions

```
Applique le §6 (navigation au D-pad) à l'écran Télé, avec des FocusRequester explicites pour les transitions listées.
Ajoute des tests UI Compose pour : droite depuis une catégorie → bloc de la chaîne en lecture ; retour depuis le guide → catégories.
Puis applique le thème, le rail et les composants aux autres écrans sans changer leur structure (§7).
Fais une capture de l'émulateur Android TV 1080p et compare-la à design/mockups/tele_guide.png : liste les écarts restants.
```

## Astuce

Glisse `design/mockups/tele_guide.png` dans le terminal Claude Code quand tu veux une comparaison visuelle.
