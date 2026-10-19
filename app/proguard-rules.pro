# R8 rules for release builds.

# Navigation finds enum route arguments by class name at runtime.
-keep enum com.lorenzo.gymtracker.ui.navigation.** { *; }
