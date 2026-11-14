APK=android-native/app/build/outputs/apk/debug/app-debug.apk
timeout 120 adb install -r $APK
timeout 20 adb shell pm grant com.turbo.rashq android.permission.POST_NOTIFICATIONS || true
mkdir -p shots-out
timeout 20 adb logcat -c || true
shot() {
  timeout 20 adb shell am force-stop com.turbo.rashq
  timeout 20 adb shell am start -n com.turbo.rashq/.MainActivity --es shot_login "$2" --es shot_route "$1"
  sleep 12
  timeout 40 adb exec-out screencap -p > "shots-out/$3.png"
  # diagnostics after every shot
  { echo "== $3"; timeout 20 adb shell "pidof com.turbo.rashq"; timeout 20 adb shell "dumpsys activity activities | grep -m3 -i 'mResumedActivity\|topResumedActivity'"; timeout 30 adb logcat -d -t 200 -s AndroidRuntime:E ActivityManager:W System.err:W "*:F" ; } >> shots-out/log_$4.txt 2>&1
}
G=$1
if [ "$G" = "user" ]; then
shot home user 01_home user
shot menu/rashq user 02_rashq user
shot menu/games user 03_games user
shot cat_tiktok user 04_tiktok_services user
shot cat_pubg user 05_pubg_store user
shot menu/points user 06_points user
shot menu/recharge user 07_recharge user
shot menu/account user 08_account user
shot set user 09_settings user
shot set/colors user 10_colors user
shot set/privacy user 11_privacy user
shot notifs user 12_notifications user
shot menu/transfer user 14_transfer_menu user
shot terms user 15_terms user
shot orders user 16_orders user
else
shot admin admin 20_admin_home admin
shot admin/settings admin 21_admin_settings admin
shot admin/gifts admin 22_admin_gifts admin
shot admin/toggle admin 23_admin_toggle admin
shot admin/buttons admin 24_button_editor admin
shot admin/services admin 25_admin_services admin
shot admin/emoji admin 26_emoji_manager admin
shot admin/recharge admin 27_admin_recharge admin
shot admin/texts admin 28_admin_texts admin
shot admin/sites admin 29_admin_sites admin
fi
exit 0
