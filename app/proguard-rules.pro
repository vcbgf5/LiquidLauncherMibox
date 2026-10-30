# Used only by the "shrunk" build type (the lite edition): code and resources nobody uses are removed.
# The activities, services and receivers named in the manifest are kept automatically.

# Libraries that mention optional classes which are not on Android.
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.conscrypt.**
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep line numbers in crash reports readable enough to find the place.
-keepattributes SourceFile,LineNumberTable
