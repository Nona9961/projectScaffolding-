#!/usr/bin/env bash
#
# export-project.sh —— 脚手架导出工具
#
# 从脚手架子仓（本仓库）导出一个可直接开始开发的新项目骨架：
#   输入「基础包名 + 聚合根名」，输出新项目目录；模板命名（com.nona /
#   rootPackage / DomainRoot 系列）全部参数化替换，脚手架自测用例与附属物剔除。
#
# 用法:
#   scripts/export-project.sh <base-package> <root-name> <output-dir> [--force]
#
# 示例:
#   scripts/export-project.sh com.acme order ./my-order-service
#
# 参数:
#   <base-package>  基础包名，如 com.acme（校验 ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$）
#   <root-name>     聚合根名，如 order（校验 ^[a-z][a-zA-Z0-9]*$）
#   <output-dir>    输出目录：不存在或为空目录；非空时须显式 --force
#   --force         输出目录非空时清空重建（默认拒绝，避免破坏既有内容）
#
# 幂等性: 全程无时间戳 / 随机数写入，相同参数 + 全新目录 ⇒ 输出字节一致。
#
set -euo pipefail

# ---- 定位脚手架根目录（本脚本所在目录的上一级） ----
SCAFFOLD_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
用法: export-project.sh <base-package> <root-name> <output-dir> [--force]

  <base-package>  基础包名，如 com.acme（^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$）
  <root-name>     聚合根名，如 order（^[a-z][a-zA-Z0-9]*$）
  <output-dir>    输出目录：不存在或为空目录；--force 时允许覆盖非空
  --force         输出目录非空时清空重建
EOF
}

die() {
  echo "error: $*" >&2
  exit 1
}

# ---- 解析参数（--force 可在任意位置） ----
force=0
args=()
for a in "$@"; do
  case "$a" in
    --force) force=1 ;;
    *) args+=("$a") ;;
  esac
done

if [ "${#args[@]}" -ne 3 ]; then
  usage
  die "需要三个位置参数: <base-package> <root-name> <output-dir>"
fi

base_pkg="${args[0]}"
root_name="${args[1]}"
out_dir="${args[2]}"

# ---- 参数校验 ----
if [[ ! "$base_pkg" =~ ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$ ]]; then
  die "base-package 不合法: '$base_pkg'（需匹配 ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$，如 com.acme）"
fi
if [[ ! "$root_name" =~ ^[a-z][a-zA-Z0-9]*$ ]]; then
  die "root-name 不合法: '$root_name'（需匹配 ^[a-z][a-zA-Z0-9]*$，如 order）"
fi

# ---- 派生命名 ----
pkg="$base_pkg"
pkg_path="${base_pkg//.//}"                 # com.acme -> com/acme
root="$root_name"                           # order -> order
Root="$(printf '%s' "$root_name" | perl -pe 's/^(.)/\U$1/')"   # order -> Order
ROOT_ALL="$(printf '%s' "$root_name" | tr '[:lower:]' '[:upper:]')"  # order -> ORDER

# ---- 输出目录准备 ----
mkdir -p "$out_dir"
OUT="$(cd "$out_dir" && pwd)"
case "$OUT" in
  /|"") die "输出目录不合法: $out_dir" ;;
esac

# 防呆：输出目录不得与脚手架子仓重叠（相等 / 为仓库祖先 / 为仓库子目录）——
# --force 的清空或 rsync 复制会触碰仓库自身（可能清掉模板或仓库外的项目目录）。
if [ "$SCAFFOLD_ROOT" = "$OUT" ] || [ "${SCAFFOLD_ROOT#"$OUT"/}" != "$SCAFFOLD_ROOT" ] \
   || [ "${OUT#"$SCAFFOLD_ROOT"/}" != "$OUT" ]; then
  die "输出目录与脚手架子仓重叠: $out_dir（请选择仓库外的目录）"
fi

if [ -e "$out_dir" ] && [ -n "$(ls -A "$out_dir" 2>/dev/null)" ]; then
  if [ "$force" -eq 0 ]; then
    die "输出目录非空: $out_dir（使用 --force 覆盖，或换一个空目录）"
  fi
  # --force：清空目录内容（保留目录本身；绝不触碰目录外路径）
  find "$OUT" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
fi

# ---- 1. rsync 复制 + 排除 ----
# 排除清单：构建产物、自测用例、版本历史、脚手架附属物、本机私密配置（.mvn 的
# maven.config 引用本机私有 m2 settings.xml 绝对路径，绝不能导出）、防御性 IDE/日志目录。
# rsync 规则：不含嵌入斜杠的模式匹配任意层级（target/、logs/、.git/ 等）；
# src/test 在模块内（api|common|server/src/test），须用 **/ 前缀匹配任意层级。
excludes=(
  --exclude 'target/'
  --exclude '**/src/test/'
  --exclude '.git/'
  --exclude '.gitignore'
  --exclude '.mvn/'
  --exclude 'issues/'
  --exclude 'scripts/'
  --exclude 'structure.md'
  --exclude 'domain.md'
  --exclude '*.iml'
  --exclude '.idea/'
  --exclude 'logs/'
)

