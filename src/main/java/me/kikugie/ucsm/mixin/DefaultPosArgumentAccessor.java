package me.kikugie.ucsm.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.command.argument.CoordinateArgument;
import net.minecraft.command.argument.DefaultPosArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DefaultPosArgument.class)
@Environment(EnvType.CLIENT)
public interface DefaultPosArgumentAccessor {

    @Accessor("x")
    CoordinateArgument getX();

    @Accessor("y")
    CoordinateArgument getY();

    @Accessor("z")
    CoordinateArgument getZ();

}
