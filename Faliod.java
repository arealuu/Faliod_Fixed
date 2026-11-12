package ru.faliod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@Mod(Faliod.MODID)
public final class Faliod {
    public static final String MODID = "faliod";
    public static final ResourceKey<Biome> FALIOD_BIOME = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath(MODID, "faliod"));

    public Faliod() {
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.GAME)
    public static final class Events {
        private static final String TIMER = "FaliodExposureTicks";
        private static final String EVER_EXPOSED = "FaliodEverEntered";

        private Events() {}

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            ServerLevel level = serverPlayer.serverLevel();
            boolean inside = level.getBiome(serverPlayer.blockPosition()).is(FALIOD_BIOME);
            if (!inside) {
                serverPlayer.getPersistentData().putInt(TIMER, 0);
                return;
            }

            int ticks = serverPlayer.getPersistentData().getInt(TIMER) + 1;
            serverPlayer.getPersistentData().putInt(TIMER, ticks);
            if (ticks >= 20) serverPlayer.getPersistentData().putBoolean(EVER_EXPOSED, true);

            // 3 seconds: dark particles.
            if (ticks >= 60 && ticks % 10 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, serverPlayer.getX(), serverPlayer.getY() + 1.0,
                        serverPlayer.getZ(), 12, 0.45, 0.65, 0.45, 0.015);
            }

            // 6 seconds: half a heart every second.
            if (ticks >= 120 && ticks % 20 == 0) {
                serverPlayer.hurt(level.damageSources().magic(), 1.0F);
            }

            // 7 seconds: blindness and unsettling sounds.
            if (ticks == 140) {
                serverPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 180, 0, false, false, true));
                level.playSound(null, serverPlayer.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.AMBIENT, 1.0F, 0.55F);
                level.playSound(null, serverPlayer.blockPosition(), SoundEvents.AMBIENT_CAVE,
                        SoundSource.AMBIENT, 1.0F, 0.6F);
            }

            // 15 seconds: kill() bypasses the normal damage/totem rescue path.
            if (ticks >= 300) {
                serverPlayer.kill();
            }
        }

        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (!event.getItemStack().is(Items.CHORUS_FRUIT)) return;
            Player player = event.getEntity();
            if (!player.getPersistentData().getBoolean(EVER_EXPOSED)) return;

            event.setCanceled(true);
            if (!event.getLevel().isClientSide && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.kill();
            }
        }

        @SubscribeEvent
        public static void onEntityJoin(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide || !(event.getLevel() instanceof ServerLevel level)) return;
            Entity entity = event.getEntity();
            if (!level.getBiome(entity.blockPosition()).is(FALIOD_BIOME)) return;

            if (entity instanceof ThrownEnderpearl) {
                entity.discard();
                return;
            }

            if (entity instanceof AbstractBoat) {
                entity.discard();
                int sticks = 1 + level.random.nextInt(2);
                ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(),
                        new ItemStack(Items.STICK, sticks));
                level.addFreshEntity(drop);
            }
        }
    }
}
