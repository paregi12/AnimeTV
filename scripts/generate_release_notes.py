#!/usr/bin/env python3
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from update_server_json import get_changelog

def main():
    clean_ver = os.environ.get("CLEAN_VER", "1.0.0")
    new_tag = os.environ.get("NEW_TAG", f"v{clean_ver}")
    notes = get_changelog(clean_ver)

    content = f"""### AnimeTV {new_tag}

#### Changelog:
{notes}

---
- **AFTV Downloader Code:** `1600676`
- **Direct Download:** [animetv.apk](https://github.com/paregi12/AnimeTV/releases/latest/download/animetv.apk)
"""
    out_dir = "release-artifacts"
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "release_notes.md")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(content.strip())
    print(f"Generated {out_path}:\n{content.strip()}")

if __name__ == "__main__":
    main()
