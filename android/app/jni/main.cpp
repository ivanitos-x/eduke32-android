#include <cstdlib>
#include <dirent.h>
#include <strings.h>
#include <atomic>
#include <jni.h>
#include "android_performance_hint.h"

static std::atomic<float> androidDisplayRefreshRate{0.f};
extern "C" double AndroidGetDisplayRefreshRate(void)
{
    return androidDisplayRefreshRate.load(std::memory_order_relaxed);
}
#include <fstream>
#include <string>
#include <vector>
#include <unistd.h>
#include <sys/stat.h>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <SDL.h>
#include <SDL_system.h>
#include "compat.h"
#include "in_android.h"
#ifdef POLYMER
extern char nogl;

static bool AndroidPolymerEnabled(std::string const &configPath)
{
    std::ifstream config(configPath.c_str());
    std::string line;
    bool inScreenSetup = false;
    while (std::getline(config, line))
    {
        size_t const first = line.find_first_not_of(" \t\r");
        if (first == std::string::npos)
            continue;
        line.erase(0, first);
        if (line.front() == '[' && line.back() == ']')
        {
            inScreenSetup = line.compare(1, line.size() - 2, "Screen Setup") == 0;
            continue;
        }
        if (!inScreenSetup)
            continue;
        size_t const equal = line.find('=');
        if (equal == std::string::npos)
            continue;
        std::string key = line.substr(0, equal);
        size_t const keyEnd = key.find_last_not_of(" \t");
        if (keyEnd != std::string::npos)
            key.erase(keyEnd + 1);
        if (key != "Polymer")
            continue;
        size_t const value = line.find_first_not_of(" \t", equal + 1);
        return value != std::string::npos && line[value] == '1';
    }
    return false;
}
#endif
extern int32_t g_bloodCMCompat;
extern "C" int eduke32_android_main(int argc, char const *argv[]);
droidsysinfo_t droidinfo = { 48000, 1024, 1280, 720 };
int ALSA_ClientID = 0;
int ALSA_PortID = 0;

static int mappedActions[13] = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 };
static uint8_t actionCounts[0x300];

