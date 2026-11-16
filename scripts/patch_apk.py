import hashlib
import re
import shutil
import struct
import sys
from pathlib import Path


INJECT_CALL = "    invoke-static {p0}, Lio/github/hassinalhadi/ninja/NinjaMod;->init(Landroid/app/Application;)V\n"


def extract_v2_cert(apk_path: Path) -> bytes | None:
    data = apk_path.read_bytes()
    eocd_off = data.rfind(b"PK\x05\x06")
    if eocd_off < 0:
        return None
    cd_offset = struct.unpack_from("<I", data, eocd_off + 16)[0]
    magic_off = cd_offset - 16
    if magic_off < 24 or data[magic_off:cd_offset] != b"APK Sig Block 42":
        return None
    block_size = struct.unpack_from("<Q", data, cd_offset - 24)[0]
    pos = cd_offset - 8 - block_size + 8
    while pos < cd_offset - 24:
        pair_len = struct.unpack_from("<Q", data, pos)[0]
        pair_id = struct.unpack_from("<I", data, pos + 8)[0]
        if pair_id in (0x7109871A, 0xF05368C0):
            val = data[pos + 12 : pos + 8 + pair_len]
            sd_len = struct.unpack_from("<I", val, 8)[0]
            sd = val[12 : 12 + sd_len]
            dig_len = struct.unpack_from("<I", sd, 0)[0]
            certs_off = 4 + dig_len
            first_cert_len = struct.unpack_from("<I", sd, certs_off + 4)[0]
            return sd[certs_off + 8 : certs_off + 8 + first_cert_len]
        pos += 8 + pair_len
    return None


def embed_original_cert(target_apk: Path, target_dir: Path) -> str | None:
    cert_bytes = extract_v2_cert(target_apk)
    if not cert_bytes:
        print("[!] Could not extract v2/v3 cert from target APK")
        return None
    assets_dir = target_dir / "assets"
    assets_dir.mkdir(parents=True, exist_ok=True)
    out_cert = assets_dir / "ninja_orig_cert.der"
    out_cert.write_bytes(cert_bytes)
    sha256 = hashlib.sha256(cert_bytes).hexdigest()
    print(f"[+] Embedded original cert ({len(cert_bytes)} bytes, SHA-256: {sha256})")
    return sha256


def _replace_method_body(method_block: str, new_body: str) -> str:
    lines = method_block.splitlines(keepends=True)
    header = lines[0]
    footer = lines[-1]
    return f"{header}{new_body}{footer}"


def _neutralize_debug_checks(text: str) -> str:
    return re.sub(
        r"invoke-static\s+\{\},\s+Landroid/os/Debug;->(?:isDebuggerConnected|waitingForDebugger)\(\)Z\s*\n\s*move-result\s+([vp]\d+)",
        r"const/4 \1, 0x0",
        text,
    )


def bypass_samurai_integrity_checks(target_dir: Path, orig_sha256: str) -> None:
    method_re = re.compile(r"(^\.method\b.*?^\.end method\s*$)", re.MULTILINE | re.DOTALL)
    patched_files = 0

    for smali_file in target_dir.glob("smali*/**/*.smali"):
        text = smali_file.read_text(encoding="utf-8", errors="ignore")

        if "security_blocked_signer_sha256_csv" in text:
            def patch_integrity_method(match: re.Match[str]) -> str:
                block = match.group(1)
                header = block.splitlines()[0]
                if "(Landroid/content/Context;)Ljava/lang/String;" in header and (
                    "getPackageInfo" in block or "JarFile" in block
                ):
                    return _replace_method_body(
                        block,
                        f"    .locals 1\n\n    const-string v0, \"{orig_sha256}\"\n\n    return-object v0\n",
                    )
                if "(Landroid/content/Context;)Z" in header:
                    if "getAssets" in block or "Ljava/lang/Class;->forName" in block or "ZipFile" in block:
                        return _replace_method_body(
                            block,
                            "    .locals 1\n\n    const/4 v0, 0x0\n\n    return v0\n",
                        )
                    if "getApplicationContext" in block or "areEqual" in block:
                        return _replace_method_body(
                            block,
                            "    .locals 1\n\n    const/4 v0, 0x1\n\n    return v0\n",
                        )
                if header.endswith(")Z") and "Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;" in block:
                    return _replace_method_body(
                        block,
                        "    .locals 1\n\n    const/4 v0, 0x1\n\n    return v0\n",
                    )
                return block

            new_text = method_re.sub(patch_integrity_method, text)
            new_text = new_text.replace('"/proc/self/maps"', '"/dev/null"')
            new_text = _neutralize_debug_checks(new_text)
            smali_file.write_text(new_text, encoding="utf-8")
            patched_files += 1
            print(f"[+] Patched integrity checker in {smali_file}")

        elif "PMS_HOOK_DETECTED:disk_signer_mismatch" in text or "security: periodic_integrity_violation" in text:
            def patch_periodic_method(match: re.Match[str]) -> str:
                block = match.group(1)
                header = block.splitlines()[0]
                if "invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;" in header:
                    return _replace_method_body(
                        block,
                        "    .locals 1\n\n    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;\n\n    return-object v0\n",
                    )
                return block

            new_text = method_re.sub(patch_periodic_method, text)
            new_text = new_text.replace('"/proc/self/maps"', '"/dev/null"')
            new_text = _neutralize_debug_checks(new_text)
            smali_file.write_text(new_text, encoding="utf-8")
            patched_files += 1
            print(f"[+] Patched periodic integrity monitor in {smali_file}")

        elif "security: unofficial_apk_blocked entry=" in text:
            def patch_gate_method(match: re.Match[str]) -> str:
                block = match.group(1)
                header = block.splitlines()[0]
                if header.endswith(")Z") and "unofficial_apk_blocked" in block:
                    return _replace_method_body(
                        block,
                        "    .locals 1\n\n    const/4 v0, 0x1\n\n    return v0\n",
                    )
                return block

            new_text = method_re.sub(patch_gate_method, text)
            smali_file.write_text(new_text, encoding="utf-8")
            patched_files += 1
            print(f"[+] Patched integrity gate in {smali_file}")

        elif "X-Device-Integrity" in text and '"/proc/self/maps"' in text:
            new_text = text.replace('"/proc/self/maps"', '"/dev/null"')
            new_text = _neutralize_debug_checks(new_text)
            smali_file.write_text(new_text, encoding="utf-8")
            patched_files += 1
            print(f"[+] Patched integrity headers in {smali_file}")

    if patched_files == 0:
        print("[!] Integrity checker smali classes not found (skipped smali bypass)")


