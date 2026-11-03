"""Check the Java examples in the docs.

Usage:
    python3 tools/check_java.py DOCS JAVA OUTDIR [ROBOT_PROJECT]

A block titled with a bare file name (```java title="Hello.java") is run with
`java Hello.java`. A block titled with a path inside a robot project
(```java title="src/main/java/first/robot/Robot.java") is copied into a fresh
copy of ROBOT_PROJECT and compiled with Gradle; these are skipped when no
ROBOT_PROJECT is given. The project must already have the vendordeps the
examples use, and must have been built once online so Gradle can run offline.

Before each block, tools/robot-fixtures/<chapter>/common/ and then
tools/robot-fixtures/<chapter>/<page>/ (e.g. 06-motors-sensors/motor-config/)
are copied into the project, so an example can refer to classes from other
pages (e.g. a RobotContainer that uses the chapter 5 Intake). The page folder
holds files that differ at that point of the chapter. Keep fixtures in sync
with the REV version shown in the docs.
"""
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

docs, java, outdir = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])
robot_project = Path(sys.argv[4]) if len(sys.argv) > 4 else None
fixtures_root = Path(__file__).resolve().parent / "robot-fixtures"
outdir.mkdir(parents=True, exist_ok=True)
fence = re.compile(r'^(?P<ind>[ \t]*)```java title="(?P<name>[^"]+\.java)"\s*$')
java_home = str(Path(java).resolve().parent.parent)
failed = 0
count = 0


def run_single(md, name, src_text):
    d = outdir / md.stem / name.removesuffix(".java")
    d.mkdir(parents=True, exist_ok=True)
    src = d / name
    src.write_text(src_text, encoding="utf-8")
    return subprocess.run([java, str(src)], capture_output=True, text=True, timeout=60, cwd=d)


def build_robot(md, name, src_text):
    global count
    count += 1
    d = outdir / "robot" / f"{md.stem}-{count}"
    if d.exists():
        shutil.rmtree(d)
    shutil.copytree(robot_project, d, ignore=shutil.ignore_patterns("build", ".gradle"))
    chapter = fixtures_root / md.parent.name
    for fixtures in (chapter / "common", chapter / md.stem):
        if fixtures.is_dir():
            shutil.copytree(fixtures, d, dirs_exist_ok=True)
    (d / name).write_text(src_text, encoding="utf-8")
    env = dict(os.environ, JAVA_HOME=java_home)
    return subprocess.run(["./gradlew", "build", "-x", "test", "--offline", "-q"],
                          capture_output=True, text=True, timeout=600, cwd=d, env=env)


for md in sorted(docs.rglob("*.md")):
    lines = md.read_text(encoding="utf-8").splitlines()
    i = 0
    while i < len(lines):
        m = fence.match(lines[i])
        if not m:
            i += 1
            continue
        ind, name = m["ind"], m["name"]
        body = []
        i += 1
        while lines[i].strip() != "```":
            body.append(lines[i][len(ind):])
            i += 1
        src_text = "\n".join(body) + "\n"
        label = f"{md.relative_to(docs)}:{i + 1} :: {name}"
        if "/" in name:
            if robot_project is None:
                print(f"== SKIP {label}")
                continue
            r = build_robot(md, name, src_text)
        else:
            r = run_single(md, name, src_text)
        status = "OK " if r.returncode == 0 else "FAIL"
        failed += r.returncode != 0
        print(f"== {status} {label}")
        out = (r.stdout + r.stderr).rstrip()
        if out:
            print(out)
print(f"\n{failed} failed")
sys.exit(1 if failed else 0)
