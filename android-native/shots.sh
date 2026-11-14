APK=android-native/app/build/outputs/apk/debug/app-debug.apk
adb install -r $APK
adb shell pm grant com.turbo.rashq android.permission.POST_NOTIFICATIONS || true
mkdir -p shots-out
shot() { adb shell am force-stop com.turbo.rashq; adb shell am start -n com.turbo.rashq/.MainActivity --es shot_login "$2" --es shot_route "$1"; sleep 7; timeout 30 adb exec-out screencap -p > "shots-out/$3.png"; }
if [ "$1" = "user" ]; then
shot home user 01_home
shot menu/rashq user 02_rashq
shot menu/games user 03_games
shot cat_tiktok user 04_tiktok_services
shot cat_pubg user 05_pubg_store
shot menu/points user 06_points
shot menu/recharge user 07_recharge
shot menu/account user 08_account
shot set user 09_settings
shot set/colors user 10_colors
shot set/privacy user 11_privacy
shot notifs user 12_notifications
shot menu/transfer user 14_transfer_menu
shot terms user 15_terms
shot orders user 16_orders
shot transfer:username user 13_transfer
else
shot admin admin 20_admin_home
shot admin/settings admin 21_admin_settings
shot admin/gifts admin 22_admin_gifts
shot admin/toggle admin 23_admin_toggle
shot admin/buttons admin 24_button_editor
shot admin/services admin 25_admin_services
shot admin/emoji admin 26_emoji_manager
shot admin/recharge admin 27_admin_recharge
shot admin/texts admin 28_admin_texts
shot admin/sites admin 29_admin_sites
shot admin/fetch admin 30_admin_fetch
fi
exit 0
