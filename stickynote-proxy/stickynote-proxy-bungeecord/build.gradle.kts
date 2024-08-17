dependencies {
    compileOnly(libs.bungeecord)
    api(libs.mccoroutines.bungeecord.api)
    api(libs.mccoroutines.bungeecord.core)

    compileOnlyApi(libs.libby.bungee)
    compileOnlyApi(libs.adventure.platform.bungeecord)

    compileOnly(project(":stickynote-core"))
    compileOnly(project(":stickynote-proxy"))
}