package com.hexagram2021.tpmaster.mixin;

import com.hexagram2021.tpmaster.TeleportMaster;
import com.hexagram2021.tpmaster.server.TextFormatter;
import com.hexagram2021.tpmaster.server.commands.TPMCommands;
import com.hexagram2021.tpmaster.server.config.TPMServerConfig;
import com.hexagram2021.tpmaster.server.util.ITeleportable;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.hexagram2021.tpmaster.server.config.TPMServerConfig.*;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements ITeleportable {
    @Shadow
    @Final
    public MinecraftServer server;

    private Entity teleportMasterRequester = null;
    private RequestType requestType = null;

    private int teleportMasterAwayCoolDownTicks = 0;
    private int teleportMasterRequestCoolDownTicks = 0;
    private int teleportMasterAutoDenyTicks = 0;

    private final Map<String, GlobalPos> teleportMasterHomes = new HashMap<>();
    private GlobalPos lastTeleportLocation = null;
    private GlobalPos lastDeathLocation = null;

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;tick()V", shift = At.Shift.AFTER))
    public void tickTeleportMaster(CallbackInfo ci) {
        if (this.teleportMasterAwayCoolDownTicks > 0) {
            --this.teleportMasterAwayCoolDownTicks;
        }
        if (this.teleportMasterRequestCoolDownTicks > 0) {
            --this.teleportMasterRequestCoolDownTicks;
        }
        if (this.teleportMasterAutoDenyTicks > 0) {
            --this.teleportMasterAutoDenyTicks;
            if (this.teleportMasterAutoDenyTicks == 0) {
                try {
                    TPMCommands.deny((ServerPlayer) (Object) this);
                } catch (CommandSyntaxException ignored) {
                }
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At(value = "TAIL"))
    public void readTeleportMasterData(CompoundTag nbt, CallbackInfo ci) {
        if (nbt.contains("TeleportMasterRequester", Tag.TAG_INT_ARRAY)) {
            UUID uuid = nbt.getUUID("TeleportMasterRequester");
            if (uuid.equals(Util.NIL_UUID)) {
                this.teleportMasterRequester = null;
            } else {
                this.teleportMasterRequester = ((ServerPlayer) (Object) this).level().getPlayerByUUID(uuid);
            }
        }
        if (nbt.contains("RequestType", Tag.TAG_BYTE)) {
            byte req = nbt.getByte("RequestType");
            if (req <= 0 || req > RequestType.values().length) {
                this.requestType = null;
            } else {
                this.requestType = RequestType.values()[req - 1];
            }
        }

        this.teleportMasterAwayCoolDownTicks = nbt.getInt("TeleportMasterAwayCoolDownTicks");
        this.teleportMasterRequestCoolDownTicks = nbt.getInt("TeleportMasterRequestCoolDownTicks");
        this.teleportMasterAutoDenyTicks = nbt.getInt("TeleportMasterAutoDenyTicks");

        if (nbt.contains("TeleportMasterHomes", Tag.TAG_COMPOUND)) {
            CompoundTag homesTag = nbt.getCompound("TeleportMasterHomes");
            for (String name : homesTag.getAllKeys()) {
                CompoundTag homeTag = homesTag.getCompound(name);
                String dimension = homeTag.getString("dimension");
                this.server.levelKeys().stream()
                        .filter(key -> key.location().toString().equals(dimension))
                        .findFirst()
                        .ifPresentOrElse(
                                key -> this.teleportMasterHomes.put(name.replace(":", "-"),
                                        GlobalPos.of(key, BlockPos.of(homeTag.getLong("pos")))),
                                () -> TeleportMaster.LOGGER.error(
                                        "没有叫做 \"{}\" 的维度", dimension)
                        );
            }
        }

        if (nbt.contains("LastTeleportLocation", Tag.TAG_COMPOUND)) {
            CompoundTag locTag = nbt.getCompound("LastTeleportLocation");
            String dimension = locTag.getString("dimension");
            this.server.levelKeys().stream()
                    .filter(key -> key.location().toString().equals(dimension))
                    .findFirst()
                    .ifPresentOrElse(
                            key -> this.lastTeleportLocation =
                                    GlobalPos.of(key, BlockPos.of(locTag.getLong("pos"))),
                            () -> TeleportMaster.LOGGER.error(
                                    "没有叫做 \"{}\" 的维度", dimension)
                    );
        }

        if (nbt.contains("LastDeathLocation", Tag.TAG_COMPOUND)) {
            CompoundTag locTag = nbt.getCompound("LastDeathLocation");
            String dimension = locTag.getString("dimension");
            this.server.levelKeys().stream()
                    .filter(key -> key.location().toString().equals(dimension))
                    .findFirst()
                    .ifPresentOrElse(
                            key -> this.lastDeathLocation =
                                    GlobalPos.of(key, BlockPos.of(locTag.getLong("pos"))),
                            () -> TeleportMaster.LOGGER.error(
                                    "没有叫做 \"{}\" 的维度", dimension)
                    );
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At(value = "TAIL"))
    public void addTeleportMasterData(CompoundTag nbt, CallbackInfo ci) {
        nbt.putUUID("TeleportMasterRequester", this.teleportMasterRequester == null ? Util.NIL_UUID : this.teleportMasterRequester.getUUID());
        nbt.putByte("RequestType", (byte) (this.requestType == null ? 0 : this.requestType.ordinal() + 1));
        nbt.putInt("TeleportMasterAwayCoolDownTicks", this.teleportMasterAwayCoolDownTicks);
        nbt.putInt("TeleportMasterRequestCoolDownTicks", this.teleportMasterRequestCoolDownTicks);
        nbt.putInt("TeleportMasterAutoDenyTicks", this.teleportMasterAutoDenyTicks);

        CompoundTag homesTag = new CompoundTag();
        for (Map.Entry<String, GlobalPos> entry : this.teleportMasterHomes.entrySet()) {
            CompoundTag homeTag = new CompoundTag();
            GlobalPos pos = entry.getValue();
            homeTag.putString("dimension", pos.dimension().location().toString());
            homeTag.putLong("pos", pos.pos().asLong());
            homesTag.put(entry.getKey(), homeTag);
        }
        nbt.put("TeleportMasterHomes", homesTag);

        if (this.lastTeleportLocation != null) {
            CompoundTag locTag = new CompoundTag();
            locTag.putString("dimension", this.lastTeleportLocation.dimension().location().toString());
            locTag.putLong("pos", this.lastTeleportLocation.pos().asLong());
            nbt.put("LastTeleportLocation", locTag);
        }

        if (this.lastDeathLocation != null) {
            CompoundTag locTag = new CompoundTag();
            locTag.putString("dimension", this.lastDeathLocation.dimension().location().toString());
            locTag.putLong("pos", this.lastDeathLocation.pos().asLong());
            nbt.put("LastDeathLocation", locTag);
        }
    }

    @Inject(method = "die", at = @At(value = "TAIL"))
    public void recordDeathPoint(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer current = (ServerPlayer) (Object) this;
        BlockPos pos = current.blockPosition();
        setTeleportMasterLastLocation(GlobalPos.of(current.level().dimension(), pos), true);
        current.sendSystemMessage(TextFormatter.format("您在 {} 的 {} 位置坐标死亡", current.level().dimension().location(), pos)
                .withStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/back dead"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击传送").withStyle(ChatFormatting.GREEN)))));
    }

    @Inject(method = "restoreFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setLastDeathLocation(Ljava/util/Optional;)V", shift = At.Shift.AFTER))
    public void restoreTeleportMasterData(ServerPlayer source, boolean won, CallbackInfo ci) {
        if (source instanceof ITeleportable teleportable) {
            this.teleportMasterRequester = teleportable.getTeleportMasterRequester();
            this.requestType = teleportable.getRequestType();
            this.teleportMasterAwayCoolDownTicks = teleportable.getTeleportMasterAwayCoolDownTick();
            this.teleportMasterRequestCoolDownTicks = teleportable.getTeleportMasterRequestCoolDownTick();
            this.teleportMasterAutoDenyTicks = TPMServerConfig.REQUEST_COMMAND_AUTO_DENY_TICK.get();

            // 更新家的存储
            this.teleportMasterHomes.clear();
            this.teleportMasterHomes.putAll(teleportable.getTeleportMasterHomes());

            // 更新最后位置
            this.lastTeleportLocation = teleportable.getTeleportMasterLastLocation();
            this.lastDeathLocation = teleportable.getTeleportMasterLastDeathLocation();
        }
    }

    @Override
    @Nullable
    public Entity getTeleportMasterRequester() {
        return this.teleportMasterRequester;
    }

    @Override
    @Nullable
    public RequestType getRequestType() {
        return this.requestType;
    }

    @Override
    public void setTeleportMasterRequest(@NotNull ITeleportable target, @NotNull RequestType type) {
        target.receiveTeleportMasterRequestFrom((Entity) (Object) this, type);
        if (!((ServerPlayer) (Object) this).getAbilities().instabuild) {
            this.teleportMasterRequestCoolDownTicks = REQUEST_COMMAND_COOL_DOWN_TICK.get();
        }
    }

    @Override
    public void receiveTeleportMasterRequestFrom(@NotNull Entity from, @NotNull RequestType type) {
        this.teleportMasterRequester = from;
        this.requestType = type;

        this.teleportMasterAutoDenyTicks = TPMServerConfig.REQUEST_COMMAND_AUTO_DENY_TICK.get();
    }

    @Override
    public void setTeleportMasterAway() {
        if (!((ServerPlayer) (Object) this).getAbilities().instabuild) {
            this.teleportMasterAwayCoolDownTicks = AWAY_COMMAND_COOL_DOWN_TICK.get();
        }
    }

    @Override
    public void clearTeleportMasterRequest() {
        this.teleportMasterRequester = null;
        this.requestType = null;
    }

    @Override
    public boolean canUseTeleportMasterAway() {
        return this.teleportMasterAwayCoolDownTicks <= 0;
    }

    @Override
    public boolean canUseTeleportMasterRequest() {
        return this.teleportMasterRequestCoolDownTicks <= 0;
    }

    @Override
    public int getTeleportMasterAwayCoolDownTick() {
        return this.teleportMasterAwayCoolDownTicks;
    }

    @Override
    public int getTeleportMasterRequestCoolDownTick() {
        return this.teleportMasterRequestCoolDownTicks;
    }

    @Override
    public void setTeleportMasterHome(GlobalPos pos, String name) throws CommandSyntaxException {
        if (name == null || name.isEmpty()) {
            throw new SimpleCommandExceptionType(
                    Component.literal("家的名称不能为空")
            ).create();
        }
        if (this.teleportMasterHomes.size() >= MAX_HOME_COUNT.get() && !this.teleportMasterHomes.containsKey(name)) {
            throw new SimpleCommandExceptionType(
                    Component.literal("您已达到最大家的数量限制")
            ).create();
        }

        if (pos == null) {
            this.teleportMasterHomes.remove(name);
        } else {
            this.teleportMasterHomes.put(name, pos);
        }
    }

    @Override
    @Nullable
    public GlobalPos getTeleportMasterHome(String name) {
        return this.teleportMasterHomes.get(name);
    }

    @Override
    public Map<String, GlobalPos> getTeleportMasterHomes() {
        return this.teleportMasterHomes;
    }

    @Override
    public void setTeleportMasterLastLocation(@Nullable GlobalPos pos, boolean isDeath) {
        if (isDeath) {
            this.lastDeathLocation = pos;
        }

        this.lastTeleportLocation = pos;
    }

    @Override
    @Nullable
    public GlobalPos getTeleportMasterLastLocation() {
        return this.lastTeleportLocation;
    }

    @Override
    @Nullable
    public GlobalPos getTeleportMasterLastDeathLocation() {
        return this.lastDeathLocation;
    }
}
