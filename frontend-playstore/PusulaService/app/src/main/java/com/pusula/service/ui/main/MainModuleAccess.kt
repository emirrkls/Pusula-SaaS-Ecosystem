package com.pusula.service.ui.main

/** NavigationBarItem consumes clicks before a modifier-level gate can intercept them. */
internal fun canOpenMainModule(featureKey: String?, features: Map<String, Boolean>): Boolean =
    featureKey == null || features[featureKey] == true
