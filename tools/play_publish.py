#!/usr/bin/env python3
"""Push store listing, contact details, graphics and releases to Google Play via the
Google Play Developer API (androidpublisher v3).

Everything the API supports lives under play/:
  play/details.json                  default language, contact email, website
  play/listings/<lang>.json          title, shortDescription, fullDescription, releaseNotes
  play/graphics/<lang>/icon.png                512x512
  play/graphics/<lang>/featureGraphic.png      1024x500
  play/graphics/<lang>/phoneScreenshots/*.png  2-8 images (uploaded in name order)
  play/graphics/<lang>/sevenInchScreenshots/*.png, tenInchScreenshots/*.png (optional)

Sections the API does not cover (category, privacy policy, ads, content rating,
target audience, data safety, advertising ID) are listed in docs/PLAY_CONSOLE.md.

Setup: pip install google-api-python-client google-auth
Usage:
  python tools/play_publish.py --key SA.json --listings --details --images
  python tools/play_publish.py --key SA.json --aab app/build/outputs/bundle/release/app-release.aab --track internal
  add --dry-run to validate the edit without committing it.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

PACKAGE = "tj.tajikdictionary"
ROOT = Path(__file__).resolve().parent.parent
PLAY = ROOT / "play"
SINGLE_IMAGES = ("icon", "featureGraphic")
MULTI_IMAGES = ("phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots")
IMAGE_EXTS = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg"}

LIMITS = {"title": 30, "shortDescription": 80, "fullDescription": 4000, "releaseNotes": 500}


def load_listings() -> dict[str, dict]:
    listings = {}
    for f in sorted((PLAY / "listings").glob("*.json")):
        data = json.loads(f.read_text(encoding="utf-8"))
        for field, limit in LIMITS.items():
            if len(data.get(field, "")) > limit:
                sys.exit(f"{f.name}: {field} is {len(data[field])} chars, limit {limit}")
        listings[f.stem] = data
    if not listings:
        sys.exit("No listings in play/listings/")
    return listings


def service(key: Path):
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    creds = service_account.Credentials.from_service_account_file(
        str(key), scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def push_details(edits, edit_id):
    body = json.loads((PLAY / "details.json").read_text(encoding="utf-8"))
    edits.details().update(packageName=PACKAGE, editId=edit_id, body=body).execute()
    print(f"details: {body}")


def push_listings(edits, edit_id, listings):
    for lang, data in listings.items():
        body = {k: data[k] for k in ("title", "shortDescription", "fullDescription")}
        body["language"] = lang
        edits.listings().update(packageName=PACKAGE, editId=edit_id, language=lang, body=body).execute()
        print(f"listing {lang}: {body['title']}")


def _upload(edits, edit_id, lang, image_type, path):
    from googleapiclient.http import MediaFileUpload

    media = MediaFileUpload(str(path), mimetype=IMAGE_EXTS[path.suffix.lower()])
    edits.images().upload(
        packageName=PACKAGE, editId=edit_id, language=lang, imageType=image_type, media_body=media
    ).execute()
    print(f"image {lang}/{image_type}: {path.name}")


def push_images(edits, edit_id):
    for lang_dir in sorted(p for p in (PLAY / "graphics").iterdir() if p.is_dir()):
        lang = lang_dir.name
        for image_type in SINGLE_IMAGES:
            files = [p for p in lang_dir.glob(f"{image_type}.*") if p.suffix.lower() in IMAGE_EXTS]
            if files:
                edits.images().deleteall(packageName=PACKAGE, editId=edit_id, language=lang, imageType=image_type).execute()
                _upload(edits, edit_id, lang, image_type, files[0])
        for image_type in MULTI_IMAGES:
            folder = lang_dir / image_type
            files = sorted(p for p in folder.glob("*") if p.suffix.lower() in IMAGE_EXTS) if folder.is_dir() else []
            if files:
                edits.images().deleteall(packageName=PACKAGE, editId=edit_id, language=lang, imageType=image_type).execute()
                for f in files[:8]:
                    _upload(edits, edit_id, lang, image_type, f)


def push_release(edits, edit_id, aab: Path, track: str, status: str, listings, user_fraction):
    from googleapiclient.http import MediaFileUpload

    media = MediaFileUpload(str(aab), mimetype="application/octet-stream", resumable=True)
    bundle = edits.bundles().upload(packageName=PACKAGE, editId=edit_id, media_body=media).execute()
    version_code = str(bundle["versionCode"])
    print(f"bundle uploaded: versionCode {version_code}")

    release = {
        "name": None,
        "versionCodes": [version_code],
        "status": status,
        "releaseNotes": [
            {"language": lang, "text": d["releaseNotes"]} for lang, d in listings.items() if d.get("releaseNotes")
        ],
    }
    if status == "inProgress":
        release["userFraction"] = user_fraction
    release = {k: v for k, v in release.items() if v is not None}
    edits.tracks().update(
        packageName=PACKAGE, editId=edit_id, track=track, body={"track": track, "releases": [release]}
    ).execute()
    print(f"track {track}: versionCode {version_code} status={status}")


def run(args, status):
    listings = load_listings()
    edits = service(args.key).edits()
    edit_id = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
    print(f"edit {edit_id}")

    if args.details:
        push_details(edits, edit_id)
    if args.listings:
        push_listings(edits, edit_id, listings)
    if args.images:
        push_images(edits, edit_id)
    if args.aab:
        push_release(edits, edit_id, args.aab, args.track, status, listings, args.user_fraction)

    if args.dry_run:
        edits.validate(packageName=PACKAGE, editId=edit_id).execute()
        edits.delete(packageName=PACKAGE, editId=edit_id).execute()
        print("dry run: edit validated and discarded")
    else:
        edits.commit(packageName=PACKAGE, editId=edit_id).execute()
        print("committed")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--key", type=Path, required=True, help="service-account JSON key (keep it out of the repo)")
    ap.add_argument("--details", action="store_true", help="push play/details.json")
    ap.add_argument("--listings", action="store_true", help="push play/listings/*.json")
    ap.add_argument("--images", action="store_true", help="push play/graphics/<lang>/...")
    ap.add_argument("--aab", type=Path, help="upload this bundle and release it to --track")
    ap.add_argument("--track", default="internal", help="internal | alpha (closed) | beta (open) | production")
    ap.add_argument("--status", default="completed", choices=["draft", "completed", "inProgress", "halted"])
    ap.add_argument("--user-fraction", type=float, default=0.1, help="rollout fraction for --status inProgress")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    if not (args.details or args.listings or args.images or args.aab):
        ap.error("nothing to do: pass --details, --listings, --images and/or --aab")
    if args.aab and not args.aab.is_file():
        ap.error(f"bundle not found: {args.aab}")

    from googleapiclient.errors import HttpError

    try:
        run(args, args.status)
    except HttpError as e:
        msg = str(e)
        if args.aab and args.status != "draft" and "draft app" in msg:
            print("App has never been published: retrying with status=draft (finish with 'Review release' in Play Console)")
            run(args, "draft")
        else:
            sys.exit(f"Play API error: {msg}")


if __name__ == "__main__":
    main()
