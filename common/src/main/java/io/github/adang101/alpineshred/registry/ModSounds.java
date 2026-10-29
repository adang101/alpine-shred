package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sound events (design doc section 17). Ids use dots ({@code alpineshred:ride.glide.snow}); each
 * has an entry with a subtitle in {@code assets/alpineshred/sounds.json}. Grind, trick and lift
 * sounds join with their phases.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(AlpineShred.MOD_ID, Registries.SOUND_EVENT);

    private static final List<String> IDS = new ArrayList<>();

    public static final RegistrySupplier<SoundEvent> GLIDE_SNOW = sound("ride.glide.snow");
    public static final RegistrySupplier<SoundEvent> GLIDE_ICE = sound("ride.glide.ice");
    public static final RegistrySupplier<SoundEvent> GLIDE_POWDER = sound("ride.glide.powder");
    public static final RegistrySupplier<SoundEvent> SCRAPE = sound("ride.scrape");
    public static final RegistrySupplier<SoundEvent> CARVE = sound("ride.carve");
    public static final RegistrySupplier<SoundEvent> BRAKE = sound("ride.brake");
    public static final RegistrySupplier<SoundEvent> POP = sound("ride.pop");
    public static final RegistrySupplier<SoundEvent> LAND_SOFT = sound("ride.land.soft");
    public static final RegistrySupplier<SoundEvent> LAND_HARD = sound("ride.land.hard");
    public static final RegistrySupplier<SoundEvent> SPIN_WHOOSH = sound("ride.spin_whoosh");
    public static final RegistrySupplier<SoundEvent> WIPEOUT = sound("ride.wipeout");
    public static final RegistrySupplier<SoundEvent> POLE_PUSH = sound("ride.pole_push");
    public static final RegistrySupplier<SoundEvent> STRAP_IN = sound("gear.strap_in");
    public static final RegistrySupplier<SoundEvent> STRAP_OUT = sound("gear.strap_out");
    public static final RegistrySupplier<SoundEvent> WAX_APPLY = sound("gear.wax_apply");
    public static final RegistrySupplier<SoundEvent> TUNE = sound("gear.tune");

    private ModSounds() {
    }

    private static RegistrySupplier<SoundEvent> sound(String id) {
        IDS.add(id);
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(AlpineShred.id(id)));
    }

    /** Every registered id path, for the sounds.json check in tests. */
    public static List<String> ids() {
        return Collections.unmodifiableList(IDS);
    }

    public static void register() {
        SOUNDS.register();
    }
}
