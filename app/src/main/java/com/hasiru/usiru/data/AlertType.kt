package com.hasiru.usiru.data

enum class AlertType(val label: String, val guidance: String) {
    FOREST_FIRE(
        "Forest Fire",
        "Move uphill only if the fire is below you, avoid smoke, call local emergency services, and never try to fight a large fire alone."
    ),
    LANDSLIDE(
        "Landslide",
        "Stay away from slopes, streams, and fresh cracks. Warn nearby people and leave the area by the safest visible route."
    ),
    ILLEGAL_TREE_CUTTING(
        "Illegal Tree Cutting",
        "Do not confront offenders. Capture evidence from a safe distance and share exact coordinates with forest staff."
    ),
    WILDLIFE_SIGHTING(
        "Wildlife Sighting",
        "Keep distance, avoid flash, never feed animals, and leave a clear path for wildlife movement."
    )
}
