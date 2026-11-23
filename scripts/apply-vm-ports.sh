#!/usr/bin/env bash
# 在 VMware 的 Linux 虚拟机里执行：把宿主机端口换成不冲突的一组。
#
# 背景：VM 上已有 taco 的 MySQL(3306)/ES(9200)/Redis(7001)，
# 本项目的 compose 默认会占 3306/6379/9200/8080/8081，直接起会端口冲突。
#
# 用法（在仓库根目录）：
#   bash scripts/apply-vm-ports.sh
#
# 注意：这会修改 docker-compose.yml（git 里会显示为已修改），
# 建议配合 `git update-index --skip-worktree docker-compose.yml`，
# 让 git 忽略这个机器相关的改动，pull 时不会再冲突。
set -euo pipefail

cd "$(dirname "$0")/.."

sed -i \
  -e 's/"3306:3306"/"13306:3306"/' \
  -e 's/"6379:6379"/"16379:6379"/' \
  -e 's/"9200:9200"/"19200:9200"/' \
  -e 's/"8080:8080"/"18080:8080"/' \
  -e 's/"8081:80"/"18081:80"/' \
  docker-compose.yml

echo "已应用 VM 端口映射："
grep -E '13306|16379|19200|18080|18081' docker-compose.yml
