#!/usr/bin/env python3
"""Genere les images du banc OCR : des etiquettes synthetiques dont on connait la valeur.

Aucune photo reelle n'est disponible hors du telephone : chaque image imite une vue de
camera tenue en portrait (720 x 1280, comme l'analyse de l'application), avec une
etiquette posee sur un fond de carcasse, un numero a lire et du texte parasite autour.
Quatre niveaux de difficulte (net, moyen, difficile, limite) degradent la taille du
texte, l'angle, le flou, le bruit, le reflet, le contraste et la compression.

Tout est deterministe (graine fixe) : deux executions donnent les memes images, a
version de Pillow egale. Les polices sont celles du depot, pour ne dependre d'aucune
police du systeme.

Usage : generate_samples.py [dossier_de_sortie]
"""

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "app" / "src" / "main" / "res" / "font"
SANS = FONT_DIR / "source_sans_3.ttf"
MONO = FONT_DIR / "source_code_pro.ttf"

SEED = 20261109
W, H = 720, 1280
# Rendu deux fois plus grand que l'image 720p : les sorties 540p, 720p et 1080p en sont
# toutes des reductions, comme un meme capteur lu a trois resolutions.
SS = 2
OUTPUTS = {"540": 0.75, "720": 1.0, "1080": 1.5}

# Sans I, O ni Q : comme sur la plupart des numeros de serie, pour que le banc mesure
# les moteurs et non l'ambiguite de glyphes que personne ne distingue.
LETTERS = "ABCDEFGHJKLMNPRSTUVWXYZ"

FORMATS = [
    {
        "id": "hk416",
        "pattern": r"\b\d{2}-\d{6}\b",
        "make": lambda r: f"{r.randint(10, 99)}-{r.randint(0, 999999):06d}",
        "header": ["HK DEFENCE", "ARSENAL NORD", "WEAPON SYSTEMS"],
        "lines": ["CAL 5.56x45", "MODEL A5", "MADE IN GERMANY"],
    },
    {
        "id": "arva",
        "pattern": r"\b\d{5}\b",
        "make": lambda r: f"{r.randint(10000, 99999)}",
        "header": ["ALPIN SAFETY", "SNOW RESCUE", "MONTAGNE PRO"],
        "lines": ["457 kHz", "TYPE DVA", "IP67"],
    },
    {
        "id": "bj",
        "pattern": r"[A-Z]{2}-\d{3}[A-Z]/\d{5}",
        "make": lambda r: (
            f"{r.choice(LETTERS)}{r.choice(LETTERS)}-{r.randint(100, 999)}"
            f"{r.choice(LETTERS)}/{r.randint(0, 99999):05d}"
        ),
        "header": ["BOITIER JONCTION", "RADIO TACTIQUE", "ELEC SERVICE"],
        "lines": ["24 V DC", "LOT 0417", "FABRIQUE EN FRANCE"],
    },
    {
        "id": "1gh",
        "pattern": r"\b\d[A-Z]{2}\d{3}\b",
        "make": lambda r: (
            f"{r.randint(1, 9)}{r.choice(LETTERS)}{r.choice(LETTERS)}{r.randint(0, 999):03d}"
        ),
        "header": ["NORDIC POWER", "BATTERY PACK", "ENERGIE MOBILE"],
        "lines": ["Li-ion 14.4V 5200mAh", "RECHARGEABLE", "NE PAS OUVRIR"],
    },
]

TIERS = [
    {"id": "net", "serial": 40, "rot": 2.0, "persp": 0.00, "blur": 0.5, "noise": 2.5,
     "jpeg": 92, "glare": 0.00, "ink": 0},
    {"id": "moyen", "serial": 32, "rot": 5.0, "persp": 0.04, "blur": 1.0, "noise": 6.0,
     "jpeg": 80, "glare": 0.28, "ink": 40},
    {"id": "difficile", "serial": 26, "rot": 9.0, "persp": 0.08, "blur": 1.5, "noise": 10.0,
     "jpeg": 62, "glare": 0.45, "ink": 80},
    # A la limite de ce qu'un oeil lit encore : c'est la que les moteurs se departagent.
    {"id": "limite", "serial": 23, "rot": 12.0, "persp": 0.10, "blur": 1.7, "noise": 12.0,
     "jpeg": 55, "glare": 0.50, "ink": 90},
]
PER_TIER = 8  # deux images par format et par niveau

BACKGROUNDS = [(38, 44, 40), (52, 54, 58), (28, 28, 30), (70, 74, 66), (88, 90, 94)]
LABELS = [(250, 250, 246), (244, 238, 205), (226, 228, 230)]
PREFIXES = ["S/N", "SN:", "N°", "SER."]


def font(path, size, weight):
    f = ImageFont.truetype(str(path), int(round(size * SS)))
    f.set_variation_by_axes([weight])
    return f


