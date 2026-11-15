package com.lekowatch.guardian.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lekowatch.guardian.ui.screens.AddDeviceScreen
import com.lekowatch.guardian.ui.screens.ChildLinkScreen
import com.lekowatch.guardian.ui.screens.ChildLinkedScreen
import com.lekowatch.guardian.ui.screens.ConsentScreen
import com.lekowatch.guardian.ui.screens.DashboardScreen
import com.lekowatch.guardian.ui.screens.LoginScreen
import com.lekowatch.guardian.ui.screens.RolePickerScreen
import com.lekowatch.guardian.ui.screens.SignUpScreen
import com.lekowatch.guardian.ui.screens.VerifyScreen
import com.lekowatch.guardian.viewmodel.AuthViewModel
import com.lekowatch.guardian.viewmodel.DeviceViewModel

@Composable
fun LekoNavGraph() {
    val navController = rememberNavController()
    val authVm = AuthViewModel()
    val deviceVm = DeviceViewModel()

    val navigate: (String) -> Unit = { route -> navController.navigate(route) }

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) { LoginScreen(authVm, navigate) }
        composable(Routes.SIGNUP) { SignUpScreen(authVm, navigate) }
        composable(Routes.VERIFY) { VerifyScreen(navigate) }
        composable(Routes.ROLE_PICKER) { RolePickerScreen(navigate) }
        composable(Routes.CONSENT) { ConsentScreen(navigate) }
        composable(Routes.ADD_DEVICE) { AddDeviceScreen(deviceVm) }
        composable(Routes.DASHBOARD) { DashboardScreen(deviceVm, navigate) }
        composable(Routes.CHILD_LINK) { ChildLinkScreen(deviceVm, "جهاز الطفل", navigate) }
        composable(Routes.CHILD_LINKED) { ChildLinkedScreen() }
    }
}
