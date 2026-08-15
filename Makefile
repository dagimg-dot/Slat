# Dynamic Environment Detection
USER_HOME ?= $(HOME)

# Detect JAVA_HOME (prefer SDKMAN if present and JAVA_HOME not set)
ifeq ($(JAVA_HOME),)
    SDKMAN_JAVA := $(USER_HOME)/.sdkman/candidates/java/current
    ifneq ($(wildcard $(SDKMAN_JAVA)),)
        export JAVA_HOME := $(SDKMAN_JAVA)
    endif
endif

# Detect Android SDK directory
ifeq ($(ANDROID_HOME),)
    ifeq ($(ANDROID_SDK_ROOT),)
        ifneq ($(wildcard $(USER_HOME)/Android/Sdk),)
            export ANDROID_HOME := $(USER_HOME)/Android/Sdk
        else ifneq ($(wildcard $(USER_HOME)/Library/Android/sdk),)
            export ANDROID_HOME := $(USER_HOME)/Library/Android/sdk
        endif
    else
        export ANDROID_HOME := $(ANDROID_SDK_ROOT)
    endif
endif

# Check if running on Linux aarch64/ARM64 and configure native aapt2 override if available
UNAME_S := $(shell uname -s)
UNAME_M := $(shell uname -m)
GRADLE_FLAGS :=

ifeq ($(UNAME_S),Linux)
    ifneq ($(filter aarch64 arm64,$(UNAME_M)),)
        AAPT2_PATH := $(ANDROID_HOME)/build-tools/35.0.0/aapt2
        ifneq ($(wildcard $(AAPT2_PATH)),)
            GRADLE_FLAGS += -Pandroid.aapt2override=$(AAPT2_PATH) -Pandroid.aapt2FromMavenOverride=$(AAPT2_PATH)
        endif
    endif
endif

GRADLE_WRAPPER := ./gradlew $(GRADLE_FLAGS)
ADB := adb
ADB_PORT := 5037
DEBUG_PORT := 8600
LOG_TAG := GLIDE
WAIT_TIME := 2
USE_DEBUGGER := 1

# Environment / Build Variant: MODE=dev (default) or MODE=prod
MODE ?= dev

ifeq ($(MODE),prod)
	BUILD_TASK := assembleRelease
	INSTALL_TASK := installRelease
	PKG_NAME := com.dagimg.glide
	APK_PATH := app/build/outputs/apk/release/app-release-unsigned.apk
else
	BUILD_TASK := assembleDebug
	INSTALL_TASK := installDebug
	PKG_NAME := com.dagimg.glide.dev
	APK_PATH := app/build/outputs/apk/debug/app-debug.apk
endif

ACTIVITY_NAME := com.dagimg.glide.MainActivity

format:
	$(GRADLE_WRAPPER) ktlintFormat

lint:
	$(GRADLE_WRAPPER) ktlintCheck

compile:
	$(GRADLE_WRAPPER) compileDebugKotlin

release:
	$(MAKE) build MODE=prod

build:
	$(GRADLE_WRAPPER) $(BUILD_TASK)

install: build
	$(ADB) install -r $(APK_PATH)

# Fast deploy method - uses InstallDebug/InstallRelease
fast-deploy:
	$(GRADLE_WRAPPER) $(INSTALL_TASK)

launch-debug:
	$(ADB) shell am start -D -n $(PKG_NAME)/$(ACTIVITY_NAME)

launch:
	$(ADB) shell am start -n $(PKG_NAME)/$(ACTIVITY_NAME)

# Normal run without clean (preserves build cache, faster)
run: install
	@if [ $(USE_DEBUGGER) -eq 1 ]; then \
		$(MAKE) launch-debug; \
		sleep $(WAIT_TIME); \
		$(MAKE) attach; \
	else \
		$(MAKE) launch; \
	fi

# Fast run - just update code and launch with synchronization
fast-run:
	@echo "Force stopping app ($(PKG_NAME))..."
	$(MAKE) force-stop
	@echo "Installing app..."
	$(GRADLE_WRAPPER) $(INSTALL_TASK)
	@if [ $(USE_DEBUGGER) -eq 1 ]; then \
		echo "Starting app with debug flag..."; \
		$(MAKE) launch-debug; \
		echo "Waiting for app to initialize ($(WAIT_TIME) seconds)..."; \
		sleep $(WAIT_TIME); \
		echo "Connecting debugger..."; \
		echo "NOTE: If debugger times out, try: make adb-reset followed by make attach_manual"; \
		$(MAKE) attach; \
	else \
		echo "Starting app normally (no debug)..."; \
		$(ADB) shell am start -n $(PKG_NAME)/$(ACTIVITY_NAME); \
	fi


# Attach debugger with automatic continue (run command)
attach:
	@echo "Attaching debugger to process..."
	@echo "Setting up port forwarding on ADB port $(ADB_PORT)..."
	$(eval PROCESS_ID := $(shell $(ADB) shell ps | grep glide | awk '{print $$2}'))
	@if [ -z "$(PROCESS_ID)" ]; then \
		echo "No running process found. Launch the app first."; \
	else \
		echo "Found process ID: $(PROCESS_ID)"; \
		$(ADB) -P $(ADB_PORT) forward tcp:$(DEBUG_PORT) jdwp:$(PROCESS_ID); \
		echo "Debug port forwarded. Connecting debugger..."; \
		echo "run" > /tmp/jdb_commands.txt; \
		echo "exit" >> /tmp/jdb_commands.txt; \
		timeout 20 jdb -attach localhost:$(DEBUG_PORT) < /tmp/jdb_commands.txt || echo "JDB timed out. Try 'make attach_manual' instead"; \
	fi

fast-run-no-debug:
	@echo "Force stopping app ($(PKG_NAME))..."
	$(MAKE) force-stop
	@echo "Installing app..."
	$(GRADLE_WRAPPER) $(INSTALL_TASK)
	@echo "Starting app without debugging..."
	$(MAKE) launch
	@echo "App started successfully."

devices:
	$(ADB) devices -l

ps:
	$(ADB) shell ps | grep -E "com.dagimg.glide"

# Show all logs in real-time (similar to Flutter logs)
logs:
	$(ADB) logcat

# Show application logs only (filters by package name)
applogs:
	$(ADB) logcat --pid=$(shell $(ADB) shell pidof -s $(PKG_NAME))

# Show logs filtered by tag (more specific filtering)
taglogs:
	$(ADB) logcat $(LOG_TAG):V *:S

# Stop running app
force-stop:
	$(ADB) shell am force-stop $(PKG_NAME)

# Deploy and stream logs
dev:
	@echo "Installing app ($(PKG_NAME))..."
	$(GRADLE_WRAPPER) $(INSTALL_TASK)
	@echo "Starting app..."
	$(MAKE) launch
	@echo "Waiting for app to initialize..."
	sleep 1
	$(MAKE) applogs
