# Ontelevis, spec de refonte visuelle (Android TV)

Cible : Android TV uniquement, Kotlin, Jetpack Compose + `androidx.tv:tv-material` (Compose for TV). Navigation à la télécommande (D-pad), pas de tactile ni de souris.

Références visuelles :
- `design/mockups/tele_guide.png` : écran Télé, 1920 × 1080 px.
- `design/mockups/systeme_visuel.png` : couleurs, typo, états au focus.
- Les `.html` du même nom contiennent les valeurs exactes en style inline.

**Conversion : les maquettes sont dessinées en px à 1920 × 1080. Android TV affiche 960 × 540 dp. Diviser chaque valeur px par 2 pour obtenir des dp ou des sp.** Les valeurs déjà converties sont dans `OntDimens.kt` et `OntType.kt`.

Code fourni (non compilé, à intégrer et corriger au besoin) :
- `ui/theme/` : `OntColors.kt`, `OntDimens.kt`, `OntType.kt`, `OntelevisTheme.kt`
- `ui/components/OntComponents.kt` : étiquette, badge en direct, libellé de section, tuile logo, item de liste, bouton principal, bouton icône, bloc de programme, ligne « maintenant »
- `ui/guide/EpgLayout.kt` : calculs du guide (fenêtre, positions, états, progression), testé
- `core/ChannelName.kt` : découpage des noms fournisseur, testé
- Le package `com.ontelevis` est provisoire : l'aligner sur le `namespace` réel.

## 1. Principes

- Sombre, légèrement bleuté. Hiérarchie par la typo et les nuances de gris, séparateurs de 1 dp, pas de cadres blancs.
- **Le focus est l'état principal.** L'élément focalisé prend un fond clair `#ECEDEF`, un texte foncé `#0A0B0E`, un léger agrandissement (1,02 à 1,06) et une ombre. On doit le repérer instantanément à 3 m.
- **Sélection ≠ focus.** La sélection (catégorie active, item du rail actif, chaîne en lecture) utilise l'ambre `#F2A93B`. Un élément peut être sélectionné et focalisé : fond clair et point ambre foncé `#B8741A`.
- Un seul accent : l'ambre, pour la sélection, la ligne « maintenant », le bouton principal au repos et la barre de progression.
- Le rouge `#FF5A4E` sert uniquement à la pastille « EN DIRECT » (`#E5372B` sur fond clair).
- Aucun emoji dans l'interface : ceux des noms fournisseur sont retirés (§5).
- Zone sûre (overscan) : 48 dp sur les côtés, 27 dp en haut et en bas. Seul le rail de navigation touche le bord gauche.

## 2. Jetons

| Jeton | Valeur | Usage |
|---|---|---|
| background | #0A0B0E | Fond, rail |
| guideBackground | #0D0E12 | Conteneur du guide |
| surface1 | #12141A | Boutons secondaires |
| surface2 | #171A21 | Item sélectionné, tuiles logo |
| surface3 | #1A1D24 | Programme en cours |
| programUpcoming | #14161C | Programme à venir |
| programPast | #0F1014 | Programme passé |
| selectedRow | #111318 | Rangée de la chaîne en lecture |
| line / lineSubtle / lineStrong | #1A1D24 / #16181E / #2A2E37 | Séparateurs, contours d'étiquettes |
| textPrimary / Secondary / Muted / Tertiary | #ECEDEF / #C4C8D0 / #A2A7B3 / #7D8390 | Tertiaire = contraste minimum autorisé |
| textNav | #8B909C | Icônes inactives du rail |
| focusSurface / onFocus / onFocusMuted | #ECEDEF / #0A0B0E / #3A3F4A | Élément focalisé |
| accent / accentOnFocus / onAccent | #F2A93B / #B8741A / #1A1206 | Sélection |
| live / liveOnFocus | #FF5A4E / #E5372B | Pastille en direct |

Rayons (dp) : étiquette 5, item 8, tuile 9, bouton et bloc 10, rail 12, carte et guide 14.

