package com.aiza.app.data

import kotlinx.coroutines.delay

/** Commands sent to a connected tag over GATT (FR-RNG-01, FR-HC-01). */
interface TagController {
    suspend fun ring(address: String, on: Boolean): Result<Unit>
    suspend fun setFindMode(address: String, on: Boolean): Result<Unit>
}

/** Pretends to talk to the tag. TODO: real implementation on platform:ble TagConnection. */
class DemoTagController : TagController {
    override suspend fun ring(address: String, on: Boolean): Result<Unit> {
        delay(350)
        return Result.success(Unit)
    }

    override suspend fun setFindMode(address: String, on: Boolean): Result<Unit> {
        delay(150)
        return Result.success(Unit)
    }
}
