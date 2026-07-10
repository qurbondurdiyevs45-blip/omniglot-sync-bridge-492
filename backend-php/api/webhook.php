<?php
/**
 * OmniGlot Sync-Bridge: PHP Webhook Bridge
 * 
 * Provides an integration point for legacy PHP applications to receive 
 * real-time configuration updates (env vars, themes) from the mesh.
 */

header('Content-Type: application/json');

// Configuration - In production, these should be environment variables
define('SYNC_BRIDGE_SECRET', getenv('SYNC_BRIDGE_SECRET') ?: 'og_bridge_default_secret');
define('LOCAL_CONFIG_PATH', __DIR__ . '/../config/omniglot_sync.json');

/**
 * Validates the incoming request signature for security.
 */
function validate_signature($payload, $signature) {
    if (!$signature) return false;
    $computed = hash_hmac('sha256', $payload, SYNC_BRIDGE_SECRET);
    return hash_equals($computed, $signature);
}

/**
 * Persists the synchronized state to a local JSON file for the PHP runtime.
 */
function update_local_sync($data) {
    if (!is_dir(dirname(LOCAL_CONFIG_PATH))) {
        mkdir(dirname(LOCAL_CONFIG_PATH), 0755, true);
    }

    $current = [];
    if (file_exists(LOCAL_CONFIG_PATH)) {
        $current = json_decode(file_get_contents(LOCAL_CONFIG_PATH), true) ?: [];
    }

    // Merge logic: Update environment and theme keys
    if (isset($data['environment'])) {
        $current['environment'] = array_merge($current['environment'] ?? [], $data['environment']);
    }
    if (isset($data['theme'])) {
        $current['theme'] = array_merge($current['theme'] ?? [], $data['theme']);
    }

    $current['last_sync_timestamp'] = time();
    $current['origin_node'] = $data['node_id'] ?? 'unknown';

    return file_put_contents(LOCAL_CONFIG_PATH, json_encode($current, JSON_PRETTY_PRINT));
}

// Main Request Handling
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['status' => 'error', 'message' => 'Only POST allowed']);
    exit;
}

$raw_payload = file_get_contents('php://input');
$signature = $_SERVER['HTTP_X_OMNIGLOT_SIGNATURE'] ?? '';

if (!validate_signature($raw_payload, $signature)) {
    http_response_code(403);
    echo json_encode(['status' => 'error', 'message' => 'Invalid signature']);
    exit;
}

$payload = json_decode($raw_payload, true);

if (json_last_error() !== JSON_ERROR_NONE || !isset($payload['sync_action'])) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'Invalid JSON payload']);
    exit;
}

try {
    switch ($payload['sync_action']) {
        case 'UPDATE_STATE':
            if (update_local_sync($payload['data'])) {
                echo json_encode(['status' => 'success', 'message' => 'State synchronized locally']);
            } else {
                throw new Exception('Failed to write bridge config file');
            }
            break;

        case 'PING':
            echo json_encode(['status' => 'pong', 'timestamp' => time()]);
            break;

        default:
            http_response_code(400);
            echo json_encode(['status' => 'error', 'message' => 'Unsupported action']);
            break;
    }
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => $e->getMessage()]);
}