# Third-Party Notices

This mod (`yuan_live2d`) bundles or depends on third-party material that is
NOT covered by this project's MIT license. Please read and comply with each
owner's terms.

## Live2D Cubism SDK

- `src/main/resources/runtime/windows-x86_64/Live2DCubismCore.dll` — the Live2D
  Cubism SDK runtime, proprietary software © Live2D Inc.

The Cubism SDK is distributed under Live2D's own license agreement
(https://www.live2d.com/eula/live2d-proprietary-software-license-agreement_en.html).
The SDK runtime is bundled only so the mod can render Live2D models and is
subject to Live2D's terms — including their revenue/profit thresholds and
their rules on redistributing the runtime with your application. This project
cannot and does not grant any additional rights to the SDK. Users are
responsible for keeping within Live2D's license.

## Live2D sample model "Haru"

- `src/main/resources/models/Haru/**` — the official Live2D sample model
  (Haru) shipped with the Cubism SDK. Live2D sample data is provided under
  Live2D's sample-material terms and is not covered by this project's license.

## Private / user models

User-supplied Live2D models are NOT part of this repository. They are loaded
at runtime from the game instance's `config/yuan_live2d/models/` directory and
are not distributed here.

---

If you believe any material here is included improperly, please open an issue
and it will be removed promptly.