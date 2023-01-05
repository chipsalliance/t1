mkdir -p /etc/nix
cat > /etc/nix/upload-to-cache.sh << EOF
#!/bin/sh
set -eu
set -f # disable globbing
export IFS=' '

echo "[Post Build Hook] Signing paths" $OUT_PATHS
nix store sign --key-file /etc/nix/cache-key.pem $OUT_PATHS
echo "[Post Build Hook] Uploading paths" $OUT_PATHS
exec nix copy --to 's3://nix?profile=nix-upload&scheme=https&endpoint=minio.inner.fi.c-3.moe&secret-key=/etc/nix/cache-key.pem' $OUT_PATHS
EOF

echo $AWS_CREDENTIALS > ~/.aws/credentials
echo $CACHE_PRIV_KEY > /etc/nix/cache-key.pem

chmod +x /etc/nix/upload-to-cache.sh
