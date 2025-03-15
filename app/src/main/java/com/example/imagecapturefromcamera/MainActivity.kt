package com.example.imagecapturefromcamera

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberImagePainter
import com.example.imagecapturefromcamera.ui.theme.ImageCaptureFromCameraTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Objects

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImageCaptureFromCamera()
        }
    }
}

@Composable
fun ImageCaptureFromCamera() {
    val context = LocalContext.current
    val file = context.createImageFile()
    val uri = FileProvider.getUriForFile(
        Objects.requireNonNull(context),
        context.packageName + ".provider", file
    )

    // Manage state for captured images and videos
    var capturedImageUriList by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var capturedVideoUriList by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Video playback state
    var isVideoPlaying by remember { mutableStateOf(false) }
    var videoView: VideoView? by remember { mutableStateOf(null) }

    // Capture image result
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
            if (it) {
                capturedImageUriList = capturedImageUriList + uri // Add captured image URI to the list
                Toast.makeText(context, "Image Captured", Toast.LENGTH_SHORT).show() // Feedback
            } else {
                Toast.makeText(context, "Image Capture Failed", Toast.LENGTH_SHORT).show() // Feedback
            }
        }

    // Capture video result
    val videoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val videoUri = result.data?.data ?: Uri.EMPTY
                capturedVideoUriList = capturedVideoUriList + videoUri // Add captured video URI to the list
            }
        }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "Permission Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(10.dp)
            .verticalScroll(rememberScrollState()), // To make content scrollable
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {

        // Capture Image Button
        Button(onClick = {
            val permissionCheckResult =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)

            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                cameraLauncher.launch(uri)
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }) {
            Text(text = "Capture Image")
        }

        // Capture Video Button
        Button(onClick = {
            val permissionCheckResult =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)

            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                videoLauncher.launch(intent)
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }) {
            Text(text = "Capture Video")
        }

        // Display all captured images
        capturedImageUriList.forEach { imageUri ->
            Image(
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth(), // Ensure image has space to be rendered
                painter = rememberImagePainter(imageUri),
                contentDescription = "Captured Image"
            )
        }

        // Display all captured videos with play/pause functionality
        capturedVideoUriList.forEach { videoUri ->
            Column(modifier = Modifier.padding(16.dp)) {
                // Display the video using VideoView
                AndroidView(
                    factory = {
                        VideoView(context).apply {
                            setVideoURI(videoUri)
                            setOnPreparedListener {
                                // Set up the play/pause functionality
                                videoView = this
                                start()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                // Play/Pause button for video
                Button(
                    onClick = {
                        if (isVideoPlaying) {
                            videoView?.pause()
                        } else {
                            videoView?.start()
                        }
                        isVideoPlaying = !isVideoPlaying
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(text = if (isVideoPlaying) "Pause" else "Play")
                }
            }
        }

        // Share Button for image or video via USB or wired method
        Button(onClick = {
            if (capturedImageUriList.isNotEmpty()) {
                shareFile(context, capturedImageUriList.last())
            } else if (capturedVideoUriList.isNotEmpty()) {
                shareFile(context, capturedVideoUriList.last())
            } else {
                Toast.makeText(context, "No file to share", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text(text = "Share via Wired")
        }
    }
}

// Share file method using Share Intent
fun shareFile(context: Context, fileUri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/*"  // Use "image/*" if sharing images, or adjust based on file type
        putExtra(Intent.EXTRA_STREAM, fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // Check if there's an app available to handle the share intent
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(intent, "Share via"))
    } else {
        Toast.makeText(context, "No app to handle the share intent", Toast.LENGTH_SHORT).show()
    }
}

fun Context.createImageFile(): File {
    val timeStamp = SimpleDateFormat("yyyy_MM_dd_HH:mm:ss").format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    val image = File.createTempFile(
        imageFileName,
        ".jpg",
        externalCacheDir
    )

    return image
}

fun Context.createVideoFile(): File {
    val timeStamp = SimpleDateFormat("yyyy_MM_dd_HH:mm:ss").format(Date())
    val videoFileName = "VIDEO_" + timeStamp + "_"
    val video = File.createTempFile(
        videoFileName,
        ".mp4",
        externalCacheDir
    )

    return video
}
