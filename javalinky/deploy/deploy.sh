#!/usr/bin/env bash
set -euo pipefail

HOST="user@javalinky.example.com"

echo "==> 本地构建（包括跑测试）"
mvn clean verify

echo "==> 上传"
scp target/javalinky.jar "$HOST:/tmp/javalinky.jar"

ssh "$HOST" bash -s <<'REMOTE'
set -euo pipefail

echo "==> 备份当前版本（方便回滚）"
sudo cp /opt/javalinky/javalinky.jar \
        /opt/javalinky/javalinky.jar.bak-$(date +%Y%m%d-%H%M%S)

echo "==> 替换"
sudo mv /tmp/javalinky.jar /opt/javalinky/javalinky.jar
sudo chown javalinky:javalinky /opt/javalinky/javalinky.jar

echo "==> 跑部署任务（迁移 + 播种）"
sudo -u javalinky env $(grep -v '^#' /etc/javalinky.env | xargs) \
     java -cp /opt/javalinky/javalinky.jar com.example.javalinky.Cli deploy

echo "==> 重启"
sudo systemctl restart javalinky

echo "==> 等它起来"
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/healthz > /dev/null; then
    echo "健康检查通过"
    exit 0
  fi
  sleep 1
done

echo "启动失败，最近的日志："
sudo journalctl -u javalinky -n 50 --no-pager
exit 1
REMOTE
