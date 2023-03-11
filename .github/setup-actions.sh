set -e

AWS_ID=$(echo "$AWS_CREDENTIALS" | head -n2 | tail -n1 | cut -d '=' -f 2)
AWS_SECRET=$(echo "$AWS_CREDENTIALS" | tail -n1 | cut -d '=' -f 2)

mkdir -p /etc/nix
cat > /etc/nix/upload-to-cache.sh << EOF
#!/bin/sh
set -eu
set -f # disable globbing
export IFS=' '

echo "Post-build hook invoked at \$USER (\$(whoami))" | tee -a /tmp/nix-post-build-hook.log

if ! command -v nix; then
  echo "Nix installing. Exit" | tee -a /tmp/nix-post-build-hook.log
  exit 0
fi

# export AWS_ACCESS_KEY_ID="${AWS_ID}"
# export AWS_SECRET_ACCESS_KEY="${AWS_SECRET}"

echo "Uploading paths" \$OUT_PATHS | tee -a /tmp/nix-post-build-hook.log
nix copy --to 's3://nix?profile=nix-upload&scheme=https&endpoint=${CACHE_DOMAIN}&secret-key=/etc/nix/cache-key.pem' \$OUT_PATHS 2>&1 | tee -a /tmp/nix-post-build-hook.log
EOF

mkdir -p ~/.aws
echo "$AWS_CREDENTIALS" > ~/.aws/credentials
echo "AWS Cred file: $(readlink -f ~/.aws/credentials)"
ls -ll ~/.aws/credentials

mkdir -p /root/.aws
echo "$AWS_CREDENTIALS" > /root/.aws/credentials
echo "AWS Cred file (root): $(readlink -f /root/.aws/credentials)"
ls -ll /root/.aws/credentials

echo -n "$CACHE_PRIV_KEY" | tr -d '\n' > /etc/nix/cache-key.pem
echo "Cache key file:"
ls -ll /etc/nix/cache-key.pem

chown runner:runner /etc/nix/cache-key.pem
chmod 600 /etc/nix/cache-key.pem
chmod +x /etc/nix/upload-to-cache.sh