extern "C" JNIEXPORT void JNICALL
Java_com_codex_eduke32_GameActivity_nativeAction(JNIEnv *, jclass, jint action, jboolean down)
{
    static int const actions[] = {
        gamefunc_Fire, gamefunc_Jump, gamefunc_Open, gamefunc_Crouch,
        gamefunc_Inventory, gamefunc_Next_Weapon,
        gamefunc_Quick_Kick, MENU_SELECT, MENU_BACK, MENU_UP, MENU_DOWN, MENU_LEFT, MENU_RIGHT
    };

    if (action < 0 || action >= (int)(sizeof(actions) / sizeof(actions[0])))
        return;

    int mapped = actions[action];
    if (down)
    {
        if (mappedActions[action] >= 0)
            return; // duplicate DOWN must not leave a permanent held count
        int const mode = AndroidPublishedTouchMode();
        if (mode == TOUCH_SCREEN_GAME)
        {
            if (action == 9) mapped = gamefunc_Move_Forward;
            else if (action == 10) mapped = gamefunc_Move_Backward;
            else if (action == 11) mapped = gamefunc_Strafe_Left;
            else if (action == 12) mapped = gamefunc_Strafe_Right;
        }
        else
        {
            if (action < 6)
            {
                if (action == 0 || action == 2 || action == 4) mapped = MENU_SELECT;
                else if (action == 1 || action == 3) mapped = MENU_BACK;
                else if (action == 5) mapped = MENU_DOWN;
            }
            else if (mode == TOUCH_SCREEN_MENU || mode == TOUCH_SCREEN_MENU_NOBACK || action == 7 || action == 8)
                mapped = action == 6 ? MENU_SELECT : actions[action];
            else
            {
                mappedActions[action] = -1;
                return;
            }
        }
        if (mapped < 0 || mapped >= (int)(sizeof(actionCounts) / sizeof(actionCounts[0])))
            return;
        mappedActions[action] = mapped;
        if (actionCounts[mapped]++ == 0)
            AndroidAction(1, mapped);
        return;
    }
    else
    {
        if (mappedActions[action] < 0)
            return;
        mapped = mappedActions[action];
        mappedActions[action] = -1;
        if (mapped < 0 || mapped >= (int)(sizeof(actionCounts) / sizeof(actionCounts[0])))
            return;
        if (actionCounts[mapped] > 0 && --actionCounts[mapped] == 0)
            AndroidAction(0, mapped);
        return;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_codex_eduke32_GameActivity_nativePreferBigCores(JNIEnv *, jclass, jboolean enabled)
{
    androidBigPreference.store(enabled ? 1 : 0, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_codex_eduke32_GameActivity_nativeMove(JNIEnv *, jclass, jfloat strafe, jfloat forward)
{
    AndroidMove(strafe, forward);
}

extern "C" JNIEXPORT void JNICALL
Java_com_codex_eduke32_GameActivity_nativeLook(JNIEnv *, jclass, jfloat yaw, jfloat pitch)
{
    AndroidLook(yaw, pitch);
}

extern "C" JNIEXPORT void JNICALL
Java_com_codex_eduke32_GameActivity_nativeLookJoystick(JNIEnv *, jclass, jfloat yaw, jfloat pitch)
{
    AndroidLookJoystick(yaw, pitch);
}

extern "C" JNIEXPORT void JNICALL
Java_com_codex_eduke32_GameActivity_nativeReset(JNIEnv *, jclass)
{

    for (int i = 0; i < (int)(sizeof(actionCounts) / sizeof(actionCounts[0])); ++i)
        if (actionCounts[i]) AndroidAction(0, i);
    for (auto &mapped : mappedActions) mapped = -1;
    for (auto &count : actionCounts) count = 0;
    AndroidResetButtons();
}

extern "C" JNIEXPORT void JNICALL
Java_com_codex_eduke32_GameActivity_nativeDisplayRefreshRate(JNIEnv *, jclass, jfloat rate)
{
    if (rate >= 30.f && rate <= 240.f)
        androidDisplayRefreshRate.store(rate, std::memory_order_relaxed);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_codex_eduke32_GameActivity_nativeTouchMode(JNIEnv *, jclass)
{
    return AndroidPublishedTouchMode();
}

static std::string AndroidFindChild(std::string const &directory, char const *name)
{
    if (directory.empty()) return {};
    DIR *dir = opendir(directory.c_str());
    if (!dir) return {};
    std::string result;
    while (dirent *entry = readdir(dir))
        if (strcasecmp(entry->d_name, name) == 0)
        {
            result = directory + "/" + entry->d_name;
            break;
        }
    closedir(dir);
    return result;
}


static bool androidEmergencyStart = false;
static int AndroidRunEngine(int argc, char const **argv)
{
    std::vector<char const *> args(argv,argv+argc);
    if(androidEmergencyStart) {
        std::ofstream config("android-emergency.cfg",std::ios::trunc);
        config << "[Setup]\nForceSetup = 0\nNoAutoLoad = 1\n[Screen Setup]\nPolymer = 0\nScreenBPP = 8\nScreenWidth = 1280\nScreenHeight = 720\n";
        config.close();
        if(!config.good()) { SDL_LogError(SDL_LOG_CATEGORY_APPLICATION,"Cannot create emergency config");return 1; }
        // Only the disposable emergency profile is reset. Normal game settings are retained.
        unlink("android-emergency_settings.cfg");
        args.insert(args.end(),{"-usecwd","-cfg","android-emergency.cfg","-noautoload","-nosetup","-ns","-nm"});
#ifdef POLYMER
        nogl=1;
#endif
    }
    SDL_Log("Entering engine: emergency=%d argc=%d",androidEmergencyStart,(int)args.size());
    // argc excludes the required null terminator. loguru validates argv[argc]
    // before it can create an engine log; spare vector capacity is not a sentinel.
    int const engineArgc=(int)args.size();
    args.push_back(nullptr);
    int const result=eduke32_android_main(engineArgc,args.data());
    SDL_Log("Engine returned normally: exit=%d",result);
    return result;
}

extern "C" int SDL_main(int argc, char **argv)
{
    SDL_Log("SDL_main entered; starting Android bootstrap");
    std::vector<char *> filtered;
    for(int i=0;i<argc;++i) {
        if(i>0 && !strcmp(argv[i],"-androidsafe")) androidEmergencyStart=true;
        else filtered.push_back(argv[i]);
    }
    argc=(int)filtered.size();
    filtered.push_back(nullptr);argv=filtered.data();
    if(androidEmergencyStart) {
        SDL_setenv("SDL_AUDIODRIVER","dummy",1);
        SDL_Log("Emergency mode: Classic, disposable config, audio off, autoload off");
    }
    // Touch controls provide camera input; do not treat the phone accelerometer as a joystick.
    SDL_SetHint(SDL_HINT_ACCELEROMETER_AS_JOYSTICK, "0");
    SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS, "1");
    // SDL otherwise allows portrait when its window is resizable, overriding the manifest.
    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight" );
    if (char const *home = SDL_AndroidGetInternalStoragePath())
    {
        setenv("HOME", home, 1);
        std::string configRoot = std::string(home) + "/.config";
        std::string configDir = configRoot + "/eduke32";
        mkdir(configRoot.c_str(), 0700);
        mkdir(configDir.c_str(), 0700);
#ifdef POLYMER
        // Use the original 8-bit classic renderer when Polymer is disabled.
        nogl = AndroidPolymerEnabled(configDir + "/eduke32.cfg") ? 0 : 1;
#endif
        // Keep the bundled GeneralUser bank in app-private storage so the MIDI
        // driver can open it through the normal VFS and app upgrades preserve it.
        std::string soundFontPath = configDir + "/GeneralUser GS SoftSynth v1.44.sf2";
        struct stat soundFontStat;
        if (stat(soundFontPath.c_str(), &soundFontStat) != 0)
        {
            JNIEnv *env = static_cast<JNIEnv *>(SDL_AndroidGetJNIEnv());
            jobject activity = static_cast<jobject>(SDL_AndroidGetActivity());
            jclass activityClass = env ? env->GetObjectClass(activity) : nullptr;
            jmethodID getAssets = activityClass ? env->GetMethodID(activityClass, "getAssets", "()Landroid/content/res/AssetManager;") : nullptr;
            jobject javaAssets = getAssets ? env->CallObjectMethod(activity, getAssets) : nullptr;
            AAssetManager *assets = javaAssets ? AAssetManager_fromJava(env, javaAssets) : nullptr;
            AAsset *soundFont = assets ? AAssetManager_open(assets, "GeneralUser GS SoftSynth v1.44.sf2", AASSET_MODE_STREAMING) : nullptr;
            if (soundFont)
            {
                std::ofstream output(soundFontPath.c_str(), std::ios::binary);
                char buffer[65536];
                int bytesRead;
                while (output.good() && (bytesRead = AAsset_read(soundFont, buffer, sizeof(buffer))) > 0)
                    output.write(buffer, bytesRead);
                AAsset_close(soundFont);
            }
            if (javaAssets) env->DeleteLocalRef(javaAssets);
            if (activityClass) env->DeleteLocalRef(activityClass);
        }

        // Upgrade older installs once: saved cvars may still select AdLib,
        // overriding the new Android GeneralUser GS default.
        std::string gsMigrationMarker = configDir + "/android-generaluser-migrated.txt";
        std::ifstream gsMigrationCheck(gsMigrationMarker.c_str());
        if (!gsMigrationCheck.good())
        {
            std::string settingsPath = configDir + "/settings.cfg";
            std::ifstream settingsInput(settingsPath.c_str());
            std::vector<std::string> lines;
            std::string line;
            bool sawMusicDevice = false;
            bool sawSoundFont = false;
            while (std::getline(settingsInput, line))
            {
                std::string trimmed = line;
                size_t first = trimmed.find_first_not_of(" \t");
                if (first != std::string::npos) trimmed.erase(0, first);
                if (trimmed.compare(0, 9, "mus_device") == 0)
                {
                    line = "mus_device 4";
                    sawMusicDevice = true;
                }
                else if (trimmed.compare(0, 12, "mus_sf2_bank") == 0)
                {
                    line = "mus_sf2_bank \"GeneralUser GS SoftSynth v1.44.sf2\"";
                    sawSoundFont = true;
                }
                lines.push_back(line);
            }
            if (!sawMusicDevice) lines.push_back("mus_device 4");
            if (!sawSoundFont) lines.push_back("mus_sf2_bank \"GeneralUser GS SoftSynth v1.44.sf2\"");
            std::ofstream settingsOutput(settingsPath.c_str(), std::ios::trunc);
            if (settingsOutput.good())
            {
                for (auto const &entry : lines) settingsOutput << entry << "\n";
                settingsOutput.close();
                std::ofstream migrated(gsMigrationMarker.c_str());
                migrated << "done\n";
            }
        }

        // BloodCM is an EDuke32 total conversion with its CON sources supplied
        // in the selected mod folder. Keep all generated files in its profile.
        std::ifstream presetInput((configDir + "/android-game-preset.txt").c_str());
        std::string preset;
        std::getline(presetInput, preset);
        if (preset == "bloodcm")
        {
            std::ifstream folderInput((configDir + "/android-bloodcm-dir.txt").c_str());
            std::string folder;
            std::getline(folderInput, folder);
            std::string group = AndroidFindChild(folder, "BloodCM.grp");
            std::string code = AndroidFindChild(folder, "blcons");
            std::string script = AndroidFindChild(code, "bloodcm.con");
            struct stat groupStat, scriptStat;
            if (group.empty() || script.empty()
                || stat(group.c_str(), &groupStat) != 0 || groupStat.st_size <= 16
                || stat(script.c_str(), &scriptStat) != 0)
            {
                SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, "BloodCM",
                    "Select the BloodCM folder using Game folder & mods in the launcher.", nullptr);
                return 1;
            }
            std::string profiles = configDir + "/presets";
            std::string profile = profiles + "/bloodcm";
            mkdir(profiles.c_str(), 0700);
            mkdir(profile.c_str(), 0700);
            // A local alias lets the VFS open the shared bank without absolute paths.
            symlink(soundFontPath.c_str(), (profile + "/generaluser.sf2").c_str());
#ifdef POLYMER
            nogl = AndroidPolymerEnabled(profile + "/bloodcm-android.cfg") ? 0 : 1;
#endif
            unsetenv("DUKE3DGRP");
            if (chdir(profile.c_str()) != 0)
            {
                SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, "BloodCM",
                    "Could not open the BloodCM settings folder.", nullptr);
                return 1;
            }
            std::vector<char const *> launchArgs;
            launchArgs.reserve(argc + 13);
            launchArgs.push_back(argc > 0 ? argv[0] : "eduke32");
            launchArgs.push_back("-usecwd");
            launchArgs.push_back("-nosetup");
            launchArgs.push_back("-game_dir");
            launchArgs.push_back(folder.c_str());
            launchArgs.push_back("-gamegrp");
            std::string groupName = group.substr(folder.size() + 1);
            launchArgs.push_back(groupName.c_str());
            launchArgs.push_back("-x");
            launchArgs.push_back(script.c_str());
            launchArgs.push_back("-h");
            launchArgs.push_back("bloodcm.def");
            launchArgs.push_back("-cfg");
            launchArgs.push_back("bloodcm-android.cfg");
            for (int i = 1; i < argc; ++i) launchArgs.push_back(argv[i]);
            g_bloodCMCompat = 1;
            SDL_Log("BloodCM legacy animation/projectile compatibility enabled");
            return AndroidRunEngine((int)launchArgs.size(), launchArgs.data());
        }


        std::string migrationMarker = configDir + "/android-config-migrated.txt";
        std::string gameMarker = configDir + "/android-game-dir.txt";
        std::ifstream migrationCheck(migrationMarker.c_str());
        if (!migrationCheck.good())
        {
            std::ifstream gameMarkerInput(gameMarker.c_str());
            std::string legacyGameDir;
            std::getline(gameMarkerInput, legacyGameDir);
            if (!legacyGameDir.empty())
            {
                const char *legacyFiles[][2] = {
                    { "eduke32.cfg", "eduke32.cfg" },
                    { "settings.cfg", "settings.cfg" },
                    { "eduke32_settings.cfg", "settings.cfg" }
                };
                for (auto const &file : legacyFiles)
                {
                    std::string source = legacyGameDir + "/" + file[0];
                    std::string target = configDir + "/" + file[1];
                    std::ifstream existing(target.c_str(), std::ios::binary);
                    if (!existing.good())
                    {
                        std::ifstream input(source.c_str(), std::ios::binary);
                        if (input.good())
                        {
                            std::ofstream output(target.c_str(), std::ios::binary);
                            if (output.good()) output << input.rdbuf();
                        }
                    }
                }
            }
            std::ofstream migrationDone(migrationMarker.c_str());
            migrationDone << "done\n";
        }

        std::string marker = gameMarker;
        std::ifstream input(marker.c_str());
        std::string gameDir;
        if (std::getline(input, gameDir) && !gameDir.empty())
        {
            unsetenv("DUKE3DGRP");
            std::string homePrefix = std::string(home) + "/";
            std::string relativeGameDir = gameDir;
            if (gameDir.compare(0, homePrefix.size(), homePrefix) == 0)
                relativeGameDir = "../../" + gameDir.substr(homePrefix.size());

            if (chdir(configDir.c_str()) == 0)
            {
                std::vector<char const *> launchArgs;
                launchArgs.reserve(argc + 2);
                if (argc > 0)
                    launchArgs.push_back(argv[0]);
                launchArgs.push_back("-game_dir");
                launchArgs.push_back(relativeGameDir.c_str());
                for (int i = 1; i < argc; ++i)
                    launchArgs.push_back(argv[i]);
                return AndroidRunEngine((int)launchArgs.size(), launchArgs.data());
            }
        }
        if (chdir(configDir.c_str()) == 0)
        {
            return AndroidRunEngine(argc, const_cast<char const **>(argv));
        }
    }
    return AndroidRunEngine(argc, const_cast<char const **>(argv));
}

void AndroidShowKeyboard(int enabled)
{
    if (enabled) SDL_StartTextInput(); else SDL_StopTextInput();
}

void AndroidToggleButtonEditor(void) {}
