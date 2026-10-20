#!/usr/bin/env bash
# 构建 dist/region-agent.jar（内含 ASM，避免与 HMCL 的类路径冲突）。
# 首次运行会从 Maven Central 下载 ASM 并校验 SHA-256。
set -euo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$here"

ASM_VERSION=9.11.1
ASM_SHA256=ae4e6e10d59d0a1500317764b21881ad883f8550e33c66e6142f7032c6fb566c
ASM_TREE_SHA256=2b15c3c1b288a9fad3bb2cd6d988ba9521554914ed1f2a2029141fa3f483154a

mkdir -p lib
fetch() {
  local artifact=$1 sum=$2
  local jar="lib/$artifact-$ASM_VERSION.jar"
  if [ ! -f "$jar" ]; then
    echo "downloading $artifact-$ASM_VERSION.jar"
    curl -fsSL -o "$jar" \
      "https://repo1.maven.org/maven2/org/ow2/asm/$artifact/$ASM_VERSION/$artifact-$ASM_VERSION.jar"
  fi
  echo "$sum  $jar" | sha256sum -c -
}

fetch asm "$ASM_SHA256"
fetch asm-tree "$ASM_TREE_SHA256"

rm -rf build/agent && mkdir -p build/agent dist
javac -cp "lib/asm-$ASM_VERSION.jar:lib/asm-tree-$ASM_VERSION.jar" -d build/agent agent/RegionAgent.java
(cd build/agent && unzip -qo "$here/lib/asm-$ASM_VERSION.jar" && unzip -qo "$here/lib/asm-tree-$ASM_VERSION.jar" \
  && rm -f module-info.class META-INF/MANIFEST.MF META-INF/*.SF META-INF/*.RSA)
cat > build/MANIFEST.MF <<'MF'
Manifest-Version: 1.0
Premain-Class: RegionAgent
Can-Redefine-Classes: false
Can-Retransform-Classes: false
MF
jar --create --file dist/region-agent.jar --manifest build/MANIFEST.MF -C build/agent .
echo "built: dist/region-agent.jar"