def solve(matrix, vector):
    """Elimination de Gauss, pour ne pas dependre de numpy."""
    n = len(vector)
    m = [row[:] + [vector[i]] for i, row in enumerate(matrix)]
    for col in range(n):
        pivot = max(range(col, n), key=lambda r: abs(m[r][col]))
        m[col], m[pivot] = m[pivot], m[col]
        for row in range(n):
            if row != col:
                factor = m[row][col] / m[col][col]
                for k in range(col, n + 1):
                    m[row][k] -= factor * m[col][k]
    return [m[i][n] / m[i][i] for i in range(n)]


def perspective_coeffs(frame_quad, label_quad):
    """Coefficients de Image.transform(PERSPECTIVE) : point de l'image -> point de l'etiquette."""
    matrix, vector = [], []
    for (x, y), (u, v) in zip(frame_quad, label_quad):
        matrix.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        matrix.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        vector += [u, v]
    return solve(matrix, vector)


def uniform_noise(rng, size, sigma):
    """Bruit a peu pres gaussien (moyenne de trois tirages uniformes), centre sur 128."""
    layers = [Image.frombytes("L", size, rng.randbytes(size[0] * size[1])) for _ in range(3)]
    mean = ImageChops.add(ImageChops.add(layers[0], layers[1], scale=2), layers[2], scale=1.5)
    gain = sigma / 42.5  # ecart type de la moyenne de trois lois uniformes sur 0..255
    return mean.point(lambda v: max(0, min(255, int(round(128 + (v - 127.5) * gain)))))


