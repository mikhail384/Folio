#!/bin/sh
set -eu
lineage="${RENEWED_LINEAGE:-/opt/folio-canvas-test/acme/live/folio-ip}"
target=/opt/folio-canvas-test/tls
install -d -o root -g folio-test -m 0750 "$target"
install -o root -g folio-test -m 0644 "$lineage/fullchain.pem" "$target/fullchain.pem.new"
install -o root -g folio-test -m 0640 "$lineage/privkey.pem" "$target/privkey.pem.new"
mv -f "$target/fullchain.pem.new" "$target/fullchain.pem"
mv -f "$target/privkey.pem.new" "$target/privkey.pem"
if systemctl is-active --quiet folio-gateway.service; then
  systemctl restart folio-gateway.service
elif systemctl is-active --quiet folio-canvas-test-tls.service; then
  systemctl restart folio-canvas-test-tls.service
fi
