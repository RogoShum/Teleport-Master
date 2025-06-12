package com.hexagram2021.tpmaster.server.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Map;

public interface ITeleportable {
    @Nullable
    Entity getTeleportMasterRequester();

    @Nullable
    RequestType getRequestType();

    void clearTeleportMasterRequest();

    void setTeleportMasterRequest(@NotNull ITeleportable target, @NotNull RequestType type);

    void receiveTeleportMasterRequestFrom(@NotNull Entity from, @NotNull RequestType type);

    void setTeleportMasterAway();

    boolean canUseTeleportMasterAway();

    boolean canUseTeleportMasterRequest();

    int getTeleportMasterAwayCoolDownTick();

    int getTeleportMasterRequestCoolDownTick();

    void setTeleportMasterHome(GlobalPos pos, String name) throws CommandSyntaxException;

    @Nullable
    GlobalPos getTeleportMasterHome(String name);

    Map<String, GlobalPos> getTeleportMasterHomes();

    void setTeleportMasterLastLocation(@Nullable GlobalPos pos, boolean isDeath);

    @Nullable
    GlobalPos getTeleportMasterLastLocation();

    @Nullable
    GlobalPos getTeleportMasterLastDeathLocation();

    enum RequestType {
        ASK,        //Ask if requester can teleport to where requestee is.
        INVITE        //Invite requestee to come to where requester is.
    }
}
