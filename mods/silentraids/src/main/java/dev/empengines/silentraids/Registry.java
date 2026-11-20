package dev.empengines.silentraids;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.empengines.silentraids.gear.SigilSocketRecipe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Registry {
    /** A Silent Gear item's level and the XP it has towards the next one. */
    public record GearLevel(int level, int xp) {
        public static final GearLevel NONE = new GearLevel(0, 0);
        public static final Codec<GearLevel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("level").forGetter(GearLevel::level),
                Codec.INT.fieldOf("xp").forGetter(GearLevel::xp)).apply(i, GearLevel::new));
        public static final StreamCodec<ByteBuf, GearLevel> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GearLevel::level, ByteBufCodecs.VAR_INT, GearLevel::xp, GearLevel::new);
    }

    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SilentRaids.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, SilentRaids.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GearLevel>> GEAR_LEVEL =
            COMPONENTS.registerComponentType("gear_level", b -> b.persistent(GearLevel.CODEC).networkSynchronized(GearLevel.STREAM_CODEC));
    /** Set once a Sigil of Socketing has been used on the item (one sigil socket per item). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SIGIL_USED =
            COMPONENTS.registerComponentType("sigil_used", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SigilSocketRecipe>> SIGIL_SOCKET =
            SERIALIZERS.register("sigil_socket", () -> SigilSocketRecipe.Serializer.INSTANCE);

    static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        SERIALIZERS.register(modBus);
    }

    private Registry() {}
}
