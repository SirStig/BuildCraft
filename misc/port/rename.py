#!/usr/bin/env python3
"""
Mechanical 1.12.2 -> 26.x renames, as a first pass when porting a file.

This is a starting point, NOT a port. It handles the renames that are purely
positional -- package moves and type renames with identical semantics. It does
NOT know about anything that changed shape: capabilities, the transfer API,
NBT on stacks, rendering, block metadata, or the packet system. Every file it
touches still has to be read, and the compiler is the real check.

Usage:  misc/port/rename.py <files...>          # rewrite in place
        misc/port/rename.py --stdout <file>     # preview one
"""
import re
import sys

# Import moves. Applied longest-first so a more specific path wins.
IMPORTS = {
    "net.minecraft.util.EnumFacing": "net.minecraft.core.Direction",
    "net.minecraft.util.math.BlockPos": "net.minecraft.core.BlockPos",
    "net.minecraft.util.math.Vec3d": "net.minecraft.world.phys.Vec3",
    "net.minecraft.util.math.Vec3i": "net.minecraft.core.Vec3i",
    "net.minecraft.util.math.AxisAlignedBB": "net.minecraft.world.phys.AABB",
    "net.minecraft.util.math.RayTraceResult": "net.minecraft.world.phys.BlockHitResult",
    "net.minecraft.util.NonNullList": "net.minecraft.core.NonNullList",
    "net.minecraft.util.ResourceLocation": "net.minecraft.resources.Identifier",
    "net.minecraft.util.IStringSerializable": "net.minecraft.util.StringRepresentable",
    "net.minecraft.util.EnumHand": "net.minecraft.world.InteractionHand",
    "net.minecraft.util.EnumActionResult": "net.minecraft.world.InteractionResult",
    "net.minecraft.util.Rotation": "net.minecraft.world.level.block.Rotation",
    "net.minecraft.world.World": "net.minecraft.world.level.Level",
    "net.minecraft.world.IBlockAccess": "net.minecraft.world.level.BlockGetter",
    "net.minecraft.world.Explosion": "net.minecraft.world.level.Explosion",
    "net.minecraft.tileentity.TileEntity": "net.minecraft.world.level.block.entity.BlockEntity",
    "net.minecraft.block.state.IBlockState": "net.minecraft.world.level.block.state.BlockState",
    "net.minecraft.block.Block": "net.minecraft.world.level.block.Block",
    "net.minecraft.item.ItemStack": "net.minecraft.world.item.ItemStack",
    "net.minecraft.item.EnumDyeColor": "net.minecraft.world.item.DyeColor",
    "net.minecraft.item.Item": "net.minecraft.world.item.Item",
    "net.minecraft.item.crafting.Ingredient": "net.minecraft.world.item.crafting.Ingredient",
    "net.minecraft.entity.player.EntityPlayer": "net.minecraft.world.entity.player.Player",
    "net.minecraft.entity.EntityLivingBase": "net.minecraft.world.entity.LivingEntity",
    "net.minecraft.entity.Entity": "net.minecraft.world.entity.Entity",
    "net.minecraft.nbt.NBTTagCompound": "net.minecraft.nbt.CompoundTag",
    "net.minecraft.nbt.NBTBase": "net.minecraft.nbt.Tag",
    "net.minecraft.nbt.NBTTagString": "net.minecraft.nbt.StringTag",
    "net.minecraft.network.PacketBuffer": "net.minecraft.network.RegistryFriendlyByteBuf",
    "net.minecraft.entity.item.EntityItem": "net.minecraft.world.entity.item.ItemEntity",
    "net.minecraftforge.fluids.FluidStack": "net.neoforged.neoforge.fluids.FluidStack",
    "net.minecraftforge.fluids.Fluid": "net.minecraft.world.level.material.Fluid",
    "net.minecraftforge.fml.common.eventhandler.Event": "net.neoforged.bus.api.Event",
    "net.minecraftforge.fml.common.eventhandler.Cancelable": "net.neoforged.bus.api.ICancellableEvent",
    "javax.annotation.Nonnull": "org.jetbrains.annotations.NotNull",
    "javax.annotation.Nullable": "org.jetbrains.annotations.Nullable",
}

# Bare type renames, applied on word boundaries.
TYPES = {
    "EnumFacing": "Direction",
    "Vec3d": "Vec3",
    "AxisAlignedBB": "AABB",
    "RayTraceResult": "BlockHitResult",
    "IBlockState": "BlockState",
    "TileEntity": "BlockEntity",
    "EnumDyeColor": "DyeColor",
    "EntityPlayer": "Player",
    "EntityLivingBase": "LivingEntity",
    "NBTTagCompound": "CompoundTag",
    "NBTTagString": "StringTag",
    "NBTBase": "Tag",
    "PacketBuffer": "RegistryFriendlyByteBuf",
    "ResourceLocation": "Identifier",
    "IStringSerializable": "StringRepresentable",
    "EnumHand": "InteractionHand",
    "EnumActionResult": "InteractionResult",
    "IBlockAccess": "BlockGetter",
    "Nonnull": "NotNull",
    "EntityItem": "ItemEntity",
    "World": "Level",
}

