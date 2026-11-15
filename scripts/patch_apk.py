import re
import shutil
import sys
from pathlib import Path


INJECT_CALL = "    invoke-static {p0}, Lio/github/hassinalhadi/ninja/NinjaMod;->init(Landroid/app/Application;)V\n"


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

    # 1. onCreate()V موجودة مع invoke-super بأي سجل
    pattern_super = re.compile(
        r"(\.method\s+[^\n]*\bonCreate\(\)V\b.*?invoke-super\s+\{[^}]+\},\s+[^\n]+->onCreate\(\)V\s*\n)",
        re.DOTALL,
    )
    new_content, count = pattern_super.subn(r"\1\n" + INJECT_CALL, content, count=1)
    if count > 0:
        app_smali.write_text(new_content, encoding="utf-8")
        print(f"[+] Injected NinjaMod.init after invoke-super in {app_smali}")
        return

    # 2. onCreate()V موجودة بدون invoke-super -> الحقن قبل return-void
    pattern_ret = re.compile(
        r"(\.method\s+[^\n]*\bonCreate\(\)V\b.*?)(\s+return-void\s*\n\.end method)",
        re.DOTALL,
    )
    new_content, count = pattern_ret.subn(r"\1\n" + INJECT_CALL + r"\2", content, count=1)
    if count > 0:
        app_smali.write_text(new_content, encoding="utf-8")
        print(f"[+] Injected NinjaMod.init before return-void in {app_smali}")
        return

    # 3. onCreate()V غير موجودة في AndroidApp -> إضافتها تلقائياً
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
    if len(sys.argv) != 3:
        print("Usage: python3 patch_apk.py <mod_decoded_dir> <target_decoded_dir>")
        sys.exit(1)

    mod_dir = Path(sys.argv[1])
    target_dir = Path(sys.argv[2])

    copy_mod_smali(mod_dir, target_dir)
    copy_native_libs(mod_dir, target_dir)
    inject_android_app(target_dir)
    print("[✓] Patching complete.")


if __name__ == "__main__":
    main()
