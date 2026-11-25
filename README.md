# Melodrift v22 — préparation des améliorations

Cette archive est basée sur `melodrift-v21-video-features.zip`.

## Changements préparés
- Retrait de l’entrée et des écrans Melodrift Studio, ainsi que des outils qui lui étaient propres (écriture de paroles IA, mode voiture, mix intelligent, outils Studio).
- Conservation du lecteur musical, des playlists, de l’égaliseur et du visualiseur musical déjà présents.
- Menu musical enrichi : partage, visualiseur, vitesse, sonnerie, minuterie, masquer et supprimer.
- Bibliothèque vidéo présentée en liste compacte avec miniature, titre et durée.
- Lecteur vidéo avec titre, réglage de vitesse et menu d’options (partage, répétition, ajustement, masquer, supprimer).
- Lecteur paysage adapté à la rotation système : pochette circulaire animée, commandes et informations à côté ; barre de progression et temps masqués en paysage. En portrait, la barre de progression reste visible.
- Version du projet et de l’APK préparée en 22.0.
- Le service de lecture ne demande plus explicitement son arrêt quand la tâche de l’application est retirée.

## Limites à vérifier sur appareil réel
- Fenêtre flottante vidéo et sélection de piste audio : non implémentées dans cette version WebView ; elles demandent une intégration Android native spécifique.
- La continuité audio après fermeture forcée du processus Android, la session multimédia complète, les notifications et les autorisations doivent être testées sur téléphone.
- Le menu « Définir comme sonnerie » dépend de l’autorisation Android et du plugin natif déjà présent.
- L’adaptation paysage et la suppression de barre de progression sont définies en CSS ; à valider sur plusieurs tailles d’écran.

Aucune compilation APK n’a été exécutée dans cet environnement.
