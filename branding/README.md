# Car Manager — identité A11.6

Le symbole associe un C ouvert à droite et une ligne de pavillon compacte dans son espace intérieur. Le C évoque Car et la continuité du suivi ; le pavillon suffit à donner un indice automobile sans dessiner un véhicule complet. Géométrie originale construite localement, sans référence, copie ou tracé de badge constructeur. Ce choix ne constitue pas une recherche de disponibilité de marque.

Palette fixe : pétrole **#006A62**, identique au primaire Light du produit, et blanc **#FFFFFF**. Aucun accent supplémentaire, texte, ombre, dégradé ou arrondi de masque intégré.

- `car_manager_mark.svg` : source canonique transparente, viewport 64 × 64, deux chemins remplis. Le C a une épaisseur de 8 unités, le pavillon environ 6 ; aucun trait fin ni détail dépendant d'une couleur.
- `car_manager_launcher_preview.svg` : source carrée opaque 512 × 512, viewport 72 × 72 correspondant au cadre visible du launcher. Même géométrie translatée de (4,4), sans masque baked-in. Convient comme master pour de futurs artworks.
- `car_manager_play_icon_512.png` : export PNG RGBA 512 × 512 entièrement opaque de cette source, sans coins arrondis ni ombre. Généré avec Sharp/librsvg déjà disponible localement ; aucune dépendance ajoutée à l'application.

Les Android VectorDrawable reprennent exactement les deux chemins. Le foreground et le monochrome ont un canvas de 108 dp et une translation (22,22). Le symbole tient dans un cercle de rayon 28 autour de (54,54), à l'intérieur de la zone sûre de rayon 33. Les resources v26 gardent les deux couches et les noms du manifest ; les variantes v33 ajoutent la couche monochrome noire sur fond transparent, teintée par le launcher.

Splash : le même symbole blanc, sans animation. Une vector intrinsèque de 192 dp, viewport 72 et translation (4,4), entourée de 48 dp d'insets natifs forme le canvas de 288 dp. Son rendu est exactement équivalent à une vector 288 dp/viewport108/translation22, tout en évitant le nouvel avertissement lint VectorRaster. Les thèmes natifs existants gardent leur rôle : layer-list centrée sur pétrole pour API26–30, fond uni et `windowSplashScreenAnimatedIcon` pour API31+. `startup_surface` reste le nom utilisé par la garde A11.0 et devient un alias du fond pétrole. Aucun délai, Activity, bibliothèque ou changement Kotlin de démarrage.

Login : symbole décoratif 24 dp avec teinte `MaterialTheme.colorScheme.primary`, à côté du titre Car Manager. Les icônes automobiles représentant des véhicules restent intactes.

Ne pas étirer le symbole, changer séparément ses deux chemins, ni le substituer aux marques Google/Play ou aux pictogrammes métier. Les simulations de masques et de petites tailles sont des preuves de rendu local ; l'apparence sur launcher, splash et appareil OEM reste à valider physiquement.

Sources Android vérifiées le 03/10/2026 : [adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [splash natif](https://developer.android.com/develop/ui/views/launch/splash-screen), [icône Google Play](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).
