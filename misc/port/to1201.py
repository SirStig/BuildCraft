#!/usr/bin/env python3
"""
Adjusts a file already ported to 26.x so it compiles on 1.20.1.

The 26.x tree is the reference: port a file there first, get it compiling, then run this to produce the
1.20.1 copy. It undoes the handful of changes that are 26.x-only, which is far fewer edits than porting
from 1.12.2 twice.

It does NOT know about the transfer API, capabilities or rendering -- those genuinely differ in shape and
the file has to be written twice. See PORTING.md's "things that differ between our two targets" table.
"""
import re
import sys

SUBS = [
    # Resource ids kept the old name on 1.20.1.
    ("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation"),
    (r"\bIdentifier\b", "ResourceLocation"),
    (r"ResourceLocation\.fromNamespaceAndPath\(([^,]+), ([^)]+)\)", r"new ResourceLocation(\1, \2)"),
    (r"ResourceLocation\.parse\(([^)]+)\)", r"new ResourceLocation(\1)"),

    # Buffers are not registry-aware.
    ("net.minecraft.network.RegistryFriendlyByteBuf", "net.minecraft.network.FriendlyByteBuf"),
    (r"\bRegistryFriendlyByteBuf\b", "FriendlyByteBuf"),

    # Loader and fluid packages.
    ("net.neoforged.fml.", "net.minecraftforge.fml."),
    ("net.neoforged.neoforge.fluids.FluidStack", "net.minecraftforge.fluids.FluidStack"),

    # CompoundTag getters still return values rather than Optionals, so the "Or" forms do not exist.
    (r"\.getIntOr\(([^,]+), 0\)", r".getInt(\1)"),
    (r"\.getBooleanOr\(([^,]+), false\)", r".getBoolean(\1)"),
    (r"\.getByteOr\(([^,]+), \(byte\) 0\)", r".getByte(\1)"),
    (r"\.getLongOr\(([^,]+), 0L\)", r".getLong(\1)"),
    (r"\.getFloatOr\(([^,]+), 0\.0F\)", r".getFloat(\1)"),
    (r"\.getDoubleOr\(([^,]+), 0\.0D\)", r".getDouble(\1)"),
    (r'\.getStringOr\(([^,]+), ""\)', r".getString(\1)"),
    (r"\.getShort\(([^)]+)\)\.orElse\(\(short\) 0\)", r".getShort(\1)"),
    (r"\.getIntArray\(([^)]+)\)\.orElse\(new int\[0\]\)", r".getIntArray(\1)"),
    (r"\.getByteArray\(([^)]+)\)\.orElse\(new byte\[0\]\)", r".getByteArray(\1)"),
    (r"\.getCompound\(([^)]+)\)\.orElseGet\(CompoundTag::new\)", r".getCompound(\1)"),
    (r"\.getList\(([^)]+)\)\.orElseGet\(ListTag::new\)", r".getList(\1, Tag.TAG_COMPOUND)"),
]


def convert(text: str) -> str:
    for pattern, replacement in SUBS:
        text = re.sub(pattern, replacement, text) if pattern.startswith("\\") or "(" in pattern \
            else text.replace(pattern, replacement)
    return text


def main(argv):
    if not argv:
        print(__doc__.strip())
        return 1
    for path in argv:
        with open(path) as handle:
            converted = convert(handle.read())
        with open(path, "w") as handle:
            handle.write(converted)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
