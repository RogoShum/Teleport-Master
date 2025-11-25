package com.hexagram2021.tpmaster.server.commands;

import com.hexagram2021.tpmaster.mixin.TeleportCommandInvoker;
import com.hexagram2021.tpmaster.server.TextFormatter;
import com.hexagram2021.tpmaster.server.config.TPMServerConfig;
import com.hexagram2021.tpmaster.server.util.ITeleportable;
import com.hexagram2021.tpmaster.server.util.LevelUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class TPMCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
                .then(Commands.literal("accept").requires(stack -> stack.hasPermission(TPMServerConfig.ACCEPT_DENY_PERMISSION_LEVEL.get()))
                        .executes(context -> accept(context.getSource(), context.getSource().getEntityOrException())))
                .then(Commands.literal("deny").requires(stack -> stack.hasPermission(TPMServerConfig.ACCEPT_DENY_PERMISSION_LEVEL.get()))
                        .executes(context -> deny(context.getSource().getEntityOrException()))));

        dispatcher.register(Commands.literal("tpr").requires(stack -> stack.hasPermission(TPMServerConfig.AWAY_PERMISSION_LEVEL.get()))
                .executes(context -> away(context.getSource(), context.getSource().getEntityOrException(), 0, true, null))
                .then(
                        Commands.argument("distance", IntegerArgumentType.integer(0, 10000))
                                .executes(context -> away(context.getSource(), context.getSource().getEntityOrException(), context.getArgument("distance", Integer.class), true, null))
                                .then(
                                        Commands.argument("mustOnLand", BoolArgumentType.bool())
                                                .executes(context -> away(context.getSource(), context.getSource().getEntityOrException(), context.getArgument("distance", Integer.class), context.getArgument("mustOnLand", Boolean.class), null))
                                )
                ));

        dispatcher.register(Commands.literal("tpa").requires(stack -> stack.hasPermission(TPMServerConfig.REQUEST_PERMISSION_LEVEL.get()))
                .then(
                        Commands.argument("target", EntityArgument.entity())
                                .executes(context -> request(context.getSource(), context.getSource().getEntityOrException(), EntityArgument.getEntity(context, "target"), ITeleportable.RequestType.ASK))
                                .then(
                                        Commands.literal("ask")
                                                .executes(context -> request(context.getSource(), context.getSource().getEntityOrException(), EntityArgument.getEntity(context, "target"), ITeleportable.RequestType.ASK))
                                )
                                .then(
                                        Commands.literal("invite")
                                                .executes(context -> request(context.getSource(), context.getSource().getEntityOrException(), EntityArgument.getEntity(context, "target"), ITeleportable.RequestType.INVITE))
                                )
                ));

        dispatcher.register(Commands.literal("spawn").requires(stack -> stack.hasPermission(TPMServerConfig.SPAWN_PERMISSION_LEVEL.get()))
                .executes(context -> spawn(context.getSource(), context.getSource().getEntityOrException())));

        dispatcher.register(Commands.literal("sethome").requires(stack -> stack.hasPermission(TPMServerConfig.HOME_PERMISSION_LEVEL.get()))
                .executes(context -> sethome(
                        context.getSource().getEntityOrException(),
                        null
                ))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> sethome(
                                context.getSource().getEntityOrException(),
                                StringArgumentType.getString(context, "name")
                        ))));

        dispatcher.register(Commands.literal("home").requires(stack -> stack.hasPermission(TPMServerConfig.HOME_PERMISSION_LEVEL.get()))
                .executes(context -> listOrTeleportHome(context.getSource(), context.getSource().getEntityOrException()))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(HOME_SUGGESTIONS)
                        .executes(context -> home(
                                context.getSource(),
                                context.getSource().getEntityOrException(),
                                StringArgumentType.getString(context, "name")
                        )))
                .then(Commands.literal("del").then(
                                Commands.argument("name", StringArgumentType.word())
                                        .suggests(HOME_SUGGESTIONS)
                                        .executes(context -> removeHome(context.getSource().getEntityOrException(), StringArgumentType.getString(context, "name")))
                        )
                ).then(Commands.literal("set")
                        .executes(context -> sethome(context.getSource().getEntityOrException(), null))
                        .then(Commands.argument("name", StringArgumentType.word()).executes(context -> sethome(
                                context.getSource().getEntityOrException(),
                                StringArgumentType.getString(context, "name"))))
                ));

        dispatcher.register(Commands.literal("delhome").requires(stack -> stack.hasPermission(TPMServerConfig.HOME_PERMISSION_LEVEL.get()))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(HOME_SUGGESTIONS)
                        .executes(context -> removeHome(context.getSource().getEntityOrException(), StringArgumentType.getString(context, "name")))
                ));

        dispatcher.register(Commands.literal("back").requires(stack -> stack.hasPermission(TPMServerConfig.BACK_PERMISSION_LEVEL.get()))
                .executes(context -> back(context.getSource(), context.getSource().getEntityOrException(), false))
                .then(Commands.literal("dead")
                        .executes(context -> back(context.getSource(), context.getSource().getEntityOrException(), true))));
    }

    // 异常定义
    private static final SimpleCommandExceptionType NO_NEED_TO_ACCEPT = new SimpleCommandExceptionType(
            TextFormatter.format("您没有收到任何传送请求，无需同意")
    );

    private static final SimpleCommandExceptionType NO_NEED_TO_DENY = new SimpleCommandExceptionType(
            TextFormatter.format("您没有收到任何传送请求，无需拒绝")
    );

    private static final DynamicCommandExceptionType TARGET_UNHANDLED_RESERVATION = new DynamicCommandExceptionType(
            (name) -> TextFormatter.format("{} 有一条未处理的传送请求，请等待一会", name)
    );

    private static final DynamicCommandExceptionType INVALID_AWAY_DISTANCE_PARAMETER = new DynamicCommandExceptionType(
            (d) -> TextFormatter.format("非法的参数 distance = {}，其范围为 0 < distance < 10000", d)
    );

    private static final SimpleCommandExceptionType CANNOT_FIND_POSITION = new SimpleCommandExceptionType(
            TextFormatter.format("找不到合法的传送位置。请等待一会重试（建议尝试使用不同的参数）")
    );

    private static final DynamicCommandExceptionType NO_HOME_WITH_NAME = new DynamicCommandExceptionType(
            name -> TextFormatter.format("找不到名为 {} 的家", name)
    );

    private static final DynamicCommandExceptionType NO_LEVEL_FOUNDED_TO_HOME = new DynamicCommandExceptionType(
            (level) -> TextFormatter.format("无法回家，失配的维度 {}", level)
    );

    private static final SimpleCommandExceptionType NO_LOCATION_TO_BACK = new SimpleCommandExceptionType(
            TextFormatter.format("无法返回，您没有上一个传送或死亡位置")
    );

    private static final DynamicCommandExceptionType NO_LEVEL_FOUNDED_TO_BACK = new DynamicCommandExceptionType(
            (level) -> TextFormatter.format("无法回到死亡点，失配的维度{}", level));

    private static final DynamicCommandExceptionType COOL_DOWN_AWAY = new DynamicCommandExceptionType(
            (d) -> TextFormatter.format("您使用指令 {} 太过频繁！请等待 {} 秒",
                    Component.literal("/tpr").withStyle(ChatFormatting.YELLOW), d)
    );
    private static final DynamicCommandExceptionType COOL_DOWN_REQUEST = new DynamicCommandExceptionType(
            (d) -> TextFormatter.format("您使用指令 {} 太过频繁！请等待 {} 秒",
                    Component.literal("/tpa").withStyle(ChatFormatting.YELLOW), d)
    );

    public static int deny(Entity entity) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            Entity requester = teleportable.getTeleportMasterRequester();
            if (requester == null) {
                throw NO_NEED_TO_DENY.create();
            }

            entity.sendSystemMessage(TextFormatter.format("您拒绝了 {} 的传送请求", requester));
            requester.sendSystemMessage(TextFormatter.format("您的传送请求已被 {} 拒绝", entity));
            teleportable.clearTeleportMasterRequest();
        }
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("SameParameterValue")
    private static int away(CommandSourceStack stack, Entity entity, int distance, boolean mustOnLand, @Nullable TeleportCommand.LookAt lookAt) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            if (!teleportable.canUseTeleportMasterAway()) {
                throw COOL_DOWN_AWAY.create(teleportable.getTeleportMasterAwayCoolDownTick() / 20);
            }
            teleportable.setTeleportMasterAway();
        }
        if (distance == 0) {
            distance = entity.level().getRandom().nextInt(600) + 600;
        } else if (distance < 0 || distance > 10000) {
            throw INVALID_AWAY_DISTANCE_PARAMETER.create(distance);
        }

        if (entity instanceof ITeleportable teleportable) {
            // 保存当前位置
            teleportable.setTeleportMasterLastLocation(
                    GlobalPos.of(entity.level().dimension(), entity.blockPosition()),
                    false
            );
        }

        boolean flag = false;
        RandomSource random = entity.level().getRandom();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        for (int i = 0; i < TPMServerConfig.AWAY_TRY_COUNT.get(); ++i) {
            double phi = random.nextDouble() * 2.0D * Math.acos(-1.0D);
            x = entity.getX() + distance * Math.cos(phi) + random.nextDouble() * TPMServerConfig.AWAY_NOISE_BOUND.get() * distance;
            z = entity.getZ() + distance * Math.sin(phi) + random.nextDouble() * TPMServerConfig.AWAY_NOISE_BOUND.get() * distance;
            BlockPos blockPos = new BlockPos((int) x, (int) 255.0D, (int) z);
            Biome biome = entity.level().getBiome(blockPos).value();
            boolean conti = false;
            if (mustOnLand) {
                for (String ocean : TPMServerConfig.OCEAN_BIOME_KEYS.get()) {
                    ResourceLocation biomeId = ForgeRegistries.BIOMES.getKey(biome);
                    if (biomeId != null && biomeId.toString().equals(ocean)) {
                        conti = true;
                        break;
                    }
                }
            }
            if (!conti) {
                flag = true;
                y = LevelUtils.getTopBlock(entity.level(), blockPos);
                if (y < 8) {
                    continue;
                }
                break;
            }
        }
        if (!flag) {
            throw CANNOT_FIND_POSITION.create();
        }
        performTeleport(stack, entity, (ServerLevel) entity.level(), x, y, z,
                EnumSet.noneOf(RelativeMovement.class), entity.getYRot(), entity.getXRot(), lookAt);

        BlockPos finalPos = new BlockPos((int) x, (int) y, (int) z);
        entity.sendSystemMessage(TextFormatter.format(
                "成功将您随机传送到 {} 方块远的位置 {}",
                Component.literal(String.valueOf(distance)).withStyle(ChatFormatting.GOLD),
                Component.literal(finalPos.toShortString()).withStyle(ChatFormatting.AQUA)
        ));

        return Command.SINGLE_SUCCESS;
    }

    private static int listOrTeleportHome(CommandSourceStack stack, Entity entity) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            Map<String, GlobalPos> homes = teleportable.getTeleportMasterHomes();
            if (homes.isEmpty()) {
                entity.sendSystemMessage(Component.literal("您还没有设置任何家")
                        .withStyle(ChatFormatting.YELLOW));
                return Command.SINGLE_SUCCESS;
            }

            if (homes.size() == 1) {
                // 如果只有一个家，直接传送
                String homeName = homes.keySet().iterator().next();
                return home(stack, entity, homeName);
            }

            // 列出所有家
            MutableComponent message = Component.literal("您的家列表：\n")
                    .withStyle(ChatFormatting.YELLOW);

            for (Map.Entry<String, GlobalPos> entry : homes.entrySet()) {
                GlobalPos pos = entry.getValue();
                message = message.append(TextFormatter.format("- {} : {}\n",
                        Component.literal(entry.getKey())
                                .withStyle(Style.EMPTY
                                        .withColor(ChatFormatting.GREEN)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + entry.getKey()))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击传送到家").withStyle(ChatFormatting.GREEN)))),
                        Component.literal(String.format("(%d, %d, %d) in %s",
                                        pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(),
                                        pos.dimension().location()))
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + entry.getKey()))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击传送到家")
                                                .withStyle(ChatFormatting.GREEN))))
                ));
            }

            entity.sendSystemMessage(message);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int request(CommandSourceStack stack, Entity entity, Entity target, ITeleportable.RequestType type) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            if (!teleportable.canUseTeleportMasterRequest()) {
                throw COOL_DOWN_REQUEST.create(teleportable.getTeleportMasterRequestCoolDownTick() / 20);
            }
        }

        if (target instanceof ITeleportable teleportableTarget) {
            if (teleportableTarget.getTeleportMasterRequester() != null) {
                throw TARGET_UNHANDLED_RESERVATION.create(target.getName().getString());
            }
            if (entity instanceof ITeleportable teleportable) {
                teleportable.setTeleportMasterRequest(teleportableTarget, type);
            } else {
                teleportableTarget.receiveTeleportMasterRequestFrom(entity, type);
            }

            entity.sendSystemMessage(TextFormatter.format("您成功向 {} 发送了传送请求", target));

            Component message = Component.empty()
                    .append(Component.literal("【传送请求】").withStyle(ChatFormatting.YELLOW))
                    .append(TextFormatter.format("{} ", entity))
                    .append(Component.literal(switch (type) {
                        case ASK -> "希望传送到您的位置";
                        case INVITE -> "希望您传送到他/她的位置";
                    }).withStyle(ChatFormatting.WHITE))
                    .append("\n")
                    .append(Component.literal("[同意]")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.GREEN)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa accept"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("点击同意传送请求").withStyle(ChatFormatting.GREEN)))
                            )
                    )
                    .append(" | ")
                    .append(Component.literal("[拒绝]")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.RED)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa deny"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("点击拒绝传送请求").withStyle(ChatFormatting.RED)))
                            )
                    )
                    .append(TextFormatter.format("\n（此请求将在 {} 秒后自动拒绝）",
                            Component.literal(String.valueOf(TPMServerConfig.REQUEST_COMMAND_AUTO_DENY_TICK.get() / 20))
                                    .withStyle(ChatFormatting.GOLD)));

            target.sendSystemMessage(message);
        } else {
            entity.sendSystemMessage(TextFormatter.format("您成功向 {} 发送了传送请求", target));
            boolean flag1 = entity instanceof Monster || (entity instanceof NeutralMob && !(entity instanceof TamableAnimal));
            boolean flag2 = target instanceof Monster || (target instanceof NeutralMob && !(target instanceof TamableAnimal));
            if (flag1 == flag2) {
                // 保存传送前的位置
                if (type == ITeleportable.RequestType.ASK && entity instanceof ITeleportable teleportable) {
                    teleportable.setTeleportMasterLastLocation(
                            GlobalPos.of(entity.level().dimension(), entity.blockPosition()),
                            false
                    );
                } else if (type == ITeleportable.RequestType.INVITE && target instanceof ITeleportable teleportable) {
                    teleportable.setTeleportMasterLastLocation(
                            GlobalPos.of(target.level().dimension(), target.blockPosition()),
                            false
                    );
                }

                switch (type) {
                    case ASK -> performTeleport(
                            stack, entity, (ServerLevel) target.level(),
                            target.getX(), target.getY(), target.getZ(),
                            EnumSet.noneOf(RelativeMovement.class),
                            entity.getYRot(), entity.getXRot(), null
                    );
                    case INVITE -> performTeleport(
                            stack, target, (ServerLevel) entity.level(),
                            entity.getX(), entity.getY(), entity.getZ(),
                            EnumSet.noneOf(RelativeMovement.class),
                            target.getYRot(), target.getXRot(), null
                    );
                }
                entity.sendSystemMessage(TextFormatter.format("您的传送请求已被 {} 同意。你们将很快见面了", target));
            } else {
                entity.sendSystemMessage(TextFormatter.format("您的传送请求已被 {} 拒绝", target));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int accept(CommandSourceStack stack, Entity entity) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            Entity requester = teleportable.getTeleportMasterRequester();
            ITeleportable.RequestType requestType = teleportable.getRequestType();
            if (requester == null || requestType == null) {
                throw NO_NEED_TO_ACCEPT.create();
            }

            // 保存传送前的位置
            if (requestType == ITeleportable.RequestType.ASK && requester instanceof ITeleportable requesterTeleportable) {
                requesterTeleportable.setTeleportMasterLastLocation(
                        GlobalPos.of(requester.level().dimension(), requester.blockPosition()),
                        false
                );
            } else if (requestType == ITeleportable.RequestType.INVITE) {
                teleportable.setTeleportMasterLastLocation(
                        GlobalPos.of(entity.level().dimension(), entity.blockPosition()),
                        false
                );
            }

            switch (requestType) {
                case ASK -> performTeleport(
                        stack, requester, (ServerLevel) entity.level(),
                        entity.getX(), entity.getY(), entity.getZ(),
                        EnumSet.noneOf(RelativeMovement.class),
                        requester.getYRot(), requester.getXRot(), null
                );
                case INVITE -> performTeleport(
                        stack, entity, (ServerLevel) requester.level(),
                        requester.getX(), requester.getY(), requester.getZ(),
                        EnumSet.noneOf(RelativeMovement.class),
                        entity.getYRot(), entity.getXRot(), null
                );
            }

            entity.sendSystemMessage(TextFormatter.format("您同意了 {} 的传送请求", requester));
            requester.sendSystemMessage(TextFormatter.format("您的传送请求已被 {} 同意。你们将很快见面了", entity));
            teleportable.clearTeleportMasterRequest();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int spawn(CommandSourceStack stack, Entity entity) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            // 保存当前位置
            teleportable.setTeleportMasterLastLocation(
                    GlobalPos.of(entity.level().dimension(), entity.blockPosition()),
                    false
            );
        }

        ServerLevel overworld = stack.getServer().overworld();
        BlockPos spawnPoint = overworld.getSharedSpawnPos();
        performTeleport(
                stack, entity, overworld,
                spawnPoint.getX(), spawnPoint.getY() + 1.0D, spawnPoint.getZ(),
                EnumSet.noneOf(RelativeMovement.class),
                entity.getYRot(), entity.getXRot(), null
        );

        entity.sendSystemMessage(TextFormatter.format("已将您传送至世界出生点 {}", spawnPoint));
        return Command.SINGLE_SUCCESS;
    }

    private static int sethome(Entity entity, @Nullable String name) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            BlockPos pos = entity.getOnPos();
            GlobalPos globalPos = GlobalPos.of(entity.level().dimension(), pos);

            // 如果名称为空，使用默认名称
            if (name == null || name.isEmpty()) {
                name = String.format("%s_%d_%d_%d",
                        entity.level().dimension().location().toString().replace(":", "-"),
                        pos.getX(), pos.getY(), pos.getZ());
            }

            teleportable.setTeleportMasterHome(globalPos, name);

            entity.sendSystemMessage(TextFormatter.format(
                    "成功将位置 {} 设置为家 \"{}\"",
                    pos,
                    Component.literal(name).withStyle(ChatFormatting.GREEN)
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int home(CommandSourceStack stack, Entity entity, String name) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            GlobalPos globalPos = teleportable.getTeleportMasterHome(name);
            if (globalPos == null) {
                throw NO_HOME_WITH_NAME.create(name);
            }

            // 保存当前位置作为上一个传送点
            teleportable.setTeleportMasterLastLocation(
                    GlobalPos.of(entity.level().dimension(), entity.blockPosition()),
                    false
            );

            ServerLevel level = stack.getServer().getLevel(globalPos.dimension());
            if (level == null) {
                throw NO_LEVEL_FOUNDED_TO_HOME.create(globalPos.dimension().toString());
            }

            BlockPos pos = globalPos.pos();
            performTeleport(
                    stack, entity, level,
                    pos.getX(), pos.getY() + 1.0D, pos.getZ(),
                    EnumSet.noneOf(RelativeMovement.class),
                    entity.getYRot(), entity.getXRot(), null
            );

            entity.sendSystemMessage(TextFormatter.format(
                    "已将您传送至家 \"{}\" {}",
                    Component.literal(name).withStyle(ChatFormatting.GREEN),
                    pos
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int back(CommandSourceStack stack, Entity entity, boolean toDeath) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            GlobalPos lastPos = toDeath ? teleportable.getTeleportMasterLastDeathLocation() : teleportable.getTeleportMasterLastLocation();
            if (lastPos == null) {
                throw NO_LOCATION_TO_BACK.create();
            }

            // 保存当前位置作为新的上一个传送点
            GlobalPos currentPos = GlobalPos.of(entity.level().dimension(), entity.blockPosition());

            ServerLevel level = stack.getServer().getLevel(lastPos.dimension());
            if (level == null) {
                throw NO_LEVEL_FOUNDED_TO_BACK.create(lastPos.dimension().toString());
            }

            BlockPos pos = lastPos.pos();
            performTeleport(
                    stack, entity, level,
                    pos.getX(), pos.getY() + 1.0D, pos.getZ(),
                    EnumSet.noneOf(RelativeMovement.class),
                    entity.getYRot(), entity.getXRot(), null
            );

            // 设置新的上一个传送点
            teleportable.setTeleportMasterLastLocation(currentPos, false);

            entity.sendSystemMessage(TextFormatter.format(
                    "已将您传送至{}位置 {}",
                    toDeath ? "上一个死亡" : "上一个传送",
                    pos
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeHome(Entity entity, String name) throws CommandSyntaxException {
        if (entity instanceof ITeleportable teleportable) {
            if (teleportable.getTeleportMasterHome(name) == null) {
                throw NO_HOME_WITH_NAME.create(name);
            }
            teleportable.setTeleportMasterHome(null, name);
            entity.sendSystemMessage(TextFormatter.format(
                    "成功删除家 {}",
                    Component.literal(name).withStyle(ChatFormatting.GREEN)
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static final SuggestionProvider<CommandSourceStack> HOME_SUGGESTIONS = (context, builder) -> {
        Entity entity = context.getSource().getEntity();
        if (entity instanceof ITeleportable teleportable) {
            Map<String, GlobalPos> homes = teleportable.getTeleportMasterHomes();
            for (String homeName : homes.keySet()) {
                builder.suggest(homeName);
            }
        }
        return builder.buildFuture();
    };

    public static void performTeleport(CommandSourceStack source, Entity entity, ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeList, float yaw, float pitch, @Nullable TeleportCommand.LookAt facing) {
        TeleportCommandInvoker.callPerformTeleport(source, entity, level, x, y, z, relativeList, yaw, pitch, facing);
    }
}