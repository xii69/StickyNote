dependencies {
    api(libs.libby.bukkit)
    api(libs.libby.paper)
    api(libs.adventure.platform.bukkit)
    api(libs.cloud.paper)
    api(libs.cloud.minecraft.extras)
    api(libs.inventoryframework)
    api(libs.xseries) { isTransitive = false }
    api(libs.pathetic.pathfinder.bukkit)

    compileOnlyApi(libs.authlib)
    compileOnlyApi(libs.placeholderapi)
    compileOnlyApi(libs.skinsrestorer.api)

    compileOnly(libs.paper)
    compileOnly(libs.folia)

    compileOnly(project(":stickynote-core"))
}