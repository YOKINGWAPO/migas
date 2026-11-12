package com.jokin.calorias.data.health

/**
 * Display names of the apps that usually write watch data into Health Connect. A fixed table
 * because reading any installed app's label needs QUERY_ALL_PACKAGES, which Play restricts.
 */
object SourceApps {
    private val NAMES = mapOf(
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.xiaomi.wearable" to "Mi Fitness",
        "com.mi.health" to "Mi Fitness",
        "com.huami.watch.hmwatchmanager" to "Zepp",
        "com.withings.wiscale2" to "Withings",
        "com.ouraring.oura" to "Oura",
        "fi.polar.polarflow" to "Polar Flow",
        "com.stt.android.suunto" to "Suunto",
        "com.coros.coros" to "COROS",
        "com.strava" to "Strava",
        "com.whoop.android" to "WHOOP",
    )

    /** Known names in a stable order; unknown packages are left out and [anyUnknown] says so. */
    fun names(packages: Set<String>): List<String> = packages.mapNotNull { NAMES[it] }.distinct().sorted()

    fun anyUnknown(packages: Set<String>): Boolean = packages.any { it !in NAMES }
}
