# Add project specific Proguard rules here.

# ── Jetpack Compose / Lifecycle ───────────────────────────────────────────────
# Fix: CompositionLocal LocalLifecycleOwner not present in R8 release builds
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** { *; }
-keep class androidx.lifecycle.ReportFragment { *; }
-keep class androidx.lifecycle.LifecycleRegistry { *; }
-keep class androidx.lifecycle.ProcessLifecycleOwner { *; }
-keep class androidx.lifecycle.ViewTreeLifecycleOwner { *; }
-keep class androidx.lifecycle.compose.** { *; }

# Keep Compose runtime internals needed for CompositionLocal resolution
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
-keepclassmembers class androidx.compose.ui.platform.** { *; }

# Keep AbstractComposeView which sets up LocalLifecycleOwner
-keep class androidx.compose.ui.platform.AbstractComposeView { *; }
-keep class androidx.compose.ui.platform.ComposeView { *; }
-keep class androidx.activity.compose.** { *; }

# Keep ViewModel
-keep class * extends androidx.lifecycle.ViewModel { <init>(); }

# ── Firebase ──────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ── App models ────────────────────────────────────────────────────────────────
-keep class com.benjamin.salarytracker.** { *; }
