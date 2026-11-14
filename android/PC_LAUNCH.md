# PC launch scripts

Select a direct working folder, then **PC launch script** and a `.bat` or `.cmd`
in that folder. The launcher imports one EDuke32 command and passes its arguments
to the Android engine. The working folder stays the starting directory; the
engine handles options such as `-j`, `-game_dir`, `-x`, `-h` and `-g` normally.
Script arguments follow the launcher's default arguments. Selection is stored
per game preset, included in the restart signature, and reparsed before launch.
Select **None - normal launch** to clear it. Emergency launch ignores the script.

This is a limited importer, not Windows command execution. Blank lines, REM/::
comments and basic echo/cls/pause presentation are ignored. A single eduke32 or
eduke32.exe invocation supports quoted arguments and converts backslash paths.
Variables, command operators, unknown commands, malformed quotes and multiple
engine launches report an error. Other executables are not run. Scripts that
change directory or call other scripts require additional explicit support.

Confirmed source example from Imperium2011:

    EDuke32.exe -jImperium -game_dir DukePlus -forcegl

The previous launcher omitted all three options and loaded original GAME.CON.
Choosing a script containing -forcegl selects Polymost if Classic was selected.
The user can choose Polymer separately if required by the content.

Validation: actual Imperium.bat parameter extraction, quoted Windows paths,
unsupported shell/variable/multiple-launch/malformed-command rejection, and
APK compilation passed. Full mod compatibility requires device gameplay checks.
