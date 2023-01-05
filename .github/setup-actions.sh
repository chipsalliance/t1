mkdir -p /etc/nix
cat > /etc/nix/upload-to-cache.sh << EOF
#!/bin/sh
set -eu
set -f # disable globbing
export IFS=' '

>&2 echo "[Post Build Hook] Signing paths" $OUT_PATHS
nix store sign --key-file /etc/nix/cache-key.pem $OUT_PATHS
>&2 echo "[Post Build Hook] Uploading paths" $OUT_PATHS
exec nix copy --to 's3://nix?profile=nix-upload&scheme=https&endpoint=minio.inner.fi.c-3.moe&secret-key=/etc/nix/cache-key.pem' $OUT_PATHS
EOF

mkdir -p ~/.aws
echo "$AWS_CREDENTIALS" > ~/.aws/credentials
echo -n "$CACHE_PRIV_KEY" | tr -d '\n' > /etc/nix/cache-key.pem

chown runner:runner /etc/nix/cache-key.pem
chmod 600 /etc/nix/cache-key.pem
ls -ll /etc/nix
ls -ll ~/.aws/credentials
chmod +x /etc/nix/upload-to-cache.sh
