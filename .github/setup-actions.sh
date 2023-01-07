mkdir -p /etc/nix
cat > /etc/nix/upload-to-cache.sh << EOF
#!/bin/sh
set -eu
set -f # disable globbing
export IFS=' '

if ! command -v nix; then
  echo "Nix installing. Exit" | tee -a /tmp/nix-post-build-hook.log
  exit 0
fi

echo "Uploading paths" \$OUT_PATHS | tee -a /tmp/nix-post-build-hook.log
nix copy --to 's3://nix?profile=nix-upload&scheme=https&endpoint=${CACHE_DOMAIN}&secret-key=/etc/nix/cache-key.pem' \$OUT_PATHS | tee -a /tmp/nix-post-build-hook.log
EOF

mkdir -p ~/.aws
echo "$AWS_CREDENTIALS" > ~/.aws/credentials
echo -n "$CACHE_PRIV_KEY" | tr -d '\n' > /etc/nix/cache-key.pem

chown runner:runner /etc/nix/cache-key.pem
chmod 600 /etc/nix/cache-key.pem
chmod +x /etc/nix/upload-to-cache.sh
