# Content is read explicitly with JSONObject; no reflective model deserialisation.
# Keep the public review activities available to Android and install-update checks.
-keep public class com.oloomyar.app.*Activity { public <init>(); }
