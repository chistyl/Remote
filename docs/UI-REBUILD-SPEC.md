# CHI TV Remote UI Rebuild Specification

## Product direction
Use the uploaded Remote_ATV_5.0.5 APK as the visual and feature reference. Rebuild the experience in the existing native Android / Jetpack Compose app. Do not embed or redistribute the reference APK.

## Non-negotiable product requirements
- Preserve the existing Android TV discovery, pairing, certificate persistence, secure remote channel, reconnect and text-send flows.
- Do not gate or arbitrarily limit remote features.
- Do not show update prompts, update banners, forced-update dialogs, or update announcements.
- Support these seven UI languages: Traditional Chinese, Simplified Chinese, English, Japanese, Korean, Thai, Vietnamese.
- Language default: follow the Android system locale. If unsupported, fall back to Traditional Chinese. Provide an in-app language setting with a "Follow system" option and persist an explicit manual choice.
- Provide HDMI input-source selection, using the TV's actual supported input list/API where available. Never report success solely because a key event was sent.
- Provide a dedicated mouse / touchpad mode, clearly distinguishing real pointer support from D-pad gesture fallback.

## Navigation target
1. Remote
2. Mouse / Touchpad
3. Keyboard
4. Media
5. HDMI / Inputs
6. Devices
7. Settings

## Reference APK asset inventory
The extracted Flutter asset bundle contains 38 SVG icons:
back, brightness, check, close, color_temperature, fast-forward, feedback, file_document_check, hdr, home, info, input, keyboard, light_sensor, mute, no_connected_device, picture_mode, picture_options, play-pause, power, power_savings, power_savings_high, power_savings_low, power_savings_off, return, review, rewind, rocket, search, settings, share, shield_lock, skip-next, skip-previous, subpage_arrow, television, volume, workspace_premium.
It also includes Open Sans Light, Regular and SemiBold fonts.

## Existing CHI implementation audit
The current Compose app already contains:
- mDNS discovery and manual IP entry.
- Pairing and persisted paired-device reconnect.
- Remote v2 key injection and text input.
- D-pad, Home, Back, Recent, volume, mute, media, power shortcuts.
- HDMI 1–4 shortcuts sent as keycodes.
- A touch area that maps swipe gestures to D-pad directions and tap to OK.

The current touch area is not a true mouse: it sends D-pad key events and does not move a TV pointer. HDMI shortcuts are also keycode attempts, not confirmed input-source switching. These must be represented honestly in the UI.

## Implementation sequence
### Phase A — UI foundation
- Introduce a centralized locale resolver and string catalogue.
- Follow system locale by default; persist manual language choice.
- Add Settings and language selection without changing the pairing protocol.
- Build a reusable dark UI system using the existing black / cyan visual identity and the reference icon family.

### Phase B — Remote modes
- Separate D-pad remote and mouse/touchpad screens.
- Mouse surface: relative pointer gestures, click, double-click, press-and-drag, two-finger scroll, adjustable pointer speed, left/right buttons.
- Detect/declare whether a TV supports actual pointer input. If no supported transport exists, label fallback as D-pad navigation; do not claim a true mouse.
- Separate HDMI source picker from generic key injection. Prefer a supported TV input API / input-source list; otherwise expose only a clearly labelled compatibility shortcut.

### Phase C — Feature coverage
- Keyboard and text input.
- Media transport controls.
- TV settings shortcuts where supported: picture mode, brightness, color temperature, HDR, power saving and related entries.
- Device list, multi-device pairing, reconnect and connection status.
- All visible labels, dialogs, errors and settings translated into all seven locales.

### Phase D — Validation
- Build debug APK through GitHub Actions.
- Static checks for missing locale keys and unsupported feature claims.
- Test install / startup, pairing, reconnect, D-pad, keyboard, mouse capability path, HDMI source behavior and language switching on real TV models.
- Keep device-specific limitations visible; build success is not hardware validation.

## Current constraints to resolve
The Android TV Remote v2 protocol currently implemented in this project provides key injection and IME text input, but no generic mouse-pointer injection or universal HDMI source-switch API. Real pointer and source switching therefore require a TV/vendor-supported API or another explicitly authorized transport. D-pad and HDMI keycode fallbacks must remain labelled as fallbacks.
