import Foundation
import Combine

/**
 * MeshObserver.swift
 * OmniGlot Sync-Bridge
 * 
 * Swift wrapper for local non-volatile storage synchronization.
 * Interfaces with the P2P mesh to ensure iOS system-level preferences 
 * and persistent storage remain consistent with other nodes.
 */

public final class MeshObserver: NSObject, ObservableObject {
    
    public static let shared = MeshObserver()
    
    private let storagePrefix = "com.omniglot.syncbridge."
    private let storageQueue = DispatchQueue(label: "com.omniglot.syncbridge.storage", qos: .utility)
    
    @Published public private(set) var syncStatus: SyncStatus = .idle
    
    public enum SyncStatus {
        case idle
        case syncing
        case updated(Date)
        case error(String)
    }
    
    private override init() {
        super.init()
        setupNotificationObservers()
    }
    
    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleExternalMeshUpdate(_:)),
            name: .MeshDidReceiveUpdate,
            object: nil
        )
    }
    
    /// Persists a configuration key-value pair to local storage and signals the mesh
    public func persist(key: String, value: Any) {
        storageQueue.async { [weak self] in
            guard let self = self else { return }
            
            let fullKey = self.storagePrefix + key
            UserDefaults.standard.set(value, forKey: fullKey)
            UserDefaults.standard.synchronize()
            
            DispatchQueue.main.async {
                self.syncStatus = .syncing
                // Notify logic layer (ffi bridge to Rust core)
                MeshCoreBridge.shared.broadcastChange(key: key, value: value)
                self.syncStatus = .updated(Date())
            }
        }
    }
    
    /// Retrieves a value from local storage
    public func fetch(key: String) -> Any? {
        return UserDefaults.standard.object(forKey: storagePrefix + key)
    }
    
    /// Applied when an external peer pushes a change to this iOS node
    @objc private func handleExternalMeshUpdate(_ notification: Notification) {
        guard let payload = notification.userInfo as? [String: Any],
              let key = payload["key"] as? String,
              let value = payload["value"] else {
            return
        }
        
        storageQueue.async { [weak self] in
            guard let self = self else { return }
            
            let fullKey = self.storagePrefix + key
            UserDefaults.standard.set(value, forKey: fullKey)
            
            DispatchQueue.main.async {
                self.syncStatus = .updated(Date())
                // Notify UI components to re-render (e.g., Theme/Env changes)
                NotificationCenter.default.post(name: .MeshLocalDataChanged, object: nil, userInfo: [key: value])
            }
        }
    }
    
    /// Synchronizes all local data with the current mesh state on startup
    public func performFullSync() {
        self.syncStatus = .syncing
        MeshCoreBridge.shared.requestFullState { [weak self] state in
            guard let self = self, let state = state else {
                self?.syncStatus = .error("Peer connection failed")
                return
            }
            
            for (key, value) in state {
                UserDefaults.standard.set(value, forKey: self.storagePrefix + key)
            }
            
            DispatchQueue.main.async {
                self.syncStatus = .updated(Date())
            }
        }
    }
}

extension NSNotification.Name {
    static let MeshDidReceiveUpdate = NSNotification.Name("MeshDidReceiveUpdate")
    static let MeshLocalDataChanged = NSNotification.Name("MeshLocalDataChanged")
}

/**
 * Mocking the Bridge interface for compilation purposes. 
 * In production, this internal class interfaces with the Rust/C++ runtime via FFI.
 */
internal class MeshCoreBridge {
    static let shared = MeshCoreBridge()
    
    func broadcastChange(key: String, value: Any) {
        // Rust FFI call to P2P mesh gossip protocol
    }
    
    func requestFullState(completion: @escaping ([String: Any]?) -> Void) {
        // Rust FFI call to fetch state from leader node/mesh
        completion([:])
    }
}