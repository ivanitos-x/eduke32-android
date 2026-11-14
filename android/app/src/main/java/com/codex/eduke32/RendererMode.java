package com.codex.eduke32;

/** Rendering algorithm and color depth are separate engine settings. */
enum RendererMode {
    CLASSIC("classic", "Classic — 8-bit", "Original software renderer", 8, false),
    POLYMOST_16("polymost16", "Polymost — 16-bit", "OpenGL · RGB565", 16, false),
    POLYMOST("polymost", "Polymost — 32-bit", "OpenGL · textures and 3D models", 32, false),
    POLYMER("polymer", "Polymer — 32-bit", "OpenGL · dynamic lighting", 32, true);

    final String id, title, description;
    final int colorDepth;
    final boolean polymer;
    RendererMode(String id,String title,String description,int colorDepth,boolean polymer) {
        this.id=id;this.title=title;this.description=description;
        this.colorDepth=colorDepth;this.polymer=polymer;
    }
    static RendererMode fromId(String id) {
        for(RendererMode mode:values())if(mode.id.equals(id))return mode;
        return CLASSIC;
    }
}