def background(rng):
    base = rng.choice(BACKGROUNDS)
    size = (W * SS, H * SS)
    image = Image.new("RGB", size, base)
    # Taches larges : une carcasse n'est jamais eclairee de facon uniforme.
    blotch = Image.frombytes("L", (9, 16), rng.randbytes(9 * 16)).resize(size, Image.Resampling.BICUBIC)
    shade = blotch.point(lambda v: 108 + v * 40 // 255)
    image = ImageChops.multiply(image, Image.merge("RGB", [shade] * 3).point(lambda v: min(255, v * 2)))
    draw = ImageDraw.Draw(image)
    for _ in range(rng.randint(2, 5)):  # nervures de la carcasse
        y = rng.randint(0, size[1])
        tone = tuple(max(0, c - rng.randint(10, 22)) for c in base)
        draw.line([(0, y), (size[0], y + rng.randint(-30, 30))], fill=tone, width=rng.randint(4, 10))
    return image


def draw_label(rng, fmt, tier, value):
    """Dessine l'etiquette a plat, a l'echelle SS ; renvoie l'image RGBA."""
    paper = rng.choice(LABELS)
    ink = (tier["ink"],) * 3
    width = rng.randint(500, 610) * SS
    pad = 26 * SS
    header = font(SANS, 30, 700)
    body = font(SANS, 22, 500)
    small = font(SANS, 18, 400)
    prefix_font = font(SANS, tier["serial"] * 0.6, 600)
    serial_font = font(MONO if rng.random() < 0.5 else SANS, tier["serial"], 600)

    lines = list(fmt["lines"])
    rng.shuffle(lines)
    prefix = rng.choice(PREFIXES)
    with_bars = rng.random() < 0.6

    scratch = ImageDraw.Draw(Image.new("RGB", (8, 8)))

    def height(text, used):
        box = scratch.textbbox((0, 0), text, font=used)
        return box[3] - box[1]

    serial_h = height("0AZ9/", serial_font)
    total = (pad + height("Ag", header) + 14 * SS + height("Ag", body) + 22 * SS + serial_h
             + 22 * SS + height("Ag", small) + pad + (56 * SS if with_bars else 0))
    label = Image.new("RGBA", (width, total), paper + (255,))
    draw = ImageDraw.Draw(label)
    draw.rounded_rectangle([2, 2, width - 3, total - 3], radius=10 * SS, outline=(120, 120, 120, 255), width=2 * SS)

    y = pad
    draw.text((pad, y), rng.choice(fmt["header"]), font=header, fill=ink, anchor="lt")
    y += height("Ag", header) + 14 * SS
    draw.text((pad, y), lines[0], font=body, fill=ink, anchor="lt")
    draw.text((width - pad, y), lines[1], font=body, fill=ink, anchor="rt")
    y += height("Ag", body) + 22 * SS

    # La valeur a lire, precedee de son prefixe : l'une et l'autre sur la meme ligne de
    # base, comme sur une etiquette imprimee.
    baseline = y + serial_h
    draw.text((pad, baseline), prefix, font=prefix_font, fill=ink, anchor="ls")
    prefix_w = scratch.textlength(prefix + " ", font=prefix_font)
    draw.text((pad + prefix_w + 6 * SS, baseline), value, font=serial_font, fill=ink, anchor="ls")
    y = baseline + 22 * SS

    draw.text((pad, y), lines[2], font=small, fill=ink, anchor="lt")
    y += height("Ag", small) + 10 * SS

    if with_bars:  # un code-barres factice : un parasite graphique, pas du texte
        x = pad
        while x < width - pad:
            bar = rng.choice([2, 3, 5, 7]) * SS // 2
            if rng.random() < 0.55:
                draw.rectangle([x, y, x + bar, y + 40 * SS], fill=ink + (255,))
            x += bar + SS
    return label


def place(rng, tier, label):
    """Pose l'etiquette dans l'image, tournee et vue legerement de biais."""
    lw, lh = label.size
    cx = W * SS / 2 + rng.uniform(-30, 30) * SS
    cy = H * SS / 2 + rng.uniform(-90, 90) * SS
    angle = math.radians(rng.uniform(-tier["rot"], tier["rot"]))
    squeeze_x = rng.uniform(0, tier["persp"])
    squeeze_y = rng.uniform(0, tier["persp"])
    corners = [(-lw / 2, -lh / 2), (lw / 2, -lh / 2), (lw / 2, lh / 2), (-lw / 2, lh / 2)]
    # Le bord droit et le bord bas raccourcissent : l'objectif n'est jamais face au plan.
    corners[1] = (corners[1][0], corners[1][1] * (1 - squeeze_x))
    corners[2] = (corners[2][0] * (1 - squeeze_y), corners[2][1] * (1 - squeeze_x))
    corners[3] = (corners[3][0] * (1 - squeeze_y), corners[3][1])
    frame_quad = [
        (cx + x * math.cos(angle) - y * math.sin(angle), cy + x * math.sin(angle) + y * math.cos(angle))
        for x, y in corners
    ]
    label_quad = [(0, 0), (lw, 0), (lw, lh), (0, lh)]
    coeffs = perspective_coeffs(frame_quad, label_quad)
    return label.transform((W * SS, H * SS), Image.Transform.PERSPECTIVE, coeffs, Image.Resampling.BICUBIC)


def add_glare(rng, tier, image):
    if tier["glare"] <= 0:
        return image
    size = (rng.randint(380, 620) * SS, rng.randint(200, 360) * SS)
    blob = ImageChops.invert(Image.radial_gradient("L")).resize(size, Image.Resampling.BICUBIC)
    # Le degrade de Pillow n'atteint le blanc que dans les coins : sans ce recalage, le
    # reflet garderait un bord rectangulaire net, qu'aucun eclairage ne produit.
    blob = blob.point(lambda v: int(max(0, v - 80) * 255 / 175 * tier["glare"]))
    mask = Image.new("L", image.size, 0)
    mask.paste(blob, (int(W * SS / 2 - size[0] / 2 + rng.uniform(-120, 120) * SS),
                      int(H * SS / 2 - size[1] / 2 + rng.uniform(-140, 140) * SS)))
    return Image.composite(Image.new("RGB", image.size, (255, 255, 255)), image, mask)


def render(rng, fmt, tier, value):
    scene = background(rng)
    placed = place(rng, tier, draw_label(rng, fmt, tier, value))
    scene.paste(placed, (0, 0), placed)
    scene = add_glare(rng, tier, scene)
    # Flou optique applique avant la reduction : il est le meme, en unites de scene,
    # aux trois resolutions de sortie.
    return scene.filter(ImageFilter.GaussianBlur(tier["blur"] * SS))


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "ocrbench" / "build" / "assets" / "ocrbench"
    out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(SEED)
    entries = []
    index = 0
    for tier in TIERS:
        for slot in range(PER_TIER):
            fmt = FORMATS[slot % len(FORMATS)]
            value = fmt["make"](rng)
            master = render(rng, fmt, tier, value)
            files = {}
            for name, scale in OUTPUTS.items():
                size = (int(W * scale), int(H * scale))
                image = master.resize(size, Image.Resampling.LANCZOS)
                noise = uniform_noise(rng, size, tier["noise"])
                image = ImageChops.add(image, Image.merge("RGB", [noise] * 3), scale=1, offset=-128)
                file_name = f"img_{index:02d}_{name}.jpg"
                image.save(out / file_name, "JPEG", quality=tier["jpeg"])
                files[name] = file_name
            entries.append({
                "id": index,
                "tier": tier["id"],
                "format": fmt["id"],
                "pattern": fmt["pattern"],
                "expected": value,
                "files": files,
            })
            index += 1
    (out / "manifest.json").write_text(json.dumps({"images": entries}, indent=1), encoding="utf-8")
    print(f"{index} etiquettes, {index * len(OUTPUTS)} images dans {out}")


if __name__ == "__main__":
    main()
