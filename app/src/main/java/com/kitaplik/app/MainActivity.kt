package com.kitaplik.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kitaplik.app.ui.library.LibraryScreen
import com.kitaplik.app.ui.library.LibraryViewModel
import com.kitaplik.app.ui.reader.ReaderScreen
import com.kitaplik.app.ui.reader.ReaderViewModel
import com.kitaplik.app.ui.theme.KitaplikTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemNavigationBar()

        setContent {
            val libraryViewModel: LibraryViewModel = viewModel()
            val preferences by libraryViewModel.preferences.collectAsStateWithLifecycle()

            KitaplikTheme(themeMode = preferences.themeMode) {
                KitaplikNavigation(libraryViewModel = libraryViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemNavigationBar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemNavigationBar()
        }
    }

    /**
     * Alt sistem gezinme çubuğunu (geri, ana ekran, son uygulamalar) gizler;
     * kenardan kaydırma ile geçici olarak açılıp kendiliğinden tekrar gizlenir.
     */
    private fun hideSystemNavigationBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }
}

@Composable
private fun KitaplikNavigation(
    libraryViewModel: LibraryViewModel
) {
    val navController = rememberNavController()

    // Uygulama açıldığında doğrudan kitapların olduğu liste ("library") açılır.
    NavHost(
        navController = navController,
        startDestination = "library"
    ) {
        composable(route = "library") {
            LibraryScreen(
                viewModel = libraryViewModel,
                onBookClick = { selectedBook ->
                    // Hiçbir soru sorulmaz, hiçbir ayar ekranı çıkmaz; doğrudan yerleşik okuyucu açılır.
                    navController.navigate("reader/${selectedBook.id}")
                }
            )
        }

        composable(
            route = "reader/{bookId}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            val readerViewModel: ReaderViewModel = viewModel()

            ReaderScreen(
                bookId = bookId,
                viewModel = readerViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
