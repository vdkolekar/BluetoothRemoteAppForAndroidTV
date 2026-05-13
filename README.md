# Bluetooth Remote App for Android TV

This project is an Android smartphone application designed to serve as a complete replacement for a physical Android TV remote control. It utilizes the Android `BluetoothHidDevice` API to broadcast the smartphone as a standard Bluetooth Human Interface Device (HID), specifically an HID Keyboard/Gamepad. 

By acting as a standard Bluetooth peripheral, the application requires **no custom receiver software** on the Android TV. The TV natively understands the Bluetooth HID inputs.

## Project Scope & Phases

**Current Focus (Phase 1):** Core App Development
*   D-Pad and standard utility button emulation (Home, Back, Volume, Power).
*   Robust Bluetooth connection state management.
*   Modern, haptic-enabled UI.

**Future Focus (Phase 2+):**
*   **Voice Search:** Routing audio over Bluetooth for voice commands (Deferred to later versions).
*   Trackpad/Gesture control mode.

## Technical Architecture

The application follows the **MVVM (Model-View-ViewModel)** architectural pattern, integrated with **Clean Architecture** principles to separate concerns, ensure testability, and maintain a robust codebase.

### 1. Technology Stack
*   **Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose
*   **Dependency Injection:** Hilt
*   **Asynchronous Programming:** Kotlin Coroutines and Flows
*   **Minimum SDK:** Android 9.0 (API Level 28) - Required for `BluetoothHidDevice` API support.

### 2. Core Components

#### A. Data/Service Layer
This layer handles all direct interactions with the Android Bluetooth framework and system hardware.

*   **`BluetoothManager` Service:** 
    *   Responsible for acquiring the `BluetoothProfile.HID_DEVICE` proxy via the system `BluetoothAdapter`.
    *   Registers the application as an HID device using `BluetoothHidDeviceAppSdpSettings` (defining the device class as a Keyboard/Remote).
    *   Exposes a Kotlin `StateFlow` representing the current connection state (Disconnected, Connecting, Connected).
*   **`ReportSender`:**
    *   Maps application button presses to standard HID usage codes.
    *   Constructs byte arrays (Reports) representing the keydown and keyup events.
    *   Utilizes the `BluetoothHidDevice.sendReport()` method to transmit these events to the connected TV.
*   **`PermissionHelper`:**
    *   Abstracts the complex logic of requesting appropriate Bluetooth permissions, distinguishing between legacy Android versions and Android 12+ (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`).

#### B. Domain/ViewModel Layer
This layer contains the business logic and state management for the UI, agnostic of the actual UI implementation.

*   **`RemoteViewModel`:**
    *   Injects the `BluetoothManager` and observes its connection state `Flow`.
    *   Exposes UI State to Jetpack Compose (e.g., `val uiState: StateFlow<RemoteUiState>`).
    *   Handles UI intents (e.g., `onDpadUpPressed()`, `onConnectClicked()`).
    *   Triggers the `ReportSender` when buttons are pressed.
    *   Manages haptic feedback requests.

#### C. Presentation Layer (Jetpack Compose)
The UI is fully declarative, reacting instantly to state changes emitted by the ViewModel.

*   **`RemoteScreen`:** The main layout containing the D-Pad, utility buttons, and connection status indicator.
*   **Custom Composables:**
    *   `DpadButton`: A customized button with specific shape and active haptic feedback.
    *   `StatusBanner`: An animated banner showing connection progress.

## How the Bluetooth HID Implementation Works

1.  **SDP Registration:** Upon launch (and granting permissions), the app registers an SDP (Service Discovery Protocol) record. This record essentially tells nearby Bluetooth devices: *"I am a device that supports HID, specifically, I am a Keyboard."*
2.  **Pairing:** The user navigates to their Android TV's Bluetooth settings and selects the smartphone to pair.
3.  **Connection:** Once paired and connected, the TV's operating system sets up an HID Host connection with the smartphone.
4.  **Interaction:** When a user taps the "Up" button on the app's D-Pad, the `ReportSender` crafts a specific byte array (e.g., `[0x00, 0x00, 0x52, 0x00, 0x00, 0x00, 0x00, 0x00]`). This byte array is defined by the USB HID Usage Tables specification for the "Up Arrow".
5.  **Transmission:** The app calls `bluetoothHidDevice.sendReport()`. The Android OS transmits this byte array over Bluetooth to the TV.
6.  **Action:** The Android TV receives the raw HID report, translates it into an Android `KeyEvent` (like `KEYCODE_DPAD_UP`), and dispatches it to the currently focused application on the TV.

## Setup Instructions (for Development)
*(Instructions will be populated once the initial project structure is generated)*
