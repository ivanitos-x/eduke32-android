# Working-directory storage

Game folder & mods -> Use working folder directly (PC layout) selects the native
working directory for both reads and writes: resources, saves, configs, logs
and disk cache use the engine's normal relative paths. Grant storage access in
the system dialog. Access failures report an error; launcher metadata stays in
app storage. Protected Android directories remain inaccessible.

Resource loading defaults to demand loading on Android. Optional map art and
all-sound preloads are skipped. Existing lazy loaders, decoders and eviction
are retained. Preloading can be restored in Resource loading on restart.
Active sounds, decoded textures, world/VM state, GPU textures and the filesystem
page cache still require memory. First-use stalls are possible; RAM savings
are not measured.

Import previous Android progress shows source, destination and file count before
confirmation. Stop the game first. Copying uses 64 KiB buffers and stages inputs;
originals are retained. Destination conflicts are backed up in the working folder
under android-before-import-<UUID>/previous/. Commit errors attempt rollback and
retain recovery files. The multi-file transfer is not power-loss atomic.

Legacy SAF import/backup mode remains available. Direct mode disables legacy
whole-profile ZIP restore. Emergency launch uses a disposable configuration.
Build and host transfer checks pass. Direct-mode permission UI, device gameplay,
save/load round trip and memory measurements remain unverified.
