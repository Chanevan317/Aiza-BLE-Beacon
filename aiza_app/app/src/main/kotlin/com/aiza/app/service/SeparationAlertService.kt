package com.aiza.app.service

import androidx.lifecycle.LifecycleService

/**
 * Foreground service of type connectedDevice that keeps tags connected for the left-behind alert
 * (FR-SEP-01, FR-SEP-02, NFR-REL-01).
 * TODO: startForeground with ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, watch link loss / RSSI.
 */
class SeparationAlertService : LifecycleService()