rsync -a "${excludes[@]}" "$SCAFFOLD_ROOT/api/"    "$OUT/api/"
rsync -a "${excludes[@]}" "$SCAFFOLD_ROOT/common/" "$OUT/common/"
rsync -a "${excludes[@]}" "$SCAFFOLD_ROOT/server/" "$OUT/server/"
rsync -a "${excludes[@]}" "$SCAFFOLD_ROOT/docs/"   "$OUT/docs/"
rsync -a "${excludes[@]}" "$SCAFFOLD_ROOT/pom.xml" "$OUT/pom.xml"
# README 导出但更名：内容保留脚手架集成背景，命名避开新项目的门面文档
rsync -a "${excludes[@]}" "$SCAFFOLD_ROOT/README.md" "$OUT/SCAFFOLD-HELPER.md"

# ---- 2. 目录重映射 ----
remap_dir() {
  local src="$1" dst="$2"
  [ -e "$src" ] || die "缺少待重映射目录: $src"
  mkdir -p "$(dirname "$dst")"
  mv "$src" "$dst"
}

remap_dir "$OUT/api/src/main/java/com/nona"        "$OUT/api/src/main/java/$pkg_path"
remap_dir "$OUT/common/src/main/java/com/nona"     "$OUT/common/src/main/java/$pkg_path"
remap_dir "$OUT/server/src/main/java/com/nona"     "$OUT/server/src/main/java/$pkg_path"
remap_dir "$OUT/server/src/main/java/$pkg_path/domain/rootPackage" \
          "$OUT/server/src/main/java/$pkg_path/domain/$root"

# 清掉重映射后的空父目录（rmdir 只删空目录，安全）
rmdir "$OUT/api/src/main/java/com"     "$OUT/common/src/main/java/com" \
      "$OUT/server/src/main/java/com" 2>/dev/null || true

# ---- 3. Token 替换（长 token 优先，大小写敏感，全文件替换） ----
# com.nona 同时是外部依赖 changeTracking 的两层引用面：
#  1) Maven 坐标层（dependencyManagement / server 依赖中的 com.nona:change-tracking-api）
#  2) Java 包名层（服务器源码 19 处 import com.nona.changeTracking.domain.model.tracking.* 等）
# 两者都必须保持原样；其余 com.nona 全部替换为 <pkg>。
# 实现：统一双负向预查（对全部复制文件生效）——com.nona 后紧邻 .changeTracking
# （框架 Java 包）或 </groupId>+<artifactId>change-tracking-（外部 Maven 坐标）时不替换。
# 其余 token 顺序：长 token（DomainRootFactory / DomainRootRepository /
# configureSomeStatus）必须先于其子串（DomainRoot / someStatus）替换。
# 附带：surefire argLine 中硬编码的本机 m2 绝对路径（javaagent）转为 ${user.home}
# 相对形式——新项目构建环境与本机不同，可移植且避免本机路径泄漏。
export PKG="$pkg" ROOT_LOWER="$root" ROOT_UPPER="$ROOT_ALL" ROOT_CAP="$Root"

replace_tokens() {
  local file="$1"
  perl -0pi -e '
    s/com\.nona\.domain\.rootPackage/$ENV{PKG}.domain.$ENV{ROOT_LOWER}/g;
    s/com\.nona(?!\.changeTracking)(?!<\/groupId>\s*<artifactId>change-tracking-)/$ENV{PKG}/g;
    s#-javaagent:/opt/code/.m2/repository/#-javaagent:\${user.home}/.m2/repository/#g;
    s/DomainRootFactory/${ENV{ROOT_CAP}}Factory/g;
    s/DomainRootRepository/${ENV{ROOT_CAP}}Repository/g;
    s/configureSomeStatus/configure${ENV{ROOT_CAP}}Status/g;
    s/DomainRoot/${ENV{ROOT_CAP}}/g;
    s/DomainValueObject/${ENV{ROOT_CAP}}ValueObject/g;
    s/SomeStatus/${ENV{ROOT_CAP}}Status/g;
    s/someStatus/${ENV{ROOT_LOWER}}Status/g;
    s/SOME_STATUS/${ENV{ROOT_UPPER}}_STATUS/g;
    s/rootPackage/${ENV{ROOT_LOWER}}/g;
    s/someDto/${ENV{ROOT_CAP}}Dto/g;
  ' "$file"
}

# 候选文件：含任一模板 token 的文本文件（含资源文件如 AutoConfiguration.imports）
while IFS= read -r f; do
  replace_tokens "$f"
done < <(grep -rlI -E 'com\.nona|rootPackage|DomainRoot|SomeStatus|someStatus|SOME_STATUS|someDto' "$OUT" || true)

