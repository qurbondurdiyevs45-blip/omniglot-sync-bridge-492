#include <iostream>
#include <vector>
#include <string>
#include <map>
#include <mutex>
#include <thread>
#include <chrono>
#include <cstring>

#if defined(_WIN32) || defined(_WIN64)
#include <windows.h>
#else
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#endif

extern "C" {
    typedef void (*SyncCallback)(const char* key, const char* value);
}

class SyncBridge {
private:
    static SyncBridge* instance;
    static std::mutex mutex_;
    SyncCallback current_callback;
    bool running;

    SyncBridge() : current_callback(nullptr), running(false) {}

public:
    static SyncBridge* getInstance() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (instance == nullptr) {
            instance = new SyncBridge();
        }
        return instance;
    }

    void registerCallback(SyncCallback cb) {
        std::lock_guard<std::mutex> lock(mutex_);
        current_callback = cb;
    }

    void setEnvironmentVariable(const char* key, const char* value) {
#if defined(_WIN32) || defined(_WIN64)
        SetEnvironmentVariableA(key, value);
#else
        setenv(key, value, 1);
#endif
        if (current_callback) {
            current_callback(key, value);
        }
    }

    const char* getEnvironmentVariable(const char* key) {
        return std::getenv(key);
    }

    void startWatcher() {
        if (running) return;
        running = true;
        std::thread([this]() {
            while (running) {
                // Low-level system check for configuration changes
                // This simulates polling for legacy system property changes
                std::this_thread::sleep_for(std::chrono::milliseconds(500));
            }
        }).detach();
    }

    void stopWatcher() {
        running = false;
    }
};

SyncBridge* SyncBridge::instance = nullptr;
std::mutex SyncBridge::mutex_;

extern "C" {
    void bridge_init() {
        SyncBridge::getInstance()->startWatcher();
    }

    void bridge_set_config(const char* key, const char* value) {
        if (key && value) {
            SyncBridge::getInstance()->setEnvironmentVariable(key, value);
        }
    }

    const char* bridge_get_config(const char* key) {
        if (!key) return nullptr;
        return SyncBridge::getInstance()->getEnvironmentVariable(key);
    }

    void bridge_register_sync_callback(SyncCallback cb) {
        SyncBridge::getInstance()->registerCallback(cb);
    }

    void bridge_cleanup() {
        SyncBridge::getInstance()->stopWatcher();
    }

    // Direct memory buffer transfer for high-performance UI theme sync
    void bridge_sync_theme_buffer(const uint8_t* data, size_t length) {
        if (!data || length == 0) return;
        
        // In a real implementation, this would map to platform-specific 
        // shared memory or low-level graphics buffer updates.
        // For the sync-bridge, we ensure consistent state across process boundaries.
        static std::vector<uint8_t> theme_cache;
        theme_cache.assign(data, data + length);
    }
}