# ezGCM
convenience to setup GCM for android.

# Setup
- Follow the official guide to obtain api key:  
https://developer.android.com/google/gcm/gs.html
- add this line into your build.gradle(app) dependency:
```
dependencies {
  compile "com.google.android.gms:play-services:3.1.+"
}
```


### AndroidMenifest.xml 
You will need these permission

```xml
  <uses-sdk android:minSdkVersion="8" android:targetSdkVersion="17"/>
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.GET_ACCOUNTS" />
  <uses-permission android:name="android.permission.WAKE_LOCK" />
  <uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />

  <permission android:name="com.example.gcm.permission.C2D_MESSAGE"
      android:protectionLevel="signature" />
  <uses-permission android:name="com.example.gcm.permission.C2D_MESSAGE" />
```

And add these into your <application> tag:
```xml
<application ...>
    <receiver
        android:name=".GcmBroadcastReceiver"
        android:permission="com.google.android.c2dm.permission.SEND" >
        <intent-filter>
            <action android:name="com.google.android.c2dm.intent.RECEIVE" />
            <category android:name="com.example.gcm" />
        </intent-filter>
    </receiver>
    <service android:name=".GcmIntentService" />
</application>
```

# Final Step:  
Copy these 3 files into somewhere from
https://github.com/Wesely/ezGCM/tree/master/app/src/main/java/tw/wesely/ezgcm
```
BlinkGCM.java
GcmBroadcastReceiver.java
GcmIntentService.java
```
And you are ready to go.
