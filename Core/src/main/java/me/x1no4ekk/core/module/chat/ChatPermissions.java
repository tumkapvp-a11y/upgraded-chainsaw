package me.x1no4ekk.core.module.chat;

/**
 * Permission nodes checked by the chat module (also listed in fragments/chat.permissions.yml).
 */
final class ChatPermissions {

    static final String ADMIN = "core.chat.admin";
    static final String BYPASS_CLEAR = "core.chat.bypass-clear";
    static final String BYPASS_MUTE = "core.chat.bypass-mute";
    static final String BYPASS_COOLDOWN = "core.chat.bypass-cooldown";
    static final String CHAT_TOGGLE = "core.chattoggle";
    static final String DONATE = "core.chat.donate";
    static final String STAFF = "core.chat.staff";

    static final String MSG = "core.msg";
    static final String MSG_TOGGLE = "core.msgtoggle";
    static final String MSG_BYPASS_TOGGLE = "core.msg.bypass-toggle";
    static final String MSG_BYPASS_COOLDOWN = "core.msg.bypass-cooldown";
    static final String SOCIAL_SPY = "core.socialspy";
    static final String TARGET_SPY = "core.targetspy";

    static final String IGNORE = "core.ignore";
    static final String IGNORE_EXEMPT = "core.ignore.exempt";

    static final String BROADCAST_FORMAT = "core.broadcast.format";
    static final String BROADCAST_BYPASS_COOLDOWN = "core.broadcast.bypass-cooldown";
    static final String BROADCAST_FREE = "core.broadcast.free";

    private ChatPermissions() {
    }
}
