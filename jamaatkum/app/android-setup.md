# إعدادات أندرويد (بعد `flutter create .`)

في `android/app/src/main/AndroidManifest.xml` أضف:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<!-- للتطوير فقط (http بدل https): -->
<application android:usesCleartextTraffic="true" ...>
  <activity android:name="com.yalantis.ucrop.UCropActivity"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.AppCompat.Light.NoActionBar"/>
```

اختيار الصورة من المعرض يستخدم منتقي الصور الخاص بنظام أندرويد، وهو يعطي الصلاحية تلقائياً بدون طلب إذن مزعج.
