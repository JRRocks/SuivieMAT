#!/usr/bin/env python3
"""Deroule la mise a jour integree de bout en bout sur l'emulateur que voit adb.

L'application installee est une « version 0.1 » (ancienne-version.init.gradle) : la
derniere release GitHub du depot est donc une mise a jour pour elle. Le script suit le
parcours d'un utilisateur — proposition au lancement, « Plus tard », recherche a la
demande, telechargement, confirmation d'Android — et controle a la fin que la version
installee est bien celle de la release, puis qu'elle se dit a jour.

Usage : verifier.py <apk de l'ancienne version>
"""

import json
import sys
import time
import urllib.request

from ui import adb, fail, find, show, tap_text, texts, wait_for

APP = "com.suiviemat.app"
ACTIVITY = f"{APP}/com.scanlist.app.MainActivity"
LATEST = "https://api.github.com/repos/JRRocks/SuivieMAT/releases/latest"


def installed_version():
    for line in adb("shell", "dumpsys", "package", APP).splitlines():
        if "versionName=" in line:
            return line.split("versionName=")[1].strip()
    return None


def launch():
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)


def reach_footer():
    """Le pied de l'onglet est sous les quatre modes : on fait defiler jusqu'a lui."""
    for _ in range(4):
        if find("Rechercher une mise à jour") is not None:
            return
        adb("shell", "input", "swipe", "540", "1500", "540", "600", "200")
        time.sleep(1)
    fail("le pied « Rechercher une mise à jour » est introuvable")


def main():
    apk = sys.argv[1]
    request = urllib.request.Request(LATEST, headers={"User-Agent": "verif-maj"})
    release = json.load(urllib.request.urlopen(request))
    expected = release["tag_name"].lstrip("v")
    print(f"Derniere release : {release['tag_name']}, pieces jointes : "
          f"{[(a['name'], a['size'], a.get('digest')) for a in release['assets']]}", flush=True)
    print(f"ABI de l'emulateur : {adb('shell', 'getprop', 'ro.product.cpu.abilist').strip()}", flush=True)

    # Play Protect proposerait d'analyser un APK inconnu : ce n'est pas ce qu'on verifie.
    adb("shell", "settings", "put", "global", "package_verifier_enable", "0")
    adb("shell", "settings", "put", "global", "verifier_verify_adb_installs", "0")
    adb("install", "-r", apk)
    # L'autorisation « installer des applications inconnues » est accordee d'avance :
    # son ecran appartient a Android, pas a l'application.
    adb("shell", "appops", "set", APP, "REQUEST_INSTALL_PACKAGES", "allow")
    print(f"Version installee au depart : {installed_version()}", flush=True)

    # 1. La recherche automatique du lancement propose la mise a jour.
    launch()
    if wait_for("Mise à jour disponible", timeout=60) is None:
        fail("la mise a jour n'est pas proposee au lancement")
    show("Proposition au lancement")
    if not any(expected in text for text in texts()):
        fail(f"la fenetre ne cite pas la version {expected}")

    # 2. « Plus tard » referme la fenetre, la recherche a la demande la repropose.
    tap_text("Plus tard")
    time.sleep(1)
    if find("Mise à jour disponible") is not None:
        fail("« Plus tard » n'a pas referme la fenetre")
    reach_footer()
    show("Pied de l'onglet Nouveau scan")
    tap_text("Rechercher une mise à jour")
    if wait_for("Mise à jour disponible", timeout=60) is None:
        fail("la recherche a la demande ne repropose pas la mise a jour")

    # 3. « Installer » telecharge, controle le fichier, puis ouvre l'installateur d'Android.
    tap_text("Installer")
    time.sleep(2)
    show("Pendant le telechargement")
    update = wait_for("Update", timeout=240)
    if update is None:
        fail("l'installateur d'Android ne s'est pas ouvert")
    show("Confirmation d'Android")
    tap_text("Update")

    # 4. Android remplace l'application.
    deadline = time.time() + 180
    while time.time() < deadline and installed_version() != expected:
        time.sleep(3)
    version = installed_version()
    print(f"Version installee apres la mise a jour : {version}", flush=True)
    if version != expected:
        fail(f"la version installee n'est pas {expected}")

    # 5. La version mise a jour demarre, et se dit a jour.
    time.sleep(3)
    adb("shell", "am", "force-stop", "com.google.android.packageinstaller", check=False)
    launch()
    time.sleep(5)
    if find("Mise à jour disponible") is not None:
        fail("la version a jour propose encore une mise a jour")
    reach_footer()
    show("Pied de l'onglet apres la mise a jour")
    if not any(expected in text for text in texts()):
        fail(f"le pied n'affiche pas la version {expected}")
    tap_text("Rechercher une mise à jour")
    if wait_for("SuivieMAT est à jour", timeout=60, exact=False) is None:
        fail("la version a jour ne repond pas « à jour »")
    show("Reponse de la recherche a la demande")
    print("VERIFICATION REUSSIE : mise a jour installee de bout en bout", flush=True)


if __name__ == "__main__":
    main()