# ---- 4. 类文件配套改名（public 类名 = 文件名） ----
# 6 个模板类 + api 模块 DTO 示例（someDto.java → <Root>Dto.java，record 类名随
# token 改为 <Root>Dto，文件名必须同步）；BizClient.java（通用 ACL 示例）不动。
ROOT_DIR="$OUT/server/src/main/java/$pkg_path/domain/$root"
mv "$ROOT_DIR/entity/DomainRoot.java"            "$ROOT_DIR/entity/${Root}.java"
mv "$ROOT_DIR/entity/DomainValueObject.java"     "$ROOT_DIR/entity/${Root}ValueObject.java"
mv "$ROOT_DIR/entity/SomeStatus.java"            "$ROOT_DIR/entity/${Root}Status.java"
mv "$ROOT_DIR/factory/DomainRootFactory.java"    "$ROOT_DIR/factory/${Root}Factory.java"
mv "$ROOT_DIR/repo/DomainRootRepository.java"    "$ROOT_DIR/repo/${Root}Repository.java"
mv "$OUT/api/src/main/java/$pkg_path/dto/someDto.java" "$OUT/api/src/main/java/$pkg_path/dto/${Root}Dto.java"

# ---- 5. 最终自检（未通过则脚本失败，不留半成品） ----
fail=0
err_msg() {
  echo "FAIL: $*" >&2
  fail=1
}

# 四层齐全：entity / factory / repo / ports
[ -f "$ROOT_DIR/entity/${Root}.java" ]            || err_msg "缺少 entity/${Root}.java"
[ -f "$ROOT_DIR/entity/${Root}ValueObject.java" ] || err_msg "缺少 entity/${Root}ValueObject.java"
[ -f "$ROOT_DIR/entity/${Root}Status.java" ]      || err_msg "缺少 entity/${Root}Status.java"
[ -f "$ROOT_DIR/factory/${Root}Factory.java" ]    || err_msg "缺少 factory/${Root}Factory.java"
[ -f "$ROOT_DIR/repo/${Root}Repository.java" ]    || err_msg "缺少 repo/${Root}Repository.java"
[ -f "$ROOT_DIR/ports/bizRelated/BizClient.java" ] || err_msg "缺少 ports/bizRelated/BizClient.java"

# 模板命名残留检查：
#  - 非 pom 文件：com.nona 只允许紧跟外部框架包名 .changeTracking（Java import）；
#    其余 token（rootPackage/DomainRoot/SomeStatus/someStatus/SOME_STATUS/someDto/
#    SmokeTest/AcTest）一律不许出现
#  - pom 文件：与替换同一负向预查规则——仅 change-tracking Maven 坐标的 com.nona
#    允许保留；其余 token 一律不许出现
residue="$(find "$OUT" -type f ! -name 'pom.xml' -print0 | xargs -0 perl -ne '
  if (/com\.nona(?!\.changeTracking)/) { print "$ARGV: com.nona 未受保护出现\n"; }
  for my $t (qw(rootPackage DomainRoot SomeStatus someStatus SOME_STATUS someDto SmokeTest AcTest)) {
    print "$ARGV: $t 残留\n" if index($_, $t) >= 0;
  }
' 2>/dev/null || true)"
pom_residue="$(perl -0ne '
  if (/com\.nona(?!\.changeTracking)(?!<\/groupId>\s*<artifactId>change-tracking-)/) {
    print "$ARGV: com.nona 未受保护出现\n";
  }
  for my $t (qw(rootPackage DomainRoot SomeStatus someStatus SOME_STATUS someDto SmokeTest AcTest)) {
    print "$ARGV: $t 残留\n" if index($_, $t) >= 0;
  }
' "$OUT/pom.xml" "$OUT/api/pom.xml" "$OUT/common/pom.xml" "$OUT/server/pom.xml" 2>/dev/null || true)"
if [ -n "$residue" ] || [ -n "$pom_residue" ]; then
  err_msg "模板命名残留:"
  [ -n "$residue" ] && echo "$residue" >&2
  [ -n "$pom_residue" ] && echo "$pom_residue" >&2
fi

# 本机绝对路径不得泄漏（javaagent 均应已替换为 ${user.home} 相对形式）
leaked="$(grep -rlI -- '-javaagent:/opt/code/' "$OUT" 2>/dev/null || true)"
if [ -n "$leaked" ]; then
  err_msg "导出内容含本机 javaagent 绝对路径: $leaked"
fi

# 被排除项不得出现
for p in src/test target .git .gitignore issues scripts .mvn structure.md domain.md logs; do
  if [ -n "$(find "$OUT" -name "$p" 2>/dev/null | head -n 1)" ]; then
    err_msg "导出内容含被排除项: $p"
  fi
done

if [ "$fail" -ne 0 ]; then
  die "导出自检未通过（见上方 FAIL 项）"
fi

echo "导出完成: $OUT"
echo "  base-package: $pkg"
echo "  aggregate-root: $root (${Root} / ${ROOT_ALL} 派生)"