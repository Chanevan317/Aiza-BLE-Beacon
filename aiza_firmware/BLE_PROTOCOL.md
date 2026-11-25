# AIZA tag BLE protocol (v1)

The contract between the tag firmware and the Android app (SRS §4.3). The app side lives in
`aiza_app/platform/ble/.../AizaGatt.kt`; the laptop stand-in tag in `aiza_app/tools/fake_tag/fake_tag.py`
implements the same thing. Change all three together. Multi-byte integers are little-endian.

## Advertising

| Mode | Interval | Payload |
| --- | --- | --- |
| Normal | 1–2 s, connectable | Flags + complete list of 128-bit UUIDs = AIZA service |
| Pairing (60 s after holding the button 2 s, FR-SET-06) | 100 ms, connectable, LE General Discoverable | Flags + 128-bit UUID = AIZA *pairing* UUID `9a990100-…` (instead of the service UUID) |
| Find (FR-HC-01) | 20–100 ms (Settings) | Same as normal. Keeps advertising while connected (non-connectable set) so the phone gets fast RSSI. |

- Legacy advertising only (C-1): 31 bytes. Flags (3) + one 128-bit UUID (18) = 21, so there is no room for a
  second 128-bit field; that is why pairing mode swaps the UUID instead of adding service data.
- Rotating resolvable private address; no name and no fixed ID in the payload (NFR-SEC-02).
- The phone resolves the address with the IRK it got at bonding and keys the tag by its identity address.

## Security

- Bonding with LE Secure Connections ("Just Works" on the R&D tag; no display or keyboard). Only accepted while in pairing mode.
- Every write below needs an encrypted link from a bonded phone (FR-RNG-03, NFR-SEC-01).

## Services

AIZA base UUID: `9a99xxxx-982c-45e1-a09e-5e8952971cdb`

| Service | Characteristic | UUID | Access | Format |
| --- | --- | --- | --- | --- |
| Immediate Alert | Alert Level | 0x1802 / 0x2A06 | Write without response, encrypted | `u8`: 0 = stop, 1 = mild, 2 = high. The tag rings at 2 and stops by itself after 30 s (FR-RNG-01). |
| Link Loss | Alert Level | 0x1803 / 0x2A06 | Read, write, encrypted | `u8`: level the tag plays when the link drops (0 = silent). |
| Battery | Battery Level | 0x180F / 0x2A19 | Read, notify | `u8` percent 0–100 (estimate from LiPo voltage). |
| Device Information | Model Number, Serial Number, Firmware Revision | 0x180A / 0x2A24, 0x2A25, 0x2A26 | Read | UTF-8 strings. Serial is the printed ID, e.g. `AZ-7E21-0C91`. |
| AIZA | (service) | `9a990000-…` | | |
| AIZA | Find Mode Control | `9a990001-…` | Write with response, encrypted | `u8 mode` (0 = normal, 1 = find) + `u16 timeout_s`. The tag returns to normal by itself after `timeout_s` (NFR-SEC-03); the app sends 300. |
| AIZA | Button Event | `9a990002-…` | Notify | `u8 event` (1 = short press) + `u8 counter` (wraps; lets the app drop repeats). |
| AIZA | Settings | `9a990003-…` | Read, write, encrypted | `u16 find_interval_ms` + `u16 find_max_s` + `i8 tx_power_dbm`. For experiments. |
