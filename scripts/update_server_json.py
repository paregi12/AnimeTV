#!/usr/bin/env python3
import json
import os
import re
import subprocess


def get_changelog(version):
    if not os.path.exists("CHANGELOGS.md"):
        return f"- Release {version}\n- Bug fixes and improvements"
    try:
        with open("CHANGELOGS.md", "r", encoding="utf-8") as f:
            content = f.read()
        pattern = rf"-\s+\*\*{re.escape(version)}[^\*]*\*\*(.*?)(?=\n-\s+\*\*|\Z)"
        match = re.search(pattern, content, re.DOTALL)
        if match:
            lines = [line.strip() for line in match.group(1).strip().splitlines() if line.strip()]
            if lines:
                return "\n".join(lines)
    except Exception:
        pass
    return f"- Release {version}\n- Bug fixes and improvements"


def find_release_apk():
    apk = subprocess.getoutput(
        'find androidApp/build/outputs/apk/release -name "*.apk" ! -name "*-unsigned.apk" | head -n 1'
    ).strip()
    if not apk:
        apk = subprocess.getoutput(
            'find androidApp/build/outputs/apk/release -name "*.apk" | head -n 1'
        ).strip()
    return apk


def main():
    apk = find_release_apk()
    size = "0.0 MB"
    if apk and os.path.exists(apk):
        size = subprocess.getoutput(f'du -h "{apk}" | cut -f1').strip() + "B"

    clean_ver = os.environ.get("CLEAN_VER", "1.0.0")
    ver_tag = os.environ.get("VER_TAG", f"v{clean_ver}")
    new_tag = os.environ.get("NEW_TAG", f"kmp-{ver_tag}")
    vcode = int(os.environ.get("APP_VERSION_CODE", "10000"))
    apk_url = f"https://github.com/paregi12/AnimeTV/releases/download/{new_tag}/animetv-{ver_tag}.apk"
    appnote = get_changelog(clean_ver)

    data = {
        "domain": "gotaku.to",
        "update": clean_ver,
        "appnum": vcode,
        "appver": clean_ver,
        "appsize": size,
        "appurl": apk_url,
        "appnote": appnote,
        "pcnum": vcode,
        "pcver": clean_ver,
        "pcsize": size,
        "pcurl": apk_url,
        "pcnote": f"AnimeTV KMP {clean_ver} update",
    }

    with open("server.json", "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(f"Updated server.json to version {clean_ver} ({size})")


if __name__ == "__main__":
    main()
