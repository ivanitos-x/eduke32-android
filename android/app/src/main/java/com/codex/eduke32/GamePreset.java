package com.codex.eduke32;

/** Launcher profiles; game resources are always supplied by the user. */
enum GamePreset {
    DUKE3D("duke3d", "Duke Nukem 3D", "android-game-dir.txt", "eduke32.cfg", "enable_polymer"),
    BLOODCM("bloodcm", "BloodCM", "android-bloodcm-dir.txt", "bloodcm-android.cfg", "enable_polymer_bloodcm");

    final String id, title, directoryMarker, configName, rendererKey;
    GamePreset(String id, String title, String directoryMarker, String configName, String rendererKey) {
        this.id = id;
        this.title = title;
        this.directoryMarker = directoryMarker;
        this.configName = configName;
        this.rendererKey = rendererKey;
    }
    static GamePreset fromId(String id) {
        return BLOODCM.id.equals(id) ? BLOODCM : DUKE3D;
    }
}
