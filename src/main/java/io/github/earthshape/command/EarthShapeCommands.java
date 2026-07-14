package io.github.earthshape.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.earthshape.map.EarthMapService;
import io.github.earthshape.map.EarthSignal;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class EarthShapeCommands {
    private EarthShapeCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("earthshape").requires(s -> s.hasPermission(2))
                .then(Commands.literal("sample").executes(c -> {
                    CommandSourceStack source = c.getSource();
                    var pos = source.getPosition();
                    EarthSignal signal = EarthMapService.INSTANCE.sample(source.getLevel().getSeed(), (int) Math.floor(pos.x), (int) Math.floor(pos.z));
                    BlockPos blockPos = BlockPos.containing(pos);
                    String biome = source.getLevel().getBiome(blockPos).unwrapKey()
                            .map(key -> key.location().toString()).orElse("unregistered");
                    source.sendSuccess(() -> Component.literal(String.format(
                            "EarthShape: biome=%s, distance=%.1f, land=%.3f, continentalness=%.3f",
                            biome, signal.signedDistanceBlocks(), signal.landFactor(), signal.continentalness())), false);
                    return 1;
                })));
    }
}
