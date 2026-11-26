#!/usr/bin/env python3
"""Download, SHA-256 verify, and extract pinned OpenVPN 3 native dependencies."""

import hashlib
import json
import os
import shutil
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEPS_DIR = os.path.join(ROOT, "third_party", "openvpn3")
LOCK_PATH = os.path.join(DEPS_DIR, "source-lock.json")


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest().lower()


def download(url: str, dest: str) -> None:
    tmp = dest + ".part"
    req = urllib.request.Request(url, headers={"User-Agent": "QobearVPN-DepFetcher/1.0"})
    with urllib.request.urlopen(req, timeout=120) as resp, open(tmp, "wb") as out:
        shutil.copyfileobj(resp, out)
    os.replace(tmp, dest)


def extract_zip(archive_path: str, dest_dir: str) -> None:
    with zipfile.ZipFile(archive_path, "r") as zf:
        for member in zf.infolist():
            norm = os.path.normpath(member.filename)
            if norm.startswith("..") or os.path.isabs(norm):
                raise RuntimeError(f"Unsafe zip entry path: {member.filename}")
        zf.extractall(dest_dir)


def main() -> int:
    with open(LOCK_PATH, "r", encoding="utf-8") as f:
        lock = json.load(f)

    for src in lock["sources"]:
        name = src["name"]
        url = src["url"]
        expected = src["sha256"].lower()
        archive_path = os.path.join(DEPS_DIR, src["archive"])
        target_dir = os.path.join(DEPS_DIR, src["dir"])

        if os.path.exists(archive_path):
            actual = sha256_file(archive_path)
            if actual != expected:
                print(f"[{name}] Cached archive checksum mismatch, re-downloading...")
                os.remove(archive_path)

        if not os.path.exists(archive_path):
            print(f"[{name}] Downloading {url} ...")
            download(url, archive_path)
            actual = sha256_file(archive_path)
            if actual != expected:
                os.remove(archive_path)
                raise RuntimeError(f"[{name}] SHA-256 mismatch: expected {expected}, got {actual}")
        else:
            print(f"[{name}] Verified cached archive ({expected[:12]}...)")

        extract_into = src.get("extractInto")
        if extract_into:
            dest_subdir = os.path.join(DEPS_DIR, extract_into)
            marker = os.path.join(dest_subdir, "CMakeLists.txt")
            if not os.path.exists(marker):
                if os.path.exists(target_dir):
                    shutil.rmtree(target_dir)
                extract_zip(archive_path, DEPS_DIR)
                if os.path.exists(dest_subdir):
                    shutil.rmtree(dest_subdir)
                shutil.move(target_dir, dest_subdir)
                print(f"[{name}] Installed into {extract_into}")
        else:
            if not os.path.exists(target_dir):
                extract_zip(archive_path, DEPS_DIR)
                print(f"[{name}] Extracted to {src['dir']}")

    # Sanity-check expected source markers
    core_cpp = os.path.join(DEPS_DIR, f"openvpn3-{lock['coreCommit']}", "client", "ovpncli.cpp")
    framework_cmake = os.path.join(DEPS_DIR, "mbedtls-mbedtls-3.6.7", "framework", "CMakeLists.txt")
    asio_hpp = os.path.join(DEPS_DIR, "asio-asio-1-30-2", "asio", "include", "asio.hpp")
    lz4_c = os.path.join(DEPS_DIR, "lz4-1.10.0", "lib", "lz4.c")
    fmt_cmake = os.path.join(DEPS_DIR, "fmt-11.1.4", "CMakeLists.txt")

    for p in (core_cpp, framework_cmake, asio_hpp, lz4_c, fmt_cmake):
        if not os.path.exists(p):
            raise RuntimeError(f"Missing required native file after extraction: {p}")

    print("All OpenVPN 3 native dependencies verified and ready.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
