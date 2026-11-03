"""Extract ```java title="X.java" blocks from docs and run each with the JDK."""
import re
import subprocess
import sys
from pathlib import Path

docs, java, outdir = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])
outdir.mkdir(parents=True, exist_ok=True)
fence = re.compile(r'^(?P<ind>[ \t]*)```java title="(?P<name>[^"]+\.java)"\s*$')
failed = 0
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
        d = outdir / md.stem / name.removesuffix(".java")
        d.mkdir(parents=True, exist_ok=True)
        src = d / name
        src.write_text("\n".join(body) + "\n", encoding="utf-8")
        r = subprocess.run([java, str(src)], capture_output=True, text=True, timeout=60, cwd=d)
        status = "OK " if r.returncode == 0 else "FAIL"
        failed += r.returncode != 0
        print(f"== {status} {md.relative_to(docs)} :: {name}")
        print((r.stdout + r.stderr).rstrip())
print(f"\n{failed} failed")
