package com.madolivegaming.playermessagesplus;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PluginSettings {
    public boolean useCustomJoinMessages = true;
    public boolean usePersonalJoinMessages = true;
    public boolean useCustomLeaveMessages = true;
    public boolean usePersonalLeaveMessages = true;
    public boolean useCustomRejoinMessages = true;
    public boolean usePersonalRejoinMessages = true;
    public int rejoinMessageWindow = 30;
    public int suppressLeaveMessageDuringRejoin = 0;
    public boolean colors = true;
    public boolean bold = true;
    public boolean italic = true;
    public boolean underlined = true;
    public boolean strikethrough = true;
    public boolean obfuscated = true;
    public boolean gradient = true;
    public boolean rainbow = true;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("useCustomJoinMessages", useCustomJoinMessages);
        m.put("usePersonalJoinMessages", usePersonalJoinMessages);
        m.put("useCustomLeaveMessages", useCustomLeaveMessages);
        m.put("usePersonalLeaveMessages", usePersonalLeaveMessages);
        m.put("useCustomRejoinMessages", useCustomRejoinMessages);
        m.put("usePersonalRejoinMessages", usePersonalRejoinMessages);
        m.put("rejoinMessageWindow", rejoinMessageWindow);
        m.put("suppressLeaveMessageDuringRejoin", suppressLeaveMessageDuringRejoin);
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("colors", colors); tags.put("bold", bold); tags.put("italic", italic);
        tags.put("underlined", underlined); tags.put("strikethrough", strikethrough);
        tags.put("obfuscated", obfuscated); tags.put("gradient", gradient); tags.put("rainbow", rainbow);
        m.put("minimessageTags", tags);
        return m;
    }

    public PluginSettings copy() {
        PluginSettings s = new PluginSettings();
        s.useCustomJoinMessages = useCustomJoinMessages; s.usePersonalJoinMessages = usePersonalJoinMessages;
        s.useCustomLeaveMessages = useCustomLeaveMessages; s.usePersonalLeaveMessages = usePersonalLeaveMessages;
        s.useCustomRejoinMessages = useCustomRejoinMessages; s.usePersonalRejoinMessages = usePersonalRejoinMessages;
        s.rejoinMessageWindow = rejoinMessageWindow; s.suppressLeaveMessageDuringRejoin = suppressLeaveMessageDuringRejoin;
        s.colors = colors; s.bold = bold; s.italic = italic; s.underlined = underlined;
        s.strikethrough = strikethrough; s.obfuscated = obfuscated; s.gradient = gradient; s.rainbow = rainbow;
        return s;
    }

    public boolean anyRejoinMessageEnabled() {
        return useCustomRejoinMessages || usePersonalRejoinMessages;
    }
}
