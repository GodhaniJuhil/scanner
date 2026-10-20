package com.example.scanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.scanner.ui.feature.camera.CameraScreen
import com.example.scanner.ui.feature.edit.EditScreen
import com.example.scanner.ui.feature.home.HomeScreen
import com.example.scanner.ui.feature.preview.PreviewViewModel
import com.example.scanner.ui.feature.review.ReviewScreen
import com.example.scanner.ui.feature.preview.PreviewScreen

private object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val EDIT = "edit"
    const val REVIEW = "review"
    const val PREVIEW = "preview/{${PreviewViewModel.ARG_ID}}"
    fun preview(id: Long) = "preview/$id"
}

@Composable
fun ScannerNavGraph() {
    val nav = rememberNavController()
    // Created with the activity as owner, so it is shared by the camera and edit destinations.
    val session: ScanSessionViewModel = viewModel()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onScan = { nav.navigate(Routes.CAMERA) },
                onOpen = { id -> nav.navigate(Routes.preview(id)) },
            )
        }

        composable(Routes.CAMERA) {
            val drafts by session.drafts.collectAsStateWithLifecycle()
            CameraScreen(
                pageCount = drafts.size,
                onCaptured = {
                    session.onCaptured(it)
                    nav.navigate(Routes.EDIT)
                },
                onClose = {
                    session.discard()
                    nav.popBackStack()
                },
                onReview = { nav.navigate(Routes.REVIEW) },
            )
        }

        composable(Routes.EDIT) {
            val capture = remember { session.pendingCapture }
            if (capture == null) {
                // Only possible if the process was recreated while editing: nothing to edit.
                LaunchedEffect(Unit) { nav.popBackStack(Routes.HOME, inclusive = false) }
            } else {
                val drafts by session.drafts.collectAsStateWithLifecycle()
                EditScreen(
                    imagePath = capture.imagePath,
                    quad = capture.quad,
                    previousPages = drafts,
                    onRetake = { nav.popBackStack() },
                    onPageAdded = {
                        session.addDraft(it)
                        nav.popBackStack()
                    },
                    onSaved = { id ->
                        session.finish()
                        nav.navigate(Routes.preview(id)) { popUpTo(Routes.HOME) }
                    },
                )
            }
        }

        composable(Routes.REVIEW) {
            val pages by session.drafts.collectAsStateWithLifecycle()
            val isSaving by session.isSaving.collectAsStateWithLifecycle()
            val error by session.error.collectAsStateWithLifecycle()
            ReviewScreen(
                pages = pages,
                isSaving = isSaving,
                error = error,
                onMove = session::moveDraft,
                onDelete = session::removeDraft,
                onAddPage = { nav.popBackStack() },
                onSave = {
                    session.saveDrafts { id -> nav.navigate(Routes.preview(id)) { popUpTo(Routes.HOME) } }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            route = Routes.PREVIEW,
            arguments = listOf(navArgument(PreviewViewModel.ARG_ID) { type = NavType.LongType }),
        ) {
            PreviewScreen(onBack = { nav.popBackStack() })
        }
    }
}
