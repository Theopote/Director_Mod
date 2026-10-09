package com.director.util;

/** ImGui text buffers may retain bytes after their terminating NUL. */
public final class DisplayNames {
    private DisplayNames() {}
    public static String clean(String value) {
        if(value==null)return null;
        int end=value.indexOf('\0');
        return (end<0?value:value.substring(0,end)).strip();
    }
}
