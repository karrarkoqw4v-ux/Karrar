set -x
APK=android-native/app/build/outputs/apk/debug/app-debug.apk
adb install -r $APK
adb shell pm grant com.turbo.rashq android.permission.POST_NOTIFICATIONS || true
mkdir -p shots-out
shot() { adb shell am force-stop com.turbo.rashq; adb shell am start -n com.turbo.rashq/.MainActivity --es shot_login "$2" --es shot_route "$1" ; sleep 6; adb exec-out screencap -p > "shots-out/$3.png"; }
shot home user 01_home
shot menu/rashq user 02_rashq
shot menu/games user 03_games
shot cat_tiktok user 04_tiktok_services
shot cat_pubg user 05_pubg_store
shot menu/points user 06_points
shot menu/recharge user 07_recharge
shot menu/account user 08_account
shot transfer:username user 09_transfer
shot notifs user 10_notifications
shot admin admin 11_admin
shot admin/buttons admin 12_button_editor
shot admin/services admin 13_admin_services
shot admin/emoji admin 14_emoji_manager
