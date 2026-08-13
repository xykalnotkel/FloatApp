# Changelog

## 0.4.1-alpha

- Fixed the Redmi A2 launch failure `Unknown option: --activity-new-task`.
- Replaced unsupported shell options with the equivalent `0x18008000` Intent flag bitmask.
- Rebuilt the main UI around four explicit readiness states: Shizuku, overlay permission, virtual display, and engine.
- Added a single guided setup action that always performs the next missing step.
- Applications cannot be launched until all prerequisites show ready.
- Layout and size controls now have unmistakable selected states and contextual descriptions.
- Size controls are hidden for top/bottom split layouts.
- Added clear loading, launch failure, and no-frame messages inside each virtual window.
- Added direct log-location guidance and a shorter Indonesian setup guide.

## 0.4.0-alpha

- Changed the compatibility engine to a public, own-content virtual display so third-party activities can render on it.
- Force-stops an existing task before virtual launch to prevent Android from bringing the fullscreen task forward.
- Launches targets with new-task, multiple-task, and clear-task flags.
- Added a privileged `moveRootTaskToDisplay` fallback through the Shizuku activity-task binder.
- Added detailed launch, task, display, and verification output to `info.txt`.
- Reworked Split A/B into flush top and bottom virtual regions instead of rounded floating cards.
- Prevents multiple windows from overlapping in the same split slot.
- Added maximize/restore toggle.
- Explicitly produces one universal APK without ABI or density splits.

## 0.3.1-alpha

- Fixed Redmi A2 crash: `TextureView doesn't support displaying a background drawable`.
- Removed the unsupported background assignment from the virtual-display `TextureView`.
- Reworked the UI toward a compact MIUI-inspired utility style.
- Reduced corner radii from oversized pills to approximately 8–12 dp.
- Added clearer selected states for layout and size controls.
- Improved Indonesian labels and app-list hierarchy.
- Added device, SDK, build, and version information to local logs.

## 0.3.0-alpha

- Added Virtual Display Compatibility Engine for firmware without native freeform support.
- Added movable, resizable, minimizable, maximizable, and closable virtual app windows.
- Added virtual top/bottom split layout.
- Added Shizuku-based input injection.
- Added runtime virtual-display diagnostics.

## 0.2.0-alpha

- Added floating sidebar, favorite apps, split launch commands, Indonesian diagnostics, and local logs.
