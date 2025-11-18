package com.hexagram2021.tpmaster.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;
import java.util.Set;

@Mixin(TeleportCommand.class)
public interface TeleportCommandInvoker {

    @Invoker("performTeleport")
    static void callPerformTeleport(
            CommandSourceStack source,
            Entity entity,
            ServerLevel level,
            double x, double y, double z,
            Set<RelativeMovement> movement,
            float yaw, float pitch,
            @Nullable TeleportCommand.LookAt lookAt
    ) {
        throw new AssertionError();
    }
}
