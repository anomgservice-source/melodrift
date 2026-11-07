
import cv2
import os

print("=== PHOTO TO PENCIL SKETCH ===")

image_path = input("Chemin de la photo : ")

image = cv2.imread(image_path)

if image is None:
    print("Erreur : image introuvable.")
    exit()

# Conversion en niveaux de gris
gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

# Inversion
inverted = 255 - gray

# Flou
blurred = cv2.GaussianBlur(inverted, (21, 21), 0)

# Création du dessin
inverted_blur = 255 - blurred
sketch = cv2.divide(gray, inverted_blur, scale=256.0)

# Enregistrement
output = os.path.expanduser(
    "~/storage/shared/Pictures/pencil_sketch.png"
)

os.makedirs(os.path.dirname(output), exist_ok=True)
cv2.imwrite(output, sketch)

print("Dessin créé avec succès !")
print("Fichier :", output)
