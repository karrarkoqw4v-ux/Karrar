import re, shutil, os, sys
root = sys.argv[1] if len(sys.argv) > 1 else '.'
man = os.path.join(root, 'android/app/src/main/AndroidManifest.xml')
s = open(man, encoding='utf-8').read()
s = re.sub(r'android:label="[^"]*"', 'android:label="مونتاج مرتاح"', s, count=1)
perms = '''    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/>
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28"/>
'''
if 'READ_MEDIA_VIDEO' not in s:
    s = s.replace('<application', perms + '    <application', 1)
open(man, 'w', encoding='utf-8').write(s)
g = os.path.join(root, 'android/app/build.gradle.kts')
t = open(g, encoding='utf-8').read()
t = t.replace('minSdk = flutter.minSdkVersion', 'minSdk = 24')
if 'checkReleaseBuilds' not in t:
    t = t.replace('android {', 'android {\n    lint {\n        checkReleaseBuilds = false\n        abortOnError = false\n    }', 1)
if 'useLegacyPackaging' not in t:
    t = t.replace('android {', 'android {\n    packaging {\n        jniLibs {\n            useLegacyPackaging = true\n        }\n    }', 1)
open(g, 'w', encoding='utf-8').write(t)
# icons
for d in os.listdir(os.path.join(root, 'ci/icons')):
    dst = os.path.join(root, 'android/app/src/main/res', d)
    os.makedirs(dst, exist_ok=True)
    shutil.copy(os.path.join(root, 'ci/icons', d, 'ic_launcher.png'), os.path.join(dst, 'ic_launcher.png'))
print('patched')