## 3. Typographie

Inter (corps) et Inter Tight (titres, horloge), embarquées dans `res/font` (voir le README de ce dossier). Pas de polices téléchargeables : les boîtiers TV démarrent parfois hors ligne.

| Style | Police | sp | Usage |
|---|---|---|---|
| display | Inter Tight 600 | 28 | Titre de la chaîne dans le panneau d'info |
| title | Inter Tight 600 | 22 | « Télé » |
| clock | Inter Tight 600, tabulaire | 20 | Horloge |
| item | Inter 400 | 14 | Items de liste, fil d'Ariane |
| itemStrong | Inter 600 | 13 | Nom de chaîne, titre de programme |
| body | Inter 400, interligne 19 | 13 | Description |
| meta | Inter 400 | 11 | « Relax · HDR », sous-titres |
| time | Inter 500, tabulaire | 11 | Graduations, numéros de chaîne |
| sectionLabel | Inter 600, +8 %, capitales | 11 | RACCOURCIS, CATÉGORIES |
| tag | Inter 600, +4 % | 10 | 4K, HDR, EN DIRECT |

11 sp est le minimum à distance de canapé. Les heures et numéros utilisent `fontFeatureSettings = "tnum"`.

## 4. Écran Télé

```
Row (fillMaxSize, background)
├── NavRail             72 dp, bordure droite 1 dp
├── CategoryPanel       208 dp, bordure droite, LazyColumn
└── Column (padding start 24, end 48, top/bottom 27, spacedBy 12)
    ├── TopBar          32 dp : fil d'Ariane · horloge + date
    ├── NowPlaying      190 dp : Player 338 × 190 (16:9) · espace 24 · InfoPanel (reste)
    └── EpgGuide        242 dp : en-tête 36 + 4 rangées de 51, rayon 14
```

### Rail de navigation
- Replié par défaut : icônes de 24 dp dans des zones de 48 × 44, rayon 12. Logo : carré ambre de 40 dp, rayon 12, « O » en Inter Tight 700 / 20.
- Au focus, le rail se déploie à 240 dp avec les libellés (`NavigationDrawer` de tv-material, ou `ModalNavigationDrawer`). Le contenu ne bouge pas : le tiroir passe par-dessus avec un voile.
- Groupes séparés par un trait de 28 × 1 dp : [Accueil, Recherche, Multivue, Sports] · [Télé, Films, Séries, Médias] · [Mise à jour du contenu]. Paramètres en bas.
- Item actif : icône ambre sur fond ambre à 12 %. Item focalisé : fond clair, icône foncée.
- Icônes : un set à trait fin cohérent (Lucide ou Phosphor en vector drawables, trait 1,6). Ne pas mélanger avec les Material Icons pleins.

### Panneau Catégories
- Titre « Télé » (title), puis RACCOURCIS (Favoris, Historique avec icônes de 16 dp) et CATÉGORIES.
- `OntListItem` : hauteur 36, padding 10, rayon 8. Espace de 6 dp réservé au point de sélection pour que le texte ne bouge jamais.
- Étiquette compacte à droite (4K, PPV) quand elle est déduite du nom.
- La liste défile : la catégorie focalisée reste à au moins 2 items du bord (`BringIntoViewSpec` ou défilement manuel avec marge).

### Barre du haut
- Fil d'Ariane « Télé › Relaxation » (item, dernier segment en textPrimary 500).
- À droite : horloge (clock) et date « sam. 7 nov. » (meta), via `DateTimeFormatter` en locale `fr-CA`.
- Pas de champ de recherche : la recherche est une destination du rail.

### Lecteur (aperçu)
- 338 × 190 dp, rayon 14, bordure 1 dp line, fond `#111318`.
- En haut à gauche : `LiveBadge(overVideo = true)` + étiquette qualité.
- En bas : scrim noir 92 % → 0 sur 36 dp, titre (itemStrong 15) et sous-titre (meta).
- Pas de boutons de contrôle sur l'aperçu. OK sur l'aperçu ou sur « Plein écran » ouvre le lecteur plein écran, dont les contrôles se masquent après 3 s.

