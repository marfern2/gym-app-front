package com.mar.gym.feature.push

fun shouldRequestNotificationPermission(sdk: Int, granted: Boolean): Boolean = sdk >= 33 && !granted
