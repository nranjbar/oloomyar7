# Grade 7 review signing

`grade7-review.keystore` is a public **debug/review-only** key. It is checked in
so Grade 7 review APKs from different CI runners can update one another without
resetting the student's progress. It uses the standard Android debug alias and
password (`androiddebugkey` / `android`). Never use it for a production release.

The previous 0.1 review APK used an ephemeral runner key. Its private key is
unavailable, so that installed APK cannot be updated by this key. Removing the
0.1 app deletes its saved progress. Builds from 0.2 onward must retain this key
and the `com.oloomyar.app.grade7` application ID for compatible review updates.