### Panneau d'info
- Tuile logo 48 dp + groupe (meta) + titre (display).
- Étiquettes : qualité, 24/7, catégorie.
- Description (body) : titre du programme en cours en gras + résumé. Pour une chaîne sans EPG : « **Diffusion continue.** Cette chaîne ne fournit pas de grille horaire, le flux joue en boucle. »
- En bas : `OntPrimaryButton` « Plein écran » (icône lecture) + deux `OntIconButton` de 40 dp (Favori, Multivue).

### Guide (EPG)
- Colonne chaînes de 220 dp : numéro (time, 22 dp de large), tuile logo de 34 dp, nom (itemStrong) + « Groupe · Qualité » (meta).
- Piste : fenêtre de 2 h via `EpgWindow.around(now)` (10:50 → 10:00 à 12:00), graduations aux 30 min avec un trait de 1 dp.
- Blocs : marge 6 dp verticale, 8 dp horizontale. Position et largeur avec `EpgWindow.spanOf(start, end)`.
  - Passé : programPast, texte tertiaire.
  - En cours : surface3, barre de progression ambre de 3 dp (`progressOf`), sous-titre « reste N min » (`minutesLeft`).
  - À venir : programUpcoming, bordure lineSubtle.
  - En lecture : pastille rouge devant le titre ; la rangée entière prend selectedRow.
  - Focalisé : fond clair, texte foncé, échelle 1,02, ombre.
- **Chaîne sans EPG** : un seul bloc pleine largeur « Diffusion continue » / « 24/7, sans grille horaire ». Jamais « Aucun horaire disponible ».
- Ligne « maintenant » : trait ambre de 2 dp, pastille horaire en haut (time 700 sur ambre). `zIndex(1f)` : au-dessus des rangées, sous le bloc focalisé (`zIndex(2f)`). Mise à jour chaque minute (`LaunchedEffect` avec un délai jusqu'à la minute suivante).
- Défilement vertical commun aux deux colonnes : un seul `LazyColumn` dont chaque item est une rangée complète (cellule chaîne + piste). C'est plus simple et plus fiable pour le focus que deux listes synchronisées.

## 5. Noms de chaînes et de catégories

`core/ChannelName.kt` → `parseChannelName(raw)` :
- retire les emojis ;
- sépare le groupe avant `:` ou `|` (« Relax : Night Lights » → groupe « Relax ») ;
- extrait les suffixes de qualité (4K, UHD, FHD, HDR, HD, SD, PPV) en étiquettes, sans jamais vider le titre (« 4K UHD » reste tel quel) ;
- fournit `initials` pour la tuile logo de remplacement.

L'utiliser partout où un nom fournisseur est affiché. Garder le nom brut pour la recherche.

## 6. Navigation au D-pad

- Ordre : rail → catégories → panneau d'info (boutons) → guide. L'aperçu vidéo est focalisable (OK = plein écran).
- Droite depuis une catégorie : premier bloc de la chaîne en lecture dans le guide (`FocusRequester`).
- Haut depuis la première rangée du guide : bouton « Plein écran ».
- Gauche depuis le premier bloc d'une rangée : catégorie sélectionnée (pas le rail).
- Retour : remonte d'un niveau (guide → catégories → rail) avant de quitter l'écran.
- Restaurer le focus sur l'élément précédent au retour d'un écran (`focusRestorer` / `FocusRequester` mémorisé).
- Chaque élément focalisable vient de `OntComponents.kt` ou reproduit son traitement (fond clair, échelle, 150 ms).

## 7. Hors périmètre de cette passe

Accueil, Films, Séries, Médias, Multivue et Sports n'ont pas encore de maquette : leur appliquer seulement le thème, le rail et les composants, sans changer leur structure. L'assistant vocal de l'ancienne version est retiré.
