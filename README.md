# FriendLink Android APK Starter

Free Android-only starter for:
- anonymous login
- display name
- OpenStreetMap map
- live user markers
- tap a marker to open private DM
- global Everyone chat
- placeholder buttons for global/private walkie-talkie

## What you need, all free for the MVP
1. Android Studio: https://developer.android.com/studio
2. Firebase account/project on the Spark free plan: https://firebase.google.com/pricing
3. A physical Android phone for testing.

No Google Maps billing is used. This starter uses OpenStreetMap/osmdroid.

## Setup
1. Install Android Studio.
2. Open this folder as an existing project.
3. Go to https://console.firebase.google.com/
4. Create a Firebase project.
5. Add Android app with package name:
   com.example.friendlink
6. Download `google-services.json`.
7. Put `google-services.json` into:
   app/google-services.json
8. In Firebase Console, enable:
   - Authentication > Sign-in method > Anonymous
   - Firestore Database > Create database
9. For quick testing, set Firestore rules to the temporary rules below.
10. Connect Android phone by USB, enable Developer Options and USB debugging.
11. Click Run in Android Studio.
12. Build APK: Android Studio > Build > Build Bundle(s) / APK(s) > Build APK(s)

## Temporary Firestore test rules
Use only for your own testing. Lock this down before sharing publicly.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Next feature to add
The walkie-talkie should be added after this MVP works.
Recommended free path:
- Android Foreground Service
- RECORD_AUDIO permission
- WebRTC for audio
- Firebase Firestore or Realtime Database for signaling
- optional free Google STUN server for testing

A production-quality locked-phone walkie-talkie may eventually need a TURN server, which can cost money if users are on difficult networks. For a free MVP, keep it in-app first.
