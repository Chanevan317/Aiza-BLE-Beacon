# AIZA BLE Tag – Software Requirements Specification (R&D Prototype)

Nov 18, 2026 · @CHAN

Version 0.1 (draft). This document specifies what the AIZA research prototype must do: a BLE tag built on a XIAO nRF52840 and an Android app written in Kotlin that guide a person to a lost item with hot/cold feedback, a ring button and a fixed 120° direction wedge. Feasibility findings and supporting evidence are in the appendices.

## 1. Introduction

### 1.1 Purpose

This SRS tells the developer what to build and tells reviewers how each requirement will be checked. It covers the tag firmware, the Android app, the direction-finding system and the tools for running trials.

### 1.2 Scope

AIZA is a research prototype, not a product for sale. Its research question is whether a phone can show a fixed 120° direction wedge that contains a BLE tag at least 90% of the times it is shown, using only signal strength and the phone's motion sensors.

| In scope | Out of scope |
| --- | --- |
| Tag firmware on the XIAO nRF52840 | iPhone / iOS app |
| Android app in Kotlin | Commercial certification and production hardware |
| Hot/cold guidance, ring, left-behind alert | Apple Find My and Google Find Hub networks |
| 120° direction wedge and optional spin | UWB and Bluetooth 5.1 angle-of-arrival hardware |
| Trial mode, data logging and offline evaluation | Cloud servers and user accounts |

### 1.3 Definitions

| Term | Meaning |
| --- | --- |
| BLE | Bluetooth Low Energy, the low-power radio used by the tag and phone |
| Advertising | Short packets the tag broadcasts so phones can detect it without connecting |
| GATT | The BLE system of services and characteristics a phone reads, writes or subscribes to after connecting |
| RSSI | Received signal strength in dBm; rises as the phone gets closer to the tag |
| Normal mode | Tag advertises slowly (every 1–2 s) to save power |
| Find mode | Tag advertises fast (every 20–100 ms) during a search, at the owner's request |
| Hot/cold | Guidance telling the user whether they are getting closer (warmer) or further away (colder) |
| Wedge | A fixed 120° sector on the find screen showing the likely direction of the tag |
| PDR | Pedestrian dead reckoning: estimating the walked path from steps and heading |
| Particle filter | A probabilistic method that tracks many guesses of the tag's position and weights them by evidence |
| Channel Sounding | Bluetooth 6.0 feature for precise distance measurement; optional here |
| DULT | IETF draft standard so phones can warn people about unknown trackers travelling with them |

### 1.4 References

