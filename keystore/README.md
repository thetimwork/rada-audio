# Test signing key

`rada-alpha.jks` signs the alpha builds so each new APK installs over the previous one.

- Store and key password: `rada-alpha`, alias `rada-alpha`.
- It is public. **Never use it for a Play Store or any public release.** Create a private release key, keep it out of the repository, and pass it to the build through CI secrets.
