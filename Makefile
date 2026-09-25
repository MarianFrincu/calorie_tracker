# Calorie Tracker — everyday commands. Run `make` to list them.
#
# Needs only Docker locally (plus JDK 21+ and Maven for the desktop client, and
# the AWS CLI for the cloud targets). Everything else runs in containers.

SHELL       := bash
DESKTOP     := mvn -q -f desktop-client/pom.xml javafx:run
PLAYWRIGHT  := $(shell python3 -c "import json;print(json.load(open('web-client/package-lock.json'))['packages']['node_modules/@playwright/test']['version'])" 2>/dev/null || echo 1.63.0)

# AI settings for the local stack, taken from .envrc when present (only the
# GEMINI_*, AI_PROVIDER and AI_RATE_LIMIT_* lines - the rest of .envrc is
# not run; see .envrc.example). A Gemini key means Gemini, unless AI_PROVIDER says otherwise;
# without a key the free food-library search is used.
LOCAL_AI = set -a; \
	[ -f .envrc ] && eval "$$(grep -E '^[[:space:]]*export[[:space:]]+(GEMINI_API_KEY|GEMINI_MODEL|GEMINI_FALLBACK_MODEL|AI_PROVIDER|AI_RATE_LIMIT_PER_MINUTE|AI_RATE_LIMIT_PER_DAY)=' .envrc)"; \
	set +a; \
	export CALORIETRACKER_AI_PROVIDER="$${CALORIETRACKER_AI_PROVIDER:-$${AI_PROVIDER:-$$([ -n "$${GEMINI_API_KEY:-}" ] && echo gemini || echo library)}}"; \
	echo "  AI provider: $$CALORIETRACKER_AI_PROVIDER";

.DEFAULT_GOAL := help
.PHONY: help up down logs ps desktop test e2e \
        deploy deploy-app deploy-images smoke desktop-cloud cloud-status destroy package-desktop

help: ## Show this list
	@grep -hE '^[a-z0-9-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  make %-16s %s\n", $$1, $$2}'

# ── Local ─────────────────────────────────────────────────────────────────────
up: ## Run everything locally (no sign-in): web app on http://localhost:8088
	@$(LOCAL_AI) docker compose up --build -d --wait
	@echo; echo "  Web app:   http://localhost:8088"; echo "  API:       http://localhost:8080/api"; echo "  Eureka:    http://localhost:8761"

down: ## Stop the local stack (keeps your data)
	docker compose down --remove-orphans

logs: ## Follow the local logs
	docker compose logs -f --tail=100

ps: ## Show local container health
	docker compose ps

desktop: ## Desktop client against the local stack (make up first)
	$(DESKTOP)

# ── Tests ─────────────────────────────────────────────────────────────────────
test: ## All unit + integration tests, in Docker
	./scripts/test-all.sh

e2e: ## Browser tests of the web app against the running local stack (make up first)
	docker run --rm --network host -u $$(id -u):$$(id -g) -e HOME=/tmp \
	  -e E2E_BASE_URL=$${E2E_BASE_URL:-http://localhost:8088} \
	  -e E2E_EMAIL -e E2E_PASSWORD -e E2E_ALLOW_DELETE \
	  -v $(CURDIR)/web-client:/w -w /w mcr.microsoft.com/playwright:v$(PLAYWRIGHT)-noble \
	  sh -c "npm ci --no-audit --no-fund --silent && npx playwright test --output /tmp/pw $${E2E_ARGS}"

# ── AWS ───────────────────────────────────────────────────────────────────────
deploy: ## Deploy everything to AWS (see README "Deploy to AWS")
	./scripts/deploy.sh

deploy-app: ## Update only the app stack (after changing 04-app.yaml or a setting)
	./scripts/deploy.sh app

deploy-images: ## Rebuild + roll out images without git/CI (SERVICES="backend-core ..." for a subset)
	./scripts/deploy.sh images $(SERVICES)

smoke: ## Test the deployed app end to end (creates + deletes a test user)
	./scripts/smoke-test.sh

desktop-cloud: ## Desktop client against the deployed app
	@. scripts/vars.sh || exit 1; \
	url="$$(stack_output "$$STACK_APP" ApiUrl)"; \
	[ -n "$$url" ] || { echo "Nothing deployed - run 'make deploy' first."; exit 1; }; \
	echo "Desktop client -> $$url"; \
	API_BASE_URL="$$url" AUTH_MODE=cognito COGNITO_USER_POOL_ID="$$(stack_output "$$STACK_COGNITO" UserPoolId)" \
	  COGNITO_CLIENT_ID="$$(stack_output "$$STACK_COGNITO" AppClientId)" $(DESKTOP)

cloud-status: ## Which calorietracker stacks exist right now (i.e. what is costing money)
	@aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE UPDATE_ROLLBACK_COMPLETE CREATE_IN_PROGRESS DELETE_IN_PROGRESS DELETE_FAILED \
	  --query "StackSummaries[?starts_with(StackName,'calorietracker')].[StackName,StackStatus]" --output table

destroy: ## Delete everything in AWS (stops all charges)
	./scripts/destroy.sh

package-desktop: ## Build a native desktop app for this OS (see scripts/package-desktop.sh)
	./scripts/package-desktop.sh
