#!/usr/bin/env bash

set -e

mkdir -p /etc/nix

cat > /etc/nix/upload-to-cache.sh << EOF
#!/bin/sh
set -eu
set -f # disable globbing
export IFS=' '

echo "Post-build hook invoked at \$USER (\$(whoami))" | tee -a /tmp/nix-post-build-hook.log

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

echo "$ROCKETCHIP_NIX_REMOTE_SSH_PRIVKEY" > /etc/nix/builder-key
echo "Builder key file:"
ls -ll /etc/nix/builder-key

mkdir -p ~runner/.ssh
cat > ~runner/.ssh/config << EOF
StrictHostKeyChecking accept-new

Host builder
  HostName ${ROCKETCHIP_NIX_REMOTE_HOST}
  IdentitiesOnly yes
  IdentityFile /etc/nix/builder-key
  User nix-remote
EOF

tee -a /etc/nix/nix.conf << EOF
post-build-hook = /etc/nix/upload-to-cache.sh
extra-trusted-public-keys = minio.inner.fi.c-3.moe:gDg5SOIH65O0tTV89dUawME5BTmduWWaA7as/cqvevM=
extra-substituters = https://${CACHE_DOMAIN}/nix
max-jobs = 0
builders = ssh-ng://builder x86_64-linux - 32 1 big-parallel
builders-use-substitutes = true
EOF

chown runner:runner /etc/nix/cache-key.pem
chmod 400 /etc/nix/cache-key.pem
chown runner:runner /etc/nix/builder-key
chmod 400 /etc/nix/builder-key
chmod +x /etc/nix/upload-to-cache.sh