# Method renames with unchanged semantics.
METHODS = {
    r"\.getFrontOffsetX\(\)": ".getStepX()",
    r"\.getFrontOffsetY\(\)": ".getStepY()",
    r"\.getFrontOffsetZ\(\)": ".getStepZ()",
    r"\.getDirectionVec\(\)": ".getUnitVec3i()",
    r"\.getOffset\(\)": ".getStep()",
    r"\.getDefaultState\(\)": ".defaultBlockState()",
    r"\.getTotalWorldTime\(\)": ".getGameTime()",
    r"\.addVector\(": ".add(",
    r"\.isRemote\b": ".isClientSide()",
    # Direction.VALUES and .HORIZONTALS were public fields; both are gone.
    r"\bDirection\.VALUES\b": "Direction.values()",

    # NBT writes: setX -> putX.
    r"\.setInteger\(": ".putInt(",
    r"\.setString\(": ".putString(",
    r"\.setBoolean\(": ".putBoolean(",
    r"\.setByte\(": ".putByte(",
    r"\.setShort\(": ".putShort(",
    r"\.setLong\(": ".putLong(",
    r"\.setFloat\(": ".putFloat(",
    r"\.setDouble\(": ".putDouble(",
    r"\.setIntArray\(": ".putIntArray(",
    r"\.setByteArray\(": ".putByteArray(",
    r"\.setTag\(": ".put(",
    r"\.setUniqueId\(": ".putUUID(",
    r"\.hasKey\(": ".contains(",

    # NBT reads. On 26.x CompoundTag.getX(name) returns an Optional; getXOr(name, default)
    # returns the value. 1.12.2's getX returned a zero value for a missing key, so the "Or"
    # form with a zero default is what preserves the old behaviour -- which is usually, but
    # NOT always, what the caller wanted. Check each one.
    r"\.getInteger\((\s*[^,()]+)\)": r".getIntOr(\1, 0)",
    r"\.getBoolean\((\s*[^,()]+)\)": r".getBooleanOr(\1, false)",
    r"\.getByte\((\s*[^,()]+)\)": r".getByteOr(\1, (byte) 0)",
    r"\.getLong\((\s*[^,()]+)\)": r".getLongOr(\1, 0L)",
    r"\.getFloat\((\s*[^,()]+)\)": r".getFloatOr(\1, 0.0F)",
    r"\.getDouble\((\s*[^,()]+)\)": r".getDoubleOr(\1, 0.0D)",
    r"\.getString\((\s*[^,()]+)\)": r'.getStringOr(\1, "")',
    r"\.getShort\((\s*[^,()]+)\)": r".getShort(\1).orElse((short) 0)",
    r"\.getIntArray\((\s*[^,()]+)\)": r".getIntArray(\1).orElse(new int[0])",
    r"\.getByteArray\((\s*[^,()]+)\)": r".getByteArray(\1).orElse(new byte[0])",
    r"\.getCompoundTag\((\s*[^,()]+)\)": r".getCompound(\1).orElseGet(CompoundTag::new)",
    r"\.getTagList\((\s*[^,()]+), [^)]+\)": r".getList(\1).orElseGet(ListTag::new)",

    # Forge's NBT type constants moved onto Tag itself.
    r"\bConstants\.NBT\.": "Tag.",

    # Assorted single-method renames.
    r"\.getPos\(\)": ".getBlockPos()",
    r"\.getTag\((\s*(?:\"[^\"]*\"|\w+))\)": r".get(\1)",
    r"\bnameToResourceLocation\(": "nameToResourceId(",
}


def convert(text: str) -> str:
    # @Cancelable became "implements ICancellableEvent". Done before the import rewrite so the annotation
    # and its import are both still recognisable.
    text = re.sub(
        r"@Cancelable\s*\n(\s*)((?:public |static |final |abstract )*class \w+ extends [\w.]+)(?!\s+implements)",
        r"\1\2 implements ICancellableEvent",
        text,
    )

    for old, new in sorted(IMPORTS.items(), key=lambda kv: -len(kv[0])):
        text = text.replace(old, new)
    for old, new in TYPES.items():
        text = re.sub(rf"\b{old}\b", new, text)
    for pattern, new in METHODS.items():
        text = re.sub(pattern, new, text)
    # The variable is conventionally named "level" now that the type is. The lookbehind keeps this off
    # dotted paths -- without it, net.minecraft.world.item becomes net.minecraft.level.item. Field
    # accesses through this/super are the one dotted form that IS a rename, so they go first.
    text = re.sub(r"\b(this|super)\.world\b", r"\1.level", text)
    text = re.sub(r"(?<![.\w])world\b", "level", text)
    text = re.sub(r"(?<![.\w])oLevel\b", "oLevel", text)
    return text


def main(argv):
    if not argv:
        print(__doc__.strip())
        return 1
    to_stdout = argv[0] == "--stdout"
    paths = argv[1:] if to_stdout else argv
    for path in paths:
        with open(path) as handle:
            converted = convert(handle.read())
        if to_stdout:
            print(converted)
        else:
            with open(path, "w") as handle:
                handle.write(converted)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