def find_next_smali_dir(target_dir: Path) -> Path:
    idx = 2
    while True:
        candidate = target_dir / f"smali_classes{idx}"
        if not candidate.exists():
            return candidate
        idx += 1


def copy_mod_smali(mod_dir: Path, target_dir: Path) -> None:
    dest_smali = find_next_smali_dir(target_dir)
    dest_smali.mkdir(parents=True, exist_ok=True)
    packages_to_copy = [
        Path("io/github/hassinalhadi/ninja"),
        Path("top/canyie/pine"),
    ]
    for smali_root in sorted(mod_dir.glob("smali*")):
        if not smali_root.is_dir():
            continue
        for pkg in packages_to_copy:
            src = smali_root / pkg
            if src.exists():
                dst = dest_smali / pkg
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copytree(src, dst, dirs_exist_ok=True)
                print(f"[+] Copied {src} -> {dst}")


def copy_native_libs(mod_dir: Path, target_dir: Path) -> None:
    mod_lib = mod_dir / "lib"
    target_lib = target_dir / "lib"
    if not mod_lib.exists() or not target_lib.exists():
        return
    for abi_dir in target_lib.iterdir():
        if not abi_dir.is_dir():
            continue
        src_so = mod_lib / abi_dir.name / "libpine.so"
        if src_so.exists():
            shutil.copy2(src_so, abi_dir / "libpine.so")
            print(f"[+] Copied {src_so} -> {abi_dir / 'libpine.so'}")


def inject_android_app(target_dir: Path) -> None:
    candidates = list(target_dir.glob("smali*/delivery/samurai/android/AndroidApp.smali"))
    if not candidates:
        raise FileNotFoundError("AndroidApp.smali not found in decoded target APK")

    app_smali = candidates[0]
    content = app_smali.read_text(encoding="utf-8")

    if "NinjaMod;->init(Landroid/app/Application;)V" in content:
        print(f"[=] Already injected in {app_smali}")
        return

    pattern_super = re.compile(
        r"(\.method\s+[^\n]*\bonCreate\(\)V\b.*?invoke-super\s+\{[^}]+\},\s+[^\n]+->onCreate\(\)V\s*\n)",
        re.DOTALL,
    )
    new_content, count = pattern_super.subn(r"\1\n" + INJECT_CALL, content, count=1)
    if count > 0:
        app_smali.write_text(new_content, encoding="utf-8")
        print(f"[+] Injected NinjaMod.init after invoke-super in {app_smali}")
        return

    pattern_ret = re.compile(
        r"(\.method\s+[^\n]*\bonCreate\(\)V\b.*?)(\s+return-void\s*\n\.end method)",
        re.DOTALL,
    )
    new_content, count = pattern_ret.subn(r"\1\n" + INJECT_CALL + r"\2", content, count=1)
    if count > 0:
        app_smali.write_text(new_content, encoding="utf-8")
        print(f"[+] Injected NinjaMod.init before return-void in {app_smali}")
        return

    super_match = re.search(r"^\.super\s+(\S+)", content, re.MULTILINE)
    if not super_match:
        raise RuntimeError(f"Could not find .super directive in {app_smali}")
    super_class = super_match.group(1)

    on_create_method = (
        "\n.method public onCreate()V\n"
        "    .locals 0\n\n"
        f"    invoke-super {{p0}}, {super_class}->onCreate()V\n\n"
        f"{INJECT_CALL}"
        "    return-void\n"
        ".end method\n"
    )
    app_smali.write_text(content + on_create_method, encoding="utf-8")
    print(f"[+] Added onCreate()V with NinjaMod.init in {app_smali}")


def main() -> None:
    if len(sys.argv) < 3:
        print("Usage: python3 patch_apk.py <mod_decoded_dir> <target_decoded_dir> [target_apk]")
        sys.exit(1)

    mod_dir = Path(sys.argv[1])
    target_dir = Path(sys.argv[2])
    target_apk = Path(sys.argv[3]) if len(sys.argv) >= 4 else None

    orig_sha256 = None
    if target_apk and target_apk.exists():
        orig_sha256 = embed_original_cert(target_apk, target_dir)

    if orig_sha256:
        bypass_samurai_integrity_checks(target_dir, orig_sha256)

    copy_mod_smali(mod_dir, target_dir)
    copy_native_libs(mod_dir, target_dir)
    inject_android_app(target_dir)
    print("[✓] Patching complete.")


if __name__ == "__main__":
    main()
