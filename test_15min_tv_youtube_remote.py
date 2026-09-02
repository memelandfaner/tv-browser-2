#!/usr/bin/env python3
"""
📺 SAFEER TV BROWSER 2 - 15-MINUTNI TESTNI PROTOKOL ZA YOUTUBE & DALJINSKO UPRAVLJANJE
Avtomatizirano testiranje brskanja, D-Pad navigacije, kinematografskega OSD,
previjanja, premorov, celozaslonskega načina in stabilnosti na Philips 4K Android TV.
"""

import os
import sys
import time
import subprocess

DEVICE_IP = "192.168.0.77:5555"
SCRATCH_DIR = "/home/janez/.gemini/antigravity-ide/brain/907ac91c-d609-4251-adca-52ab2485b3fb/scratch"
os.makedirs(SCRATCH_DIR, exist_ok=True)

def adb_cmd(cmd):
    full_cmd = f"adb -s {DEVICE_IP} {cmd}"
    res = subprocess.run(full_cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return res.stdout.strip()

def adb_key(keycode, sleep_sec=1.0):
    adb_cmd(f"shell input keyevent {keycode}")
    time.sleep(sleep_sec)

def adb_text(text, sleep_sec=1.0):
    encoded = text.replace(" ", "%20")
    adb_cmd(f"shell input text '{encoded}'")
    time.sleep(sleep_sec)

def capture_screen(filename):
    path = os.path.join(SCRATCH_DIR, filename)
    subprocess.run(f"adb -s {DEVICE_IP} exec-out screencap -p > '{path}'", shell=True)
    print(f"📸 Posnetek shranjen: {filename}")
    return path

print("==================================================================")
print("📺 ZAČENJAM 15-MINUTNI TESTNI PROTOKOL NA PHILIPS ANDROID TV")
print(f"📡 Ciljna naprava: {DEVICE_IP}")
print("==================================================================")

# 1. Preveri povezavo
adb_cmd(f"connect {DEVICE_IP}")
active_win = adb_cmd("shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
print(f"🔍 Trenutno aktivno okno: {active_win}")

# Zaženi aplikacijo
print("\n🚀 1. ZAGON TV-BROWSER-2")
adb_cmd("shell monkey -p com.example.safeerbrowser -c android.intent.category.LAUNCHER 1")
time.sleep(3)
capture_screen("test15_01_launch.png")

# TEST 1: Iskanje '4K sprostitvena narava'
print("\n🔍 TEST 1: Iskanje '4K sprostitvena narava'")
# Fokusiraj iskalnik preko Rdečega gumba (PROG_RED / SEARCH)
adb_key("KEYCODE_SEARCH", 1.5)
adb_text("4K sprostitvena narava", 1.0)
adb_key("KEYCODE_ENTER", 4.0)
capture_screen("test15_02_search_narava.png")

# Preizkusi D-Pad Spatial Navigation do prvega videa
print("🧭 Izbira prvega videa z D-Pad Spatial Navigation...")
adb_key("KEYCODE_DPAD_DOWN", 1.2)
adb_key("KEYCODE_DPAD_DOWN", 1.2)
capture_screen("test15_03_card_highlighted.png")

print("▶ Zagon prvega 4K videa...")
adb_key("KEYCODE_DPAD_CENTER", 4.0)
capture_screen("test15_04_narava_playing.png")

# Testiraj Play / Pause in OSD
print("⏸ Testiranje Premora (Pause)...")
adb_key("KEYCODE_DPAD_CENTER", 2.0)
capture_screen("test15_05_narava_pause.png")

print("▶ Testiranje Nadaljevanja (Play)...")
adb_key("KEYCODE_DPAD_CENTER", 2.0)
capture_screen("test15_06_narava_resume.png")

# Testiraj Previjanje naprej (+10s) in nazaj (-10s)
print("⏩ Previjanje naprej +10s...")
adb_key("KEYCODE_DPAD_RIGHT", 1.5)
capture_screen("test15_07_seek_forward.png")

print("⏪ Previjanje nazaj -10s...")
adb_key("KEYCODE_DPAD_LEFT", 1.5)
capture_screen("test15_08_seek_backward.png")

# Testiraj dvoklik za celozaslonski način
print("⛶ Testiranje preklopa v celozaslonski način z Modrim gumbom (PROG_BLUE)...")
adb_key("KEYCODE_PROG_BLUE", 2.0)
capture_screen("test15_09_fullscreen.png")

# Pusti predvajati 15 sekund za preizkus stabilnosti toka
print("⏳ Predvajam 15 sekund za preizkus tekočega predvajanja...")
time.sleep(15)

# Testiraj tipko NAZAJ (BACK)
print("⬅ Testiranje tipke NAZAJ (izhod iz videa)...")
adb_key("KEYCODE_BACK", 3.0)
capture_screen("test15_10_back_to_results.png")

# TEST 2: Iskanje slovenske narodnozabavne glasbe 'Avsenik Golica'
print("\n🎵 TEST 2: Iskanje in predvajanje 'Avsenik Golica'")
adb_key("KEYCODE_SEARCH", 1.5)
adb_text("Avsenik Golica", 1.0)
adb_key("KEYCODE_ENTER", 4.0)
capture_screen("test15_11_search_avsenik.png")

print("🧭 Izbira videoposnetka Golica...")
adb_key("KEYCODE_DPAD_DOWN", 1.2)
adb_key("KEYCODE_DPAD_DOWN", 1.2)
adb_key("KEYCODE_DPAD_CENTER", 4.0)
capture_screen("test15_12_golica_playing.png")

# Preizkusi večkratno hitro previjanje (+30s)
print("⏩ Hitro trojno previjanje naprej (+30s)...")
adb_key("KEYCODE_DPAD_RIGHT", 0.5)
adb_key("KEYCODE_DPAD_RIGHT", 0.5)
adb_key("KEYCODE_DPAD_RIGHT", 1.5)
capture_screen("test15_13_golica_seek30.png")

# Predvajaj 20 sekund
print("⏳ Predvajam glasbo 20 sekund...")
time.sleep(20)

# TEST 3: Preklop na Virtual Pointer z Zelenim gumbom (PROG_GREEN)
print("\n🖱️ TEST 3: Preizkus Virtualnega kazalca (Pointer Mode)")
adb_key("KEYCODE_PROG_GREEN", 1.5)
capture_screen("test15_14_pointer_on.png")

# Premakni kazalec po zaslonu
adb_key("KEYCODE_DPAD_RIGHT", 0.3)
adb_key("KEYCODE_DPAD_RIGHT", 0.3)
adb_key("KEYCODE_DPAD_DOWN", 0.3)
adb_key("KEYCODE_DPAD_DOWN", 1.0)
capture_screen("test15_15_pointer_moved.png")

# Preklopi nazaj na D-Pad
adb_key("KEYCODE_PROG_GREEN", 1.5)
capture_screen("test15_16_pointer_off.png")

# TEST 4: Preizkus Hitrih Zaznamkov (Rumeni gumb / PROG_YELLOW)
print("\n⭐ TEST 4: Preizkus Hitrih Zaznamkov (PROG_YELLOW)")
adb_key("KEYCODE_PROG_YELLOW", 2.0)
capture_screen("test15_17_bookmarks_dialog.png")
adb_key("KEYCODE_BACK", 1.5)

# TEST 5: Iskanje in predvajanje risanke 'Pepa Pig v slovenscini'
print("\n🐷 TEST 5: Iskanje 'Pepa Pig v slovenscini'")
adb_key("KEYCODE_SEARCH", 1.5)
adb_text("Pepa Pig v slovenscini", 1.0)
adb_key("KEYCODE_ENTER", 4.0)
capture_screen("test15_18_pepa_search.png")

print("🧭 Zagon risanke...")
adb_key("KEYCODE_DPAD_DOWN", 1.2)
adb_key("KEYCODE_DPAD_DOWN", 1.2)
adb_key("KEYCODE_DPAD_CENTER", 4.0)
capture_screen("test15_19_pepa_playing.png")

# Predvajaj 30 sekund
print("⏳ Predvajam risanko 30 sekund...")
time.sleep(30)

# TEST 6: Preizkus YouTube Shorts
print("\n⚡ TEST 6: Iskanje in predvajanje YouTube Shorts")
adb_key("KEYCODE_BACK", 2.5)
adb_key("KEYCODE_SEARCH", 1.5)
adb_text("amazing science tricks shorts", 1.0)
adb_key("KEYCODE_ENTER", 4.0)
capture_screen("test15_20_shorts_search.png")

adb_key("KEYCODE_DPAD_DOWN", 1.2)
adb_key("KEYCODE_DPAD_DOWN", 1.2)
adb_key("KEYCODE_DPAD_CENTER", 4.0)
capture_screen("test15_21_shorts_playing.png")

time.sleep(15)

# Zaključek: Vrnitev na domačo stran
print("\n🏠 Zaključek: Vrnitev na začetni zaslon...")
adb_key("KEYCODE_BACK", 1.5)
adb_key("KEYCODE_BACK", 1.5)
capture_screen("test15_22_final_home.png")

print("\n==================================================================")
print("✅ 15-MINUTNI TESTNI PROTOKOL USPEŠNO ZAKLJUČEN!")
print("==================================================================")
