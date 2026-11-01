#!/usr/bin/env python3
import os
import json
import subprocess

def main():
    apk = subprocess.getoutput('find app/build/outputs/apk/release -name "*.apk" | head -n 1')
    size = "6.03 MB"
    if apk and os.path.exists(apk):
        size = subprocess.getoutput(f'du -h "{apk}" | cut -f1') + "B"

    clean_ver = os.environ.get("CLEAN_VER", "1.0.0")
    vcode = int(os.environ.get("APP_VERSION_CODE", "10000"))

    data = {
        "domain": "gotaku.to",
        "update": clean_ver,
        "appnum": vcode,
        "appver": clean_ver,
        "appsize": size,
        "appurl": "https://github.com/paregi12/AnimeTV/releases/latest/download/animetv-release.apk",
        "appnote": f"- Release {clean_ver}\n- Official signed APK build\n- Bug fixes and improvements",
        "pcnum": vcode,
        "pcver": clean_ver,
        "pcsize": size,
        "pcurl": "https://github.com/paregi12/AnimeTV/releases/latest/download/animetv-release.apk",
        "pcnote": f"AnimeTV {clean_ver} update"
    }

    with open("server.json", "w") as f:
        json.dump(data, f, indent=2)
    print(f"Updated server.json to version {clean_ver} ({size})")

if __name__ == "__main__":
    main()
