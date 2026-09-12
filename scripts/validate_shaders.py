#!/usr/bin/env python3
"""Offline GLSL validation for ShizaVisuals's bundled shaders.

Scans src/main/resources/assets/shizavisuals/shaders for .vsh/.fsh files, expands
``#moj_import <namespace:path>`` directives (shizavisuals: from the local include
directory, minecraft: from embedded stubs mirroring vanilla's uniform blocks),
then validates each preprocessed shader with glslang twice:

  1. desktop GL syntax/semantics check:  glslang -S <stage> <file>
  2. Vulkan/SPIR-V check:                glslang -S <stage> -V \
         --auto-map-bindings --auto-map-locations -o /dev/null <file>

Files importing a minecraft: include we have no stub for (e.g. fog.glsl) are
reported as skipped. Exits nonzero listing every failure.

Stdlib only; requires glslang on PATH.
"""

import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SHADERS_ROOT = REPO_ROOT / "src" / "main" / "resources" / "assets" / "shizavisuals" / "shaders"
SILENTDLC_INCLUDE_DIR = SHADERS_ROOT / "include"

STAGES = {".vsh": "vert", ".fsh": "frag"}

MOJ_IMPORT_RE = re.compile(r"^\s*#moj_import\s*<\s*([a-z0-9_.-]+)\s*:\s*([^>]+?)\s*>")
VERSION_RE = re.compile(r"^\s*#version\b")

# Minimal stand-ins for the vanilla includes referenced by our shaders,
# matching the uniform blocks Minecraft 26.x binds at runtime.
MINECRAFT_STUBS = {
    "dynamictransforms.glsl": """\
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
""",
    "projection.glsl": """\
layout(std140) uniform Projection {
    mat4 ProjMat;
};

vec4 projection_from_position(vec4 p) {
    vec4 r = p * 0.5;
    r.xy = vec2(r.x + r.w, r.y + r.w);
    r.zw = p.zw;
    return r;
}
""",
    "globals.glsl": """\
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};
""",
}


class UnstubbedMinecraftImport(Exception):
    """Raised when a shader pulls in a minecraft: include we cannot stub."""

    def __init__(self, import_path):
        super().__init__(import_path)
        self.import_path = import_path


def strip_version_lines(source):
    return "\n".join(
        line for line in source.splitlines() if not VERSION_RE.match(line)
    )


def expand_source(source, seen_includes, strip_version):
    """Recursively expands moj_import directives; include-once semantics."""
    out_lines = []
    for line in source.splitlines():
        match = MOJ_IMPORT_RE.match(line)
        if not match:
            out_lines.append(line)
            continue

        namespace, import_path = match.group(1), match.group(2)
        key = (namespace, import_path)
        if key in seen_includes:
            out_lines.append("// moj_import <%s:%s> (already included)" % key)
            continue
        seen_includes.add(key)

        if namespace == "shizavisuals":
            include_file = SILENTDLC_INCLUDE_DIR / import_path
            if not include_file.is_file():
                raise FileNotFoundError(
                    "shizavisuals include not found: %s (from %s)" % (include_file, line.strip())
                )
            included = include_file.read_text(encoding="utf-8")
        elif namespace == "minecraft":
            if import_path not in MINECRAFT_STUBS:
                raise UnstubbedMinecraftImport(import_path)
            included = MINECRAFT_STUBS[import_path]
        else:
            raise UnstubbedMinecraftImport("%s:%s" % (namespace, import_path))

        expanded = expand_source(strip_version_lines(included), seen_includes, strip_version)
        out_lines.append("// ---- begin moj_import <%s:%s> ----" % key)
        out_lines.append(expanded)
        out_lines.append("// ---- end moj_import <%s:%s> ----" % key)

    return "\n".join(out_lines)


def preprocess(shader_file):
    source = shader_file.read_text(encoding="utf-8")
    return expand_source(source, set(), strip_version=True) + "\n"


def run_glslang(args, cwd):
    proc = subprocess.run(
        ["glslang"] + args,
        cwd=str(cwd),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return proc.returncode, proc.stdout.strip()


def to_vulkan_dialect(source):
    """Renames GL-only builtins to their Vulkan spellings (porting shim)."""
    source = re.sub(r"\bgl_VertexID\b", "gl_VertexIndex", source)
    source = re.sub(r"\bgl_InstanceID\b", "gl_InstanceIndex", source)
    return source


def validate(shader_file, stage, work_dir):
    """Returns a list of failure descriptions (empty when the file is clean)."""
    preprocessed = preprocess(shader_file)
    base_name = "preprocessed_" + shader_file.name.replace(".", "_")
    temp_file = work_dir / (base_name + ".glsl")
    temp_file.write_text(preprocessed, encoding="utf-8")

    failures = []
    code, output = run_glslang(["-S", stage, temp_file.name], work_dir)
    if code != 0:
        failures.append(("GL", output))

    vulkan_file = work_dir / (base_name + "_vk.glsl")
    vulkan_file.write_text(to_vulkan_dialect(preprocessed), encoding="utf-8")
    code, output = run_glslang(
        ["-S", stage, "-V", "--auto-map-bindings", "--auto-map-locations",
         "-o", "/dev/null", vulkan_file.name],
        work_dir,
    )
    if code != 0:
        failures.append(("Vulkan", output))

    return failures


def main():
    if shutil.which("glslang") is None:
        print("validate_shaders: glslang not found on PATH", file=sys.stderr)
        return 2
    if not SHADERS_ROOT.is_dir():
        print("validate_shaders: shader root not found: %s" % SHADERS_ROOT, file=sys.stderr)
        return 2

    shader_files = sorted(
        path for path in SHADERS_ROOT.rglob("*")
        if path.suffix in STAGES and path.is_file()
    )
    if not shader_files:
        print("validate_shaders: no .vsh/.fsh files found under %s" % SHADERS_ROOT, file=sys.stderr)
        return 2

    checked = 0
    skipped = []
    failed = []

    with tempfile.TemporaryDirectory(prefix="shizavisuals-shaders-") as temp_dir:
        work_dir = Path(temp_dir)
        for shader_file in shader_files:
            rel = shader_file.relative_to(SHADERS_ROOT)
            stage = STAGES[shader_file.suffix]
            try:
                failures = validate(shader_file, stage, work_dir)
            except UnstubbedMinecraftImport as skip:
                skipped.append((rel, skip.import_path))
                continue

            checked += 1
            if failures:
                failed.append((rel, failures))
                print("FAIL %s" % rel)
            else:
                print("ok   %s" % rel)

    for rel, import_path in skipped:
        print("skip %s (no stub for minecraft:%s)" % (rel, import_path))

    print()
    print("validate_shaders: %d checked, %d skipped, %d failed"
          % (checked, len(skipped), len(failed)))

    if failed:
        print()
        for rel, failures in failed:
            for mode, output in failures:
                print("==== %s [%s mode] ====" % (rel, mode))
                print(output)
                print()
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
