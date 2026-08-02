# Mandatory post-refactor release handoff

Before reporting any refactor or structural code change as complete, unless the user explicitly opts out:

1. Rebuild the release APK and sign it into `C:\Users\aperi\DEV\ANDROID\_scratch\_BUILDS\limen.apk`.
2. Verify its signer and state whether it is a production upload key or a debug-key emulator preview.
3. Uninstall Limen from the selected emulator, install that exact APK, launch it, and smoke-check it.
4. Provide exact `git add`, `git commit`, and `git push` commands in the final handoff. Do not run them unless asked.

Use `C:\Users\aperi\DEV\ANDROID\_scratch\deploy.ps1 limen -Serial <emulator>` for the standard release/build/install flow when available.
