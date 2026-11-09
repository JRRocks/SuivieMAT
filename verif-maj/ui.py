#!/usr/bin/env python3
"""Pilote l'ecran de l'emulateur par adb : lire les textes affiches, appuyer sur l'un d'eux.

Volontairement sans dependance : `uiautomator dump` rend l'arbre de l'ecran en XML, les
textes Compose y figurent comme ceux des vues classiques.
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True).stdout


def dump():
    """Arbre de la fenetre active, ou None si l'ecran change pendant la capture."""
    adb("shell", "uiautomator", "dump", "/sdcard/verif-ui.xml", check=False)
    xml = adb("exec-out", "cat", "/sdcard/verif-ui.xml", check=False)
    try:
        return ET.fromstring(xml)
    except ET.ParseError:
        return None


def nodes(root):
    return [] if root is None else [n for n in root.iter("node") if n.get("text")]


def texts():
    return [n.get("text") for n in nodes(dump())]


def find(label, exact=True):
    for node in nodes(dump()):
        text = node.get("text")
        if text == label or (not exact and label in text):
            return node
    return None


def wait_for(label, timeout=30, exact=True):
    """Attend qu'un texte apparaisse ; renvoie son noeud, ou None a l'expiration."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        node = find(label, exact)
        if node is not None:
            return node
        time.sleep(1)
    return None


def tap(node):
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def tap_text(label, timeout=30, exact=True):
    node = wait_for(label, timeout, exact)
    if node is None:
        fail(f"« {label} » n'est pas a l'ecran")
    tap(node)


def show(title):
    print(f"--- {title} : {texts()}", flush=True)


def fail(message):
    print(f"ECHEC : {message}", flush=True)
    print(f"Ecran au moment de l'echec : {texts()}", flush=True)
    sys.exit(1)
