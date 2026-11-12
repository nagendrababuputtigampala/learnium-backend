#!/usr/bin/env bash
# One-time setup for the Learnium Azure VM (Ubuntu Server 24.04 LTS).
#
# Run as the VM user (azureuser) from the learnium-backend repo root after
# the VM is created:
#   curl -fsSL https://raw.githubusercontent.com/nagendrababuputtigampala/learnium-backend/dev/deploy/vm-setup.sh | bash
# — or, if the repos are already cloned:
#   bash deploy/vm-setup.sh
#
# Idempotent: safe to re-run. It will:
#   1. Install Docker Engine + Compose plugin (official Docker apt repo).
#   2. Add a 2 GB swap file if none exists (the B2ats v2 has 4 GB RAM and
#      builds both images; swap keeps a build from being OOM-killed).
#   3. Clone learnium-backend and learnium-frontend side by side under
#      ~/learnium (skips any repo that is already there).
#   4. Copy the env templates to prod.env / dev.env (only if absent — it
#      never overwrites filled env files) and print the next steps.
set -euo pipefail

LEARNIUM_HOME="${HOME}/learnium"
GITHUB_OWNER="nagendrababuputtigampala"
SWAP_FILE="/swapfile"

echo "==> Installing Docker Engine + Compose plugin"
if ! command -v docker >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  sudo chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  sudo usermod -aG docker "${USER}"
  echo "    Docker installed. NOTE: log out and back in once so the docker group applies."
else
  echo "    Docker already present — skipping install."
fi

echo "==> Ensuring 2 GB swap"
if ! sudo swapon --show | grep -q .; then
  if [ ! -f "${SWAP_FILE}" ]; then
    sudo fallocate -l 2G "${SWAP_FILE}"
    sudo chmod 600 "${SWAP_FILE}"
    sudo mkswap "${SWAP_FILE}"
  fi
  sudo swapon "${SWAP_FILE}"
  grep -q "^${SWAP_FILE} " /etc/fstab || echo "${SWAP_FILE} none swap sw 0 0" | sudo tee -a /etc/fstab >/dev/null
  echo "    Swap enabled and persisted in /etc/fstab."
else
  echo "    Swap already active — skipping."
fi

echo "==> Cloning repos into ${LEARNIUM_HOME}"
mkdir -p "${LEARNIUM_HOME}"
for repo in learnium-backend learnium-frontend; do
  if [ -d "${LEARNIUM_HOME}/${repo}/.git" ]; then
    echo "    ${repo} already cloned — skipping."
  else
    git clone "https://github.com/${GITHUB_OWNER}/${repo}.git" "${LEARNIUM_HOME}/${repo}"
  fi
done
git -C "${LEARNIUM_HOME}/learnium-backend" checkout dev
git -C "${LEARNIUM_HOME}/learnium-frontend" checkout dev

echo "==> Preparing env files (templates copied only if absent)"
cd "${LEARNIUM_HOME}/learnium-backend"
for env in prod dev; do
  if [ -f "${env}.env" ]; then
    echo "    ${env}.env already exists — NOT overwriting."
  else
    cp "deploy/${env}.env.example" "${env}.env"
    chmod 600 "${env}.env"
    echo "    Created ${env}.env from template — fill in the real values."
  fi
done

cat <<'NEXT'

Setup complete. Next steps:
  1. If Docker was just installed, log out and back in (docker group).
  2. Fill prod.env and dev.env (values from your password manager;
     generate secrets with: openssl rand -base64 64).
  3. Bring up prod first:
       cd ~/learnium/learnium-backend
       docker compose -p learnium-prod -f docker-compose.azure.yml --env-file prod.env up -d --build
  4. Verify: curl http://localhost/api/v1/system/ping
NEXT