- AIZA finding design decisions, 18 Nov 2026 (project doc *aiza-finding-design-decisions*)
- [Kandula et al. 2024](https://par.nsf.gov/servlets/purl/10574078): single-phone RSSI direction finding by spinning
- [Ren et al. 2026, "Snatcher"](https://arxiv.org/pdf/2606.21067): RSSI and walking-path guidance to a BLE device
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [IETF DULT accessory protocol draft](https://datatracker.ietf.org/doc/html/draft-ietf-dult-accessory-protocol-01)

## 2. Overall description

### 2.1 Product perspective

The system has three parts: the tag, the Android app, and offline tools on a computer. The app is split so that the direction-finding logic can be tested on recorded data without a phone.

&#91;embedded content: AIZA system context · tag, app layers, offline tools\]

The platform layer turns BLE and sensor data into timestamped samples; the direction core turns samples into hot/cold and wedge results; the offline tools replay recorded samples through the very same core.

### 2.2 Product functions

- Pair, name, unpair and reset tags
- Show each tag's status, battery level and last-seen place
- Ring the tag from the app, and ring the phone from the tag's button
- Warn the user when they leave a tag behind
- Guide a search with hot/cold feedback, the ring and the 120° wedge, with an optional spin
- Run trials: record signal, motion and ground truth, and export logs for offline evaluation

### 2.3 Users

| User | Goal | Technical skill |
| --- | --- | --- |
| Owner | Find a lost item quickly | None assumed |
| Trial volunteer | Search for a hidden tag while the app records data | None assumed; gives written consent |
| Researcher | Set up trials, enter ground truth, export and analyse logs | High |

### 2.4 Operating environment

- **Tag:** XIAO nRF52840 with a piezo buzzer, a push button and a small LiPo cell. Firmware built with Nordic's nRF Connect SDK (Zephyr) is recommended for its BLE stack and over-the-air updates.
- **Phones:** Android 12 (API 31) or later with Bluetooth 5.x, an accelerometer, a gyroscope and a magnetometer. Testing uses at least two phone brands.
- **Places:** indoor homes and offices in India, with the tag stationary during a search.

### 2.5 Design and implementation constraints

| ID | Constraint |
| --- | --- |
| C-1 | BLE only, using legacy advertising on LE 1M PHY. No dependence on Coded PHY or extended advertising. |
| C-2 | Android only. The app is written in Kotlin with Jetpack Compose. |
| C-3 | The direction-finding core is a pure Kotlin module with no Android imports, so it can run on a computer against recorded logs. |
| C-4 | The direction wedge is always exactly 120° wide. |
| C-5 | No cloud services. Data stays on the phone and the researcher's computer. |
| C-6 | The XIAO nRF52840 has no Channel Sounding. Channel Sounding is possible only with an optional XIAO nRF54L15 and a supporting phone such as a Pixel 9 or 10. |

### 2.6 Assumptions and dependencies

| ID | Assumption |
| --- | --- |
| A-1 | The user holds the phone in hand with the screen on during a search. |
| A-2 | The tag does not move during a search. |
| A-3 | In a low-latency scan the phone reports RSSI for each advertisement it receives (to verify on each test phone). |
| A-4 | Signal-strength and motion-sensor timestamps can be placed on one common clock (to verify on each test phone). |
| A-5 | A search starts only once the phone can hear the tag; finding beyond Bluetooth range is out of scope. |

## 3. Functional requirements

Each requirement says what the system shall do, how important it is, and how it will be checked. **Test** means a measured check, **Demo** means showing it works, **Inspection** means reviewing code or screens, and **Offline** means replaying recorded trial logs. Numbers such as times and thresholds are starting targets, to be tuned after the first trials.

### 3.1 Tag setup and management

| ID | Requirement | Priority | Verified by |
| --- | --- | --- | --- |
| FR-SET-01 | The app shall add a tag through the Android system pairing dialog (CompanionDeviceManager) and bond with it using LE Secure Connections. | Must | Test |
| FR-SET-02 | The user shall be able to name each tag and choose an icon. | Must | Demo |
| FR-SET-03 | The app shall unpair a tag on request and delete its stored data. | Must | Test |
| FR-SET-04 | Holding the tag's button for 5 s shall factory-reset the tag and clear its bonds. | Must | Test |
| FR-SET-05 | The tag shall beep or blink to confirm pairing, entering find mode and reset. | Should | Demo |

### 3.2 Item status

| ID | Requirement | Priority | Verified by |
| --- | --- | --- | --- |
| FR-STA-01 | The home screen shall list every tag as "Nearby" or "Last seen" with a time. | Must | Test |
| FR-STA-02 | The app shall show the tag's battery level and warn when it falls below 20%. | Must | Test |
| FR-STA-03 | The app shall save the phone's location when it last heard each tag and show it on a map. | Should | Test |

### 3.3 Ring

| ID | Requirement | Priority | Verified by |
| --- | --- | --- | --- |
| FR-RNG-01 | Tapping Ring shall make a connected tag beep within 2 s; it stops on a second tap or after 30 s. | Must | Test |
| FR-RNG-02 | A short press of the tag's button shall make the phone ring. | Should | Test |
| FR-RNG-03 | Only the bonded phone shall be able to ring the tag or switch it to find mode. | Must | Test |

### 3.4 Left-behind alert

| ID | Requirement | Priority | Verified by |
| --- | --- | --- | --- |
| FR-SEP-01 | When the alert is on, the app shall keep a connection to the tag through a foreground service of type connectedDevice. | Must | Inspection |
| FR-SEP-02 | The app shall notify the user within 30 s of the connection dropping or the signal staying below a set threshold. | Must | Test |
| FR-SEP-03 | The user shall be able to switch the alert on or off for each tag. | Must | Demo |

### 3.5 Search: hot/cold guidance

| ID | Requirement | Priority | Verified by |
| --- | --- | --- | --- |
| FR-HC-01 | Opening the find screen shall switch the tag to find mode; leaving the screen, or 5 minutes without use, shall switch it back to normal mode. | Must | Test |
| FR-HC-02 | The app shall run one continuous low-latency scan per search and shall never start more than 5 scans in 30 s. | Must | Inspection |
| FR-HC-03 | The hot/cold meter shall update at least twice per second from filtered signal strength (median of each 250 ms window). | Must | Test |
| FR-HC-04 | Sound and vibration shall speed up as the signal gets stronger, and the user shall be able to mute them. | Must | Demo |
| FR-HC-05 | The Ring button shall stay visible for the whole search. | Must | Inspection |

### 3.6 Search: direction wedge

| ID | Requirement | Priority | Verified by |
| --- | --- | --- | --- |
| FR-DIR-01 | The app shall estimate the walked path from steps (detected from raw accelerometer data) and heading (gyroscope and magnetometer). | Must | Offline |
| FR-DIR-02 | The app shall label each straight stretch of walking as hotter, colder or unclear, and ignore readings taken while the user turns. | Must | Offline |
| FR-DIR-03 | A probabilistic filter shall estimate the tag's position and the probability P that the tag lies inside the wedge. | Must | Offline |
| FR-DIR-04 | The wedge shall always be exactly 120° wide, centred on the most likely direction. | Must | Inspection |
| FR-DIR-05 | The wedge shall appear only after P has stayed at or above the threshold (default 0.9, set from trials) for 2 s, and shall fade out rather than vanish. | Must | Offline |
| FR-DIR-06 | The wedge shall be hidden when hot/cold contradicts it, after the user walks about 3 m from where it was estimated, or when the tag is estimated to be within about 1 m. | Must | Test |
| FR-DIR-07 | When no wedge is shown, the screen shall prompt the user to keep walking. | Must | Demo |
| FR-DIR-08 | The wedge shall rotate as the phone turns, so it keeps pointing the same way in the room. | Must | Test |

### 3.7 Search: optional spin

| ID | Requirement | Priority | Verified by |
| --- | --- | --- | --- |
| FR-SPN-01 | A "Turn slowly" button shall guide a 360° turn and record signal strength against heading in 5° bins. | Should | Demo |
| FR-SPN-02 | The spin result shall feed the filter as a starting estimate and shall not override evidence from walking. | Should | Offline |

### 3.8 Trials and research data

| ID | Requirement | Priority | Verified by |
| --- | --- | --- | --- |
| FR-TRL-01 | A researcher mode shall start and stop trials and record the trial ID, phone model, room and starting distance. | Must | Demo |
| FR-TRL-02 | The researcher shall be able to enter ground truth: the tag's true position and checkpoints on the walked path. | Must | Demo |
| FR-TRL-03 | The app shall log every signal-strength sample, sensor reading, heading, wedge decision and user action with a timestamp. | Must | Inspection |
| FR-TRL-04 | Logs shall be exportable as CSV files. | Must | Test |
| FR-TRL-05 | Volunteers shall see a consent screen before a trial and be able to delete their data afterwards. | Must | Demo |
| FR-TRL-06 | Offline tools shall replay logs through the same direction-finding core and report wedge hit rate, how often no wedge was shown, and time to find. | Must | Offline |

### 3.9 Optional: Channel Sounding

| ID | Requirement | Priority | Verified by |
| --- | --- | --- | --- |
| FR-CS-01 | On supporting phones, the app may use Android's ranging API with a XIAO nRF54L15 tag to show an accurate distance. | Optional | Test |

## 4. External interfaces

### 4.1 User interface

The app has six screens. All text supports large font sizes and screen-reader labels, and colour is never the only cue.

| Screen | What it shows | Main actions |
| --- | --- | --- |
| Home | All tags with status and battery | Open a tag, add a tag |
| Add tag | System pairing dialog, then name and icon | Pair, name, choose icon |
| Tag detail | Status, battery, last-seen map, alert switch | Ring, Find, unpair |
| Find | Hot/cold meter, 120° wedge or "keep walking" prompt, sound and vibration | Ring, Turn slowly, mute, stop |
| Settings | Alert threshold, sound, help | Change settings, open help |
| Researcher mode | Trial setup, ground-truth entry, log list | Start/stop trial, mark checkpoint, export CSV, delete data |

### 4.2 Hardware interfaces (tag)

| Part | Connection | Purpose |
| --- | --- | --- |
| XIAO nRF52840 | — | BLE radio and processor; charges the LiPo over USB-C |
| Piezo buzzer | GPIO through an NPN transistor, driven with PWM | Ring and audio cues |
| Push button | GPIO with pull-up | Ring phone, pairing, factory reset |
| LiPo cell (3.7 V) | Battery pads on the XIAO | Power |
| Battery sensing | Analogue input through a voltage divider | Battery level for the Battery Service |
| On-board LED | Built in | Status cues |

Pin numbers will be fixed in the hardware schematic.

### 4.3 Communication interface (BLE)

**Advertising**

| Mode | Interval | Content | TX power |
| --- | --- | --- | --- |
| Normal | 1–2 s | AIZA 128-bit service UUID; rotating private address | Firmware default |
| Find | 20–100 ms | Same, optionally on one advertising channel only | Fixed for the whole search |

**GATT services**

| Service (UUID) | Characteristic | Access | Use |
| --- | --- | --- | --- |
| Immediate Alert (0x1802) | Alert Level (0x2A06) | Write | Ring the tag (FR-RNG-01) |
| Link Loss (0x1803) | Alert Level (0x2A06) | Read, write | Tag-side cue when the link drops (FR-SEP) |
| Battery (0x180F) | Battery Level (0x2A19) | Read, notify | Battery level (FR-STA-02) |
| Device Information (0x180A) | Model, firmware version | Read | Logged with each trial |
| AIZA (custom 128-bit, to be assigned) | Find Mode Control | Write, bonded only | Start or stop find mode, with a time limit (FR-HC-01) |
| AIZA | Button Event | Notify | Tag button pressed: ring phone (FR-RNG-02) |
| AIZA | Settings | Read, write, bonded only | Find-mode interval and duration for experiments |

### 4.4 Software interfaces (Android)

| Interface | Used for | Note |
| --- | --- | --- |
| BLUETOOTH\_SCAN, BLUETOOTH\_CONNECT | Scanning and connecting | Runtime permissions from Android 12 ([Android Developers](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)) |
| ACCESS\_FINE\_LOCATION | Last-seen map; direction estimate | Android allows skipping location only if an app never derives physical location from Bluetooth scans ([Android Developers](https://developer.android.com/about/versions/12/features/bluetooth-permissions)); AIZA does, so it requests location |
| POST\_NOTIFICATIONS | Left-behind alerts | Runtime permission on Android 13+ |
| Foreground service, type connectedDevice | Keeping the tag connected | Type and the FOREGROUND\_SERVICE\_CONNECTED\_DEVICE permission are mandatory from Android 14 ([Android Developers](https://developer.android.com/guide/components/fg-service-types)) |
| CompanionDeviceManager | Pairing; waking the app when the tag comes into range | Uses startObservingDevicePresence ([Android Developers](https://developer.android.com/develop/connectivity/bluetooth/ble/background)) |
| SensorManager | Accelerometer, gyroscope, rotation vector | Raw sensors need no activity-recognition permission ([Android Developers Blog](https://android-developers.googleblog.com/2019/11/privacy-protections-for-physical.html)) |
| Android ranging API | Optional Channel Sounding distance | Pixel 9/10 on Android 16 QPR2 or later |

## 5. Non-functional requirements

These describe how well the system must work. The accuracy requirement (NFR-ACC-01) is the research question itself.

| ID | Area | Requirement | Target | Verified by |
| --- | --- | --- | --- | --- |
| NFR-ACC-01 | Accuracy | When the wedge is shown, the tag shall be inside it. | At least 90% of the times shown (random chance is 33%) | Offline, on homes and phones not used for tuning |
| NFR-ACC-02 | Accuracy | The app shall prefer showing no wedge over showing a doubtful one. | Report how often no wedge is shown; no fixed target | Offline |
| NFR-PER-01 | Responsiveness | Hot/cold updates | At least 2 per second | Test |
| NFR-PER-02 | Responsiveness | Ring response | Tag beeps within 2 s of the tap | Test |
| NFR-PER-03 | Responsiveness | Direction core speed | Processes each update faster than real time on a mid-range phone | Test |
| NFR-PWR-01 | Power | Tag runtime on a small LiPo | A full day of trials | Test |
| NFR-PWR-02 | Power | Tag current recorded in normal and find mode | Measured and logged for later design work | Test (power analyser) |
| NFR-SEC-01 | Security | Only the bonded phone can ring the tag, start find mode or change settings | No success from an unbonded phone | Test |
| NFR-SEC-02 | Security | Advertisements carry no fixed identifier | Address rotates; no static ID in the payload | Inspection |
| NFR-SEC-03 | Security | Find mode ends by itself | Time limit enforced by the tag | Test |
| NFR-PRV-01 | Privacy | Trial data stays local and is anonymised | Logs hold no names; stored only on the phone and the researcher's computer | Inspection |
| NFR-PRV-02 | Privacy | Volunteers give consent and can delete their data | Consent recorded before every trial | Demo |
| NFR-REL-01 | Reliability | Left-behind alert works with the app in the background | Alert fires with the screen off on every test phone | Test |
| NFR-COM-01 | Compatibility | Supported phones | Android 12 or later; at least two brands tested | Test |
| NFR-USE-01 | Usability | A first-time user can start a search without help | Observed in user trials | Demo |
| NFR-USE-02 | Accessibility | Large text, screen-reader labels, no colour-only cues | All screens | Inspection |
| NFR-MNT-01 | Maintainability | The direction-finding core runs on a computer as well as the phone | Pure Kotlin module with no Android imports | Inspection |
| NFR-MNT-02 | Maintainability | Recorded logs can be replayed and give the same result each time | Identical output on repeated replay | Offline |
| NFR-DAT-01 | Data quality | Signal-strength and sensor samples share one timeline | A single time base for all samples, checked on each phone | Test |

## 6. Verification and acceptance

The prototype is accepted when every Must requirement passes its check and NFR-ACC-01 has been measured. If the wedge misses its 90% target, that is a valid research result; the prototype then ships with hot/cold and the ring only.

### 6.1 Trial protocol

| Factor | Values |
| --- | --- |
| Places | At least three homes or offices, several rooms each |
| Starting distance | 1–10 m from the tag |
| Line of sight | Clear, and blocked (walls, furniture, tag inside a bag or drawer) |
| Phones | At least two Android brands |
| Search style | Walking only, and walking plus the optional spin |
| Trials | About 100 per condition, giving roughly ±6% uncertainty on a 90% result |
| Ground truth | Tag position measured; walked path marked with checkpoints |
| Consent | Written consent from every volunteer before their first trial |

### 6.2 Metrics

| Metric | How it is computed |
| --- | --- |
| Wedge hit rate | Share of shown wedges that contained the true tag direction; compared with 33% chance |
| No-wedge rate | Share of search time with no wedge shown |
| Time to find | Seconds from opening the find screen to touching the tag |
| Path efficiency | Straight-line distance divided by distance walked |
| Wrong turns | Times the user walked away from the tag for more than a few steps |
| With vs without wedge | The same measures in trials with the wedge switched off |

### 6.3 Development plan and gates

The work runs in four phases, each ending with a pass/fail check. The direction algorithm is tuned and tested offline on recorded logs before it goes into the live app.

&#91;embedded content: AIZA plan · 4 phases, 3 gates\]

**First actions**

1. Wire a piezo buzzer (through a transistor) and a small LiPo to the XIAO nRF52840.
2. Write firmware for normal mode, owner-triggered find mode, ring and separation alert, with rotating identifiers from the start.
3. Test it with Nordic's nRF Connect phone app on two Android phones.
4. Build the Android logging app, then grow it into the finding app.
5. Write the trial protocol and a one-page consent form for volunteers.

## Appendix A. Feasibility: radio and phone compatibility

**Overall verdict:** a working prototype is feasible with the XIAO nRF52840 and an Android app. The one open research question is whether the 120° wedge meets NFR-ACC-01. Appendices A–H keep the feasibility findings behind this SRS.

Feasible. Everything the design depends on uses BLE features that every iOS and Android phone exposes to apps. The features phones don't expose are already excluded from the design. Testing is Android only; the iPhone column is kept for reference.

| Feature | iPhone | Android | Role in AIZA |
| --- | --- | --- | --- |
| Legacy advertising (1M PHY) | Yes | Yes | Baseline for everything |
| GATT: Immediate Alert, Link Loss, Battery | Yes | Yes | Ring, separation alert, battery level |
| RSSI while app is open | Yes, every packet | Yes | Hot/cold and direction |
| RSSI in background | Throttled, duplicates merged | Throttled | Not used for finding |
| Coded PHY (long range) | No | Inconsistent | Not used |
| Extended advertising | Unreliable | Mostly | Not used |
| AoA/AoD direction finding | No app API | No app API | Not used |
| Channel Sounding | iPhone 17, iOS 27, app open | Pixel 9/10 on Android 16 QPR2, a few others | Optional add-on, needs a newer chip |

**Constraints the app must handle**

- **iOS background:** apps can't scan in the background without a declared background mode, and even then duplicate packets are merged ([Apple](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html)). Finding therefore runs with the app open; separation alerts use a kept-open GATT connection.
- **Android scan limit:** more than five scan starts in 30 seconds silently blocks results ([Punch Through](https://punchthrough.com/android-ble-scan-filter/)). Each search uses one long low-latency scan.
- **Coded PHY:** phones can report support yet fail to scan Coded PHY advertisements ([Nordic DevZone](https://devzone.nordicsemi.com/f/nordic-q-a/70038/android-nrf-connect---proper-indication-of-coded-phy-support/287628)), so long-range mode is not relied on.

**Residual risk:** phone makers' power-saving changes can break background behaviour between OS releases. Test on the top phones sold in India each release cycle.

## Appendix B. Feasibility: finding and direction

Likely feasible, not yet proven. Two published studies show that signal strength plus phone motion can guide a person to a BLE device, but neither tested our exact setup, homes in India, or the confidence-gated 120° wedge.

| Evidence | Method | Result | Gap for AIZA |
| --- | --- | --- | --- |
| [Kandula et al. 2024](https://par.nsf.gov/servlets/purl/10574078) | User spins in place, signal strength vs compass heading | Mean error 24.8°; 12 of 13 within ±60° (95% interval about 67–99%) | 13 trials, one iPhone, slow spins, no confidence score |
| [Ren et al. 2026, “Snatcher”](https://arxiv.org/pdf/2606.21067) | Signal map along the walked path, short + long-term trend combined | 90% success from 30 m; 73–150 s to reach the device; path 2–3× the straight line | Targets advertised every \~2 s; experimenters, not everyday users |

**Why the physics supports it**

- **Hot/cold:** signal strength rises as the user nears the tag. With a standard indoor model, a 1 m step changes it by about 1–1.4 dB at 10 m but 6–9 dB at 2 m, so guidance sharpens as the user gets close.
- **Body blocking:** the user's body attenuates 2.4 GHz signals, so facing toward or away from the tag changes the reading. This is what makes the spin and walking methods work.
- **More samples than Snatcher:** AIZA's find mode advertises 20–100× more often than the \~2 s devices in that study, which should speed up convergence (our inference, untested).

**What must be proven in our trials**

1. The wedge contains the tag at least 90% of the times it is shown, against 33% for chance.
2. The app correctly stays silent (no wedge) when evidence is weak.
3. Time to find with the wedge is shorter than with hot/cold alone.

**Biggest technical risk:** reflections in cluttered rooms and drift in step-based path tracking. Both reduce how often the wedge can be shown; neither should make it wrong if the confidence threshold is calibrated.

## Appendix C. Prototype platform: XIAO nRF52840

The XIAO nRF52840 can prototype the whole agreed design except Channel Sounding. It is enough to build the working prototype; only final size and coin-cell battery life would need a custom board, which is outside this study.

**What it is:** a 21 × 17.8 mm board with the Nordic nRF52840, 1 MB flash and 256 KB RAM plus 2 MB external flash, a BQ25101 LiPo charger, and a stated deep-sleep current under 5 µA ([Seeed datasheet](https://atta.szlcsc.com/upload/public/pdf/source/20260724/EF9B64229EB34B531643B5029927D518.pdf), [Kiwi Electronics](https://www.kiwi-electronics.com/en/seeed-studio-xiao-nrf52840-11041)). The Sense variant adds an IMU and microphone.

| Need | XIAO nRF52840 | Notes |
| --- | --- | --- |
| Legacy advertising, normal and find mode | Yes | Interval and TX power set in firmware |
| GATT services (Immediate Alert, Link Loss, Battery) | Yes | Standard in Nordic and Zephyr stacks |
| Secure pairing, rotating private address | Yes | Standard BLE features of the chip |
| Buzzer | No | Add a small piezo buzzer and driver transistor |
| Battery power | Yes, LiPo | Use a small 3.7 V LiPo on the board's battery pads; the on-board charger recharges it over USB-C |
| Channel Sounding | No | Needs the nRF54L series |
| Final size and battery-life numbers | No | Board LEDs, charger and regulator add current a production tag won't have |

**Upgrade path in the same form factor:** the [XIAO nRF54L15](https://wiki.seeedstudio.com/xiao_nrf54l15_sense_getting_started/) supports Bluetooth 6.0 including Channel Sounding. Developers report trouble getting Nordic's Channel Sounding samples running on it ([Nordic DevZone](https://devzone.nordicsemi.com/f/nordic-q-a/124818/channel-sounding-reflector-sample-not-initializing-on-xiao-nrf54l15/551025)), so treat it as a phase 2 experiment. iPhone Channel Sounding also needs Core 6.3 inline PCT support, which must be confirmed for this board's software stack.

**Recommendation:** run phase 1 (hot/cold, walking direction, spin, wedge calibration) on the XIAO nRF52840 you already have. Buy one or two XIAO nRF54L15 boards only if Channel Sounding stays in scope.

## Appendix D. Power

Power is not a constraint for the prototype: a small LiPo on the XIAO comfortably runs a day of trials. Coin-cell battery life matters only if AIZA later becomes a product.

- **Chip-level estimate:** about 12–26 µA average in normal mode (advertising every 1–2 s), and 0.27–0.41 mA in find mode at 50 ms intervals. Built from 13.3–20.4 µC per advertising event plus about 3 µA idle ([Nordic DevZone](https://devzone.nordicsemi.com/f/nordic-q-a/80728/apparent-discrepancy-between-online-power-profiler-measurements/334746)).
- **The XIAO adds more:** its LEDs, charger and regulator draw extra current. Even at 1 mA total, a 100 mAh LiPo lasts about 100 hours (our arithmetic).
- **Worth recording as research data:** measure the prototype's current in each mode with a power analyser (for example Nordic's PPK2), so any later product design starts from real numbers.
- **For later reference only:** a CR2032 is rated 235 mAh ([Energizer](https://data.energizer.com/pdfs/cr2032.pdf)). At the estimates above, a future coin-cell tag would last roughly 9–28 months, depending on advertising interval and how much radio pulses reduce usable capacity.

## Appendix E. Prototype budget

The parts cost a few thousand rupees; the real cost is development time for firmware, the app, the direction algorithm and trials. Testing is Android only, so no Mac or Apple developer account is needed.

| Item | Cost | Notes |
| --- | --- | --- |
| XIAO nRF52840 (already owned) | — | About ₹1,150 each ([ThinkRobotics](https://thinkrobotics.com/products/seeed-studio-xiao-nrf52840)); a second board helps for spares and multi-tag tests |
| XIAO nRF54L15 (optional, Channel Sounding) | ₹1,337 incl. GST | [Hubtronics](https://hubtronics.in/seeed-studio/seeed-studio-xiao-nrf54l15?page=3) |
| Piezo buzzer, transistor, push button, small LiPo, wiring, simple case | A few hundred rupees | Rough estimate, not quoted |
| Power analyser (e.g. Nordic PPK2) | Optional, to be quoted | Only to record current draw for later design work |
| Test phones | Existing phones | At least two Android models from different brands; borrow more for trials |
| App development and installs | Free | Android Studio is free, and test builds install directly on Android phones |

**Not needed for the prototype (only if AIZA is commercialized later):** Bluetooth SIG qualification ($12,000 per design, [Bluetooth SIG](https://www.bluetooth.com/fee-schedule/)), WPC ETA approval (₹10,000 government fee plus an RF test report, [Silvereye](https://blogs.seyecs.com/wpc-eta-approval-process/)), and a production radio chip at about $1.3–2.3 per unit ([LCSC](https://lcsc.com/datasheet/lcsc_datasheet_2404241239_Nordic-Semicon-NRF52805-CAAA-B-R_C7321643.pdf), [Avnet](https://www.avnet.com/americas/product/nordic-semiconductor/nrf54l15-qfaa-r7/evolve-117084018/)).

## Appendix F. Rules during R&D (India)

As far as we found, an unsold research prototype needs no product certification. What does apply is responsible treatment of trial volunteers and their data. Confirm with a compliance adviser before any public demo or giveaway.

| Area | During R&D | Source |
| --- | --- | --- |
| Radio approval (WPC ETA) | An exemption for prototypes is reported; full approval applies to products placed on the market | [LegitEye](https://legiteye.com/eta-for-delicensed-frequencies-in-india-adding-complexity-or-ensuring-ease-of-business) |
| Bluetooth SIG qualification | Required for companies making available a product that uses Bluetooth or its trademarks; not for internal prototypes | [TÜV SÜD](https://www.tuvsud.com/en-us/services/testing/bluetooth-qualification) |
| Battery rules (EPR, BIS) | Aimed at producers placing batteries or battery-powered equipment on the market; a retail LiPo used in the lab is outside that | [Mondaq](https://www.mondaq.com/india/waste-management/1547088/navigating-indias-battery-waste-regulation) |
| Volunteers' data | Location traces and motion data are personal data; DPDP core duties start 13 May 2027, but written consent, minimal collection and anonymised logs are good practice now | [Khaitan & Co](https://www.khaitanco.com/sites/default/files/2025-11/ERGO%20-%20Digital%20Personal%20%20Data%20Protection%20Rules%20-%2015%20November%202025.pdf) |
| Misuse | Tag only your own items; never track a person without consent. Monitoring a woman's electronic communications is stalking under BNS Section 78 | [Advocate Khoj](https://www.advocatekhoj.com/library/bareacts/bharatiyanyayasanhita/78.php) |
| Anti-stalking standard (DULT) | Not needed for the prototype; use rotating identifiers from the start so it is easy to add | [IETF draft](https://datatracker.ietf.org/doc/html/draft-ietf-dult-accessory-protocol-01) |

**If AIZA is commercialized later:** WPC ETA, Bluetooth SIG qualification, battery EPR registration, a DPDP-compliant app and DULT support all become necessary.

## Appendix G. Risks and mitigations

The biggest practical risk is variation between Android phone models and brands. The biggest research risk is that the wedge is shown too rarely to be useful; either outcome is still a valid research result.

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Phone makers' battery savers stop the left-behind service on some Android phones | High | Medium | Test several brands; use companion-device wake-ups; guide users to exempt the app from battery saving |
| Wedge correct less than 90% of the time, or shown too rarely | Medium | Medium | Tune the confidence threshold on logged data; the prototype still works with hot/cold and the ring |
| Signal strength differs between phone models | High | Medium | Test at least three models; rely on changes in signal, not absolute levels |
| Indoor reflections and step-tracking drift | High | Low | Lower confidence hides the wedge instead of misleading |
| Too little or messy trial data for offline work | Medium | Medium | Fix the trial protocol and ground-truth method before collecting |
| Channel Sounding hard to get working (XIAO nRF54L15, iPhone needs Core 6.3) | High | Low | Optional experiment only |
| Volunteers' location data mishandled | Low | Medium | Written consent, anonymised logs, delete raw data after analysis |
| Strangers locate the tag from its signal (Snatcher-style) | Low during R&D | Medium | Rotating identifiers and authenticated find mode from the first firmware |
| Patent claims on turn-around direction finding (Stick-N-Find) | Low during research | High if commercialized | Freedom-to-operate review before any commercial step |

## Appendix H. Sources

Research papers (full text read):

- [Kandula et al. 2024, Localization of a BLE Device Based on Single-Device RSSI and DOA Measurements](https://par.nsf.gov/servlets/purl/10574078) · [MDPI page](https://mdpi.com/2673-8732/4/2/10)
- [Ren et al. 2026, Snatcher: Apple Find My Network Exposes Your Lost Devices To Strangers](https://arxiv.org/pdf/2606.21067)

Standards, vendors and regulators:

- [Bluetooth SIG fee schedule](https://www.bluetooth.com/fee-schedule/)
- [IETF DULT accessory protocol draft](https://datatracker.ietf.org/doc/html/draft-ietf-dult-accessory-protocol-01)
- [Apple Core Bluetooth background processing](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html)
- [Nordic Academy: advertising interval and power](https://academy.nordicsemi.com/courses/designing-low-power-bluetooth-le-products/lessons/lesson-4-bluetooth-le-power-optimization/topic/bluetooth-le-advertising-parameters-and-power-consumption/)
- [Energizer CR2032 datasheet](https://data.energizer.com/pdfs/cr2032.pdf)
- [Seeed XIAO nRF54L15 wiki](https://wiki.seeedstudio.com/xiao_nrf54l15_sense_getting_started/)
- [Customs instruction 17/2023 on battery EPR](https://www.taxmanagementindia.com/visitor/detail_circular.asp?ID=66349)
- [BNS Section 78, stalking](https://www.advocatekhoj.com/library/bareacts/bharatiyanyayasanhita/78.php)

Other figures (prices, WPC process, DPDP timeline, phone behaviour) are linked beside the tables where they appear. Several come from distributor listings, consultants or developer forums rather than primary sources; verify those before committing budget.
