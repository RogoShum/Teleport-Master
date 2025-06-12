package com.hexagram2021.tpmaster.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

import java.util.regex.Pattern;

public class TextFormatter {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\}");

    public static MutableComponent format(String template, Object... args) {
        MutableComponent result = Component.empty();
        String[] parts = PLACEHOLDER_PATTERN.split(template, -1);
        int argIndex = 0;

        for (int i = 0; i < parts.length; i++) {
            result.append(parts[i]);
            if (i < parts.length - 1 && argIndex < args.length) {
                Object arg = args[argIndex++];
                if (arg instanceof Component) {
                    result.append((Component) arg);
                } else {
                    result.append(formatArg(arg));
                }
            }
        }
        return result;
    }

    private static MutableComponent formatArg(Object arg) {
        if (arg == null) {
            return Component.literal("null").withStyle(ChatFormatting.RED);
        }

        if (arg instanceof Number) {
            return Component.literal(String.valueOf(arg)).withStyle(ChatFormatting.GOLD);
        }

        if (arg instanceof BlockPos pos) {
            return Component.literal(String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()))
                    .withStyle(ChatFormatting.AQUA);
        }

        if (arg instanceof Entity entity) {
            return Component.literal(entity.getName().getString())
                    .withStyle(ChatFormatting.GREEN);
        }

        return Component.literal(String.valueOf(arg));
    }
}