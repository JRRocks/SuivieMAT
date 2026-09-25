package com.scanlist.app.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.ui.common.LocalSnackbarHostState
import com.scanlist.app.ui.compare.CompareScanModeScreen
import com.scanlist.app.ui.compare.CompareSessionScreen
import com.scanlist.app.ui.compare.CompareSourceScreen
import com.scanlist.app.ui.compare.CompareStartViewModel
import com.scanlist.app.ui.folders.FolderActionsScreen
import com.scanlist.app.ui.folders.FolderDetailScreen
import com.scanlist.app.ui.folders.FolderExportScreen
import com.scanlist.app.ui.folders.FolderListScreen
import com.scanlist.app.ui.folders.FolderResumeModeScreen
import com.scanlist.app.ui.menu.MainMenuScreen
import com.scanlist.app.ui.scan.NewScanModeScreen
import com.scanlist.app.ui.scan.ScanScreen
import com.scanlist.app.ui.settings.EmailPrefsScreen

/**
 * Graphe de navigation.
 *
 * Chaque destination correspond a une entree exacte de la hierarchie de menus
 * specifiee ; voir [Routes] pour la correspondance ligne a ligne.
 */
@Composable
fun ScanListNavHost(
    navController: NavHostController = rememberNavController(),
) {
    // Fourni par MainActivity : un seul hote de snackbar pour tout le graphe.
    val snackbarHostState = LocalSnackbarHostState.current

    NavHost(navController = navController, startDestination = Routes.MENU) {

        // ------------------------------------------------------- Menu principal
        composable(Routes.MENU) {
            MainMenuScreen(
                onFolders = { navController.navigate(Routes.FOLDERS) },
                onCompare = { navController.navigate(Routes.COMPARE_SOURCE) },
                onNewScan = { navController.navigate(Routes.NEW_SCAN_MODE) },
            )
        }

        // ----------------------------------------------------- Branche Dossiers
        composable(Routes.FOLDERS) {
            FolderListScreen(
                onBack = navController::popBackStack,
                onFolderSelected = { navController.navigate(Routes.folderActions(it)) },
            )
        }

        composable(
            route = Routes.FOLDER_ACTIONS,
            arguments = listOf(navArgument(Routes.ARG_FOLDER_ID) { type = NavType.LongType }),
        ) { entry ->
            val folderId = entry.folderId()
            FolderActionsScreen(
                onBack = navController::popBackStack,
                onConsult = { navController.navigate(Routes.folderDetail(folderId)) },
                onExport = { navController.navigate(Routes.folderExport(folderId)) },
                onResume = { navController.navigate(Routes.folderResumeMode(folderId)) },
            )
        }

        composable(
            route = Routes.FOLDER_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_FOLDER_ID) { type = NavType.LongType }),
        ) {
            val compareViewModel: CompareStartViewModel = hiltViewModel()
            FolderDetailScreen(
                onBack = navController::popBackStack,
                onLaunchCompare = { folderId ->
                    compareViewModel.createFromFolder(folderId) { sessionId ->
                        navController.navigate(Routes.compareSession(sessionId))
                    }
                },
            )
        }

        composable(
            route = Routes.FOLDER_EXPORT,
            arguments = listOf(navArgument(Routes.ARG_FOLDER_ID) { type = NavType.LongType }),
        ) {
            FolderExportScreen(
                onBack = navController::popBackStack,
                onEmailPrefs = { navController.navigate(Routes.EMAIL_PREFS) },
            )
        }

        composable(
            route = Routes.FOLDER_RESUME_MODE,
            arguments = listOf(navArgument(Routes.ARG_FOLDER_ID) { type = NavType.LongType }),
        ) { entry ->
            val folderId = entry.folderId()
            FolderResumeModeScreen(
                onBack = navController::popBackStack,
                onModeSelected = { mode ->
                    navController.navigate(Routes.scanIntoFolder(mode, folderId))
                },
            )
        }

        // ----------------------------------------------------- Branche Comparer
        composable(Routes.COMPARE_SOURCE) {
            val viewModel: CompareStartViewModel = hiltViewModel()
            val error by viewModel.error.collectAsStateWithLifecycle()

            // Le SAF renvoie un URI a lecture unique : la session est creee dans la foulee.
            val pickCsv = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    viewModel.createFromCsv(uri) { sessionId ->
                        navController.navigate(Routes.compareSession(sessionId))
                    }
                }
            }

            // Le texte est resolu en composition : stringResource n'est pas appelable
            // depuis l'effet lui-meme.
            val errorText = error?.let { stringResource(it) }

            LaunchedEffect(errorText) {
                errorText?.let {
                    snackbarHostState.showSnackbar(it)
                    viewModel.clearError()
                }
            }

            CompareSourceScreen(
                onBack = navController::popBackStack,
                onFromFolder = { navController.navigate(Routes.COMPARE_PICK_FOLDER) },
                onFromCsv = {
                    // text/comma-separated-values et text/plain couvrent les exports
                    // de tableurs qui ne declarent pas text/csv.
                    pickCsv.launch(
                        arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"),
                    )
                },
            )
        }

        composable(Routes.COMPARE_PICK_FOLDER) {
            val viewModel: CompareStartViewModel = hiltViewModel()
            FolderListScreen(
                onBack = navController::popBackStack,
                onFolderSelected = { folderId ->
                    viewModel.createFromFolder(folderId) { sessionId ->
                        navController.navigate(Routes.compareSession(sessionId))
                    }
                },
            )
        }

        composable(
            route = Routes.COMPARE_SESSION,
            arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.LongType }),
        ) { entry ->
            val sessionId = entry.sessionId()
            CompareSessionScreen(
                onBack = navController::popBackStack,
                onLaunchScan = { navController.navigate(Routes.compareScanMode(sessionId)) },
            )
        }

        composable(
            route = Routes.COMPARE_SCAN_MODE,
            arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.LongType }),
        ) { entry ->
            val sessionId = entry.sessionId()
            CompareScanModeScreen(
                onBack = navController::popBackStack,
                onModeSelected = { mode ->
                    navController.navigate(Routes.scanIntoSession(mode, sessionId))
                },
            )
        }

        // ------------------------------------------------- Branche Nouveau scan
        composable(Routes.NEW_SCAN_MODE) {
            NewScanModeScreen(
                onBack = navController::popBackStack,
                onModeSelected = { mode -> navController.navigate(Routes.scanNew(mode)) },
            )
        }

        // ------------------------------------------------------- Ecran de scan
        composable(
            route = Routes.SCAN,
            arguments = listOf(
                navArgument(Routes.ARG_MODE) { type = NavType.StringType },
                navArgument(Routes.ARG_FOLDER_ID) {
                    type = NavType.LongType
                    defaultValue = Routes.NO_ID
                },
                navArgument(Routes.ARG_SESSION_ID) {
                    type = NavType.LongType
                    defaultValue = Routes.NO_ID
                },
            ),
        ) { entry ->
            // Le dossier et la session sont lus par le ViewModel dans son
            // SavedStateHandle : l'ecran n'a besoin que du mode, qui pilote sa mise
            // en page.
            val mode = ScanMode.fromNameOrNull(entry.arguments?.getString(Routes.ARG_MODE))
                ?: ScanMode.QR_CODE
            ScanScreen(mode = mode, onBack = navController::popBackStack)
        }

        // ------------------------------------------------------------ Reglages
        composable(Routes.EMAIL_PREFS) {
            EmailPrefsScreen(onBack = navController::popBackStack)
        }
    }
}

private fun androidx.navigation.NavBackStackEntry.folderId(): Long =
    arguments?.getLong(Routes.ARG_FOLDER_ID) ?: Routes.NO_ID

private fun androidx.navigation.NavBackStackEntry.sessionId(): Long =
    arguments?.getLong(Routes.ARG_SESSION_ID) ?: Routes.NO_ID
