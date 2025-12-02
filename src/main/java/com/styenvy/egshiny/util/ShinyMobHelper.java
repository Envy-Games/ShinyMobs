package com.styenvy.egshiny.util;

import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.config.ShinyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Random;

public class ShinyMobHelper {

    private static final Random RANDOM = new Random();
    private static final String SHINY_TAG = "IsShinyMob";
    private static final String HARD_SHINY_TAG = "IsHardShinyMob";

    // Tag for mobs that are allowed to wear shiny netherite gear
    private static final TagKey<EntityType<?>> SHINY_GEAR_COMPAT_TAG =
            TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.parse(EGShiny.MODID + ":shiny_gear_compatible"));

    // Team colors for glow effect (used when profile.randomTeamColor == true)
    private static final ChatFormatting[] TEAM_COLORS = {
            ChatFormatting.DARK_BLUE,
            ChatFormatting.DARK_GREEN,
            ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED,
            ChatFormatting.DARK_PURPLE,
            ChatFormatting.GOLD,
            ChatFormatting.GRAY,
            ChatFormatting.DARK_GRAY,
            ChatFormatting.BLUE,
            ChatFormatting.GREEN,
            ChatFormatting.AQUA,
            ChatFormatting.RED,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.YELLOW,
            ChatFormatting.WHITE
    };

    public static void makeShiny(LivingEntity entity, ServerLevel level) {
        makeShiny(entity, level, false);
    }

    /**
     * Main entry point: make an entity shiny using its ShinyProfile, with optional hard mode.
     *
     * @param entity   The entity to transform into a shiny.
     * @param level    The server level.
     * @param hardMode True if the player has hard-mode shinies enabled.
     */
    public static void makeShiny(LivingEntity entity, ServerLevel level, boolean hardMode) {
        // Never affect players – no glow, no gear, nothing
        if (entity instanceof Player) {
            return;
        }

        // Don't double-apply
        if (isShiny(entity)) {
            return;
        }

        // Resolve the profile for this entity type
        ShinyProfile profile = ShinyProfileRegistry.getProfileFor(entity.getType());
        if (profile == null) {
            // This entity type is not configured for shiny behavior yet
            return;
        }

        // If this profile is hard-only, require hard mode to be enabled
        if (profile.hardShiny() && !hardMode) {
            // Respect the player's difficulty choice: skip hard-only shinies
            return;
        }

        // Mark as shiny
        CompoundTag tag = entity.getPersistentData();
        tag.putBoolean(SHINY_TAG, true);

        // Mark as hard shiny if requested and the profile is actually hard-mode
        if (hardMode && profile.hardShiny()) {
            tag.putBoolean(HARD_SHINY_TAG, true);
        }

        // Apply visual effects (glow + team)
        if (profile.useGlow()) {
            applyGlowEffect(entity, level, profile);
        }

        // Modify attributes based on profile
        modifyAttributes(entity, profile);

        // Equip with gear based on profile
        if (profile.equipNetherite()) {
            equipNetheriteGear(entity, level, profile);
        }

        // Set custom name using proper formatting
        String mobName = entity.getType().getDescription().getString();
        entity.setCustomName(
                Component.literal("★ Shiny " + mobName + " ★")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        );
        entity.setCustomNameVisible(true);

        // Prevent natural despawning if configured (global, not per-profile)
        if (!ShinyConfig.NATURAL_DESPAWN.get() && entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    public static boolean isShiny(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData();
        return tag.getBoolean(SHINY_TAG);
    }

    private static void applyGlowEffect(LivingEntity entity, ServerLevel level, ShinyProfile profile) {
        // Make entity glow
        entity.setGlowingTag(true);

        // Set team color for glow
        Scoreboard scoreboard = level.getScoreboard();
        ChatFormatting color;

        if (profile.randomTeamColor()) {
            color = TEAM_COLORS[RANDOM.nextInt(TEAM_COLORS.length)];
        } else {
            color = getColorFromProfile(profile);
        }

        // Create or get team for this color
        String teamName = "egshiny_" + color.getName();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);

        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setColor(color);
        }

        // Add entity to team (use UUID string since this isn't a player)
        scoreboard.addPlayerToTeam(entity.getStringUUID(), team);
    }

    /**
     * Resolve a ChatFormatting from the profile, falling back to GOLD if invalid.
     */
    private static ChatFormatting getColorFromProfile(ShinyProfile profile) {
        String colorName = profile.fixedTeamColorKey();
        if (colorName == null || colorName.isEmpty()) {
            return ChatFormatting.GOLD;
        }

        // ChatFormatting names are upper-case
        ChatFormatting fromConfig = ChatFormatting.getByName(colorName.toUpperCase());
        if (fromConfig != null && fromConfig.isColor()) {
            return fromConfig;
        }

        return ChatFormatting.GOLD;
    }

    private static void modifyAttributes(LivingEntity entity, ShinyProfile profile) {
        // Modify health
        double healthMultiplier;
        if (profile.useRandomHealth()) {
            double min = profile.minHealthMultiplier();
            double max = profile.maxHealthMultiplier();
            healthMultiplier = min + (max - min) * RANDOM.nextDouble();
        } else {
            healthMultiplier = profile.fixedHealthMultiplier();
        }

        var maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double baseHealth = entity.getMaxHealth();
            double newHealth = baseHealth * healthMultiplier;
            maxHealthAttr.setBaseValue(newHealth);
            entity.setHealth((float) newHealth);
        }

        // Modify damage for hostile mobs
        if (entity instanceof Monster monster) {
            var attackDamageAttr = monster.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamageAttr != null) {
                double damageMultiplier = profile.damageMultiplier();
                double baseDamage = attackDamageAttr.getBaseValue();
                attackDamageAttr.setBaseValue(baseDamage * damageMultiplier);
            }
        }

        // Increase movement speed slightly (global behavior for all shinies for now)
        var movementSpeedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeedAttr != null) {
            movementSpeedAttr.setBaseValue(movementSpeedAttr.getBaseValue() * 1.2);
        }
    }

    private static void equipNetheriteGear(LivingEntity entity, ServerLevel level, ShinyProfile profile) {
        // Only meaningful on mobs
        if (!(entity instanceof Mob mob)) {
            return;
        }

        // Only give gear to compatible mobs (driven by the shiny_gear_compatible entity type tag)
        if (!isGearCompatibleMob(mob)) {
            return;
        }

        // Create and equip armor
        ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
        ItemStack chestplate = new ItemStack(Items.NETHERITE_CHESTPLATE);
        ItemStack leggings = new ItemStack(Items.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);

        // Main-hand weapon:
        // - Ranged mobs: upgraded version of what they're already using (bow/crossbow)
        // - Others: netherite sword
        ItemStack mainHand;
        if (mob instanceof RangedAttackMob) {
            mainHand = getUpgradedRangedWeapon(mob);
        } else {
            mainHand = new ItemStack(Items.NETHERITE_SWORD);
        }

        // Apply max enchantments if configured in profile
        if (profile.maxEnchantments()) {
            applyMaxEnchantments(helmet, level, EquipmentSlot.HEAD);
            applyMaxEnchantments(chestplate, level, EquipmentSlot.CHEST);
            applyMaxEnchantments(leggings, level, EquipmentSlot.LEGS);
            applyMaxEnchantments(boots, level, EquipmentSlot.FEET);
            applyMaxEnchantments(mainHand, level, EquipmentSlot.MAINHAND);
        }

        mob.setItemSlot(EquipmentSlot.HEAD, helmet);
        mob.setItemSlot(EquipmentSlot.CHEST, chestplate);
        mob.setItemSlot(EquipmentSlot.LEGS, leggings);
        mob.setItemSlot(EquipmentSlot.FEET, boots);
        mob.setItemSlot(EquipmentSlot.MAINHAND, mainHand);

        // Drop chances only exist on Mob, not generic LivingEntity
        float dropChance = (float) profile.dropChancePerItem();

        mob.setDropChance(EquipmentSlot.HEAD, dropChance);
        mob.setDropChance(EquipmentSlot.CHEST, dropChance);
        mob.setDropChance(EquipmentSlot.LEGS, dropChance);
        mob.setDropChance(EquipmentSlot.FEET, dropChance);
        mob.setDropChance(EquipmentSlot.MAINHAND, dropChance);
    }

    private static boolean isGearCompatibleMob(Mob mob) {
        return mob.getType().is(SHINY_GEAR_COMPAT_TAG);
    }

    /**
     * For ranged mobs: give them an upgraded version of whatever ranged weapon they're using.
     * - If they already hold a bow or crossbow, keep that type (new stack, then enchanted).
     * - Otherwise, choose a sensible default based on mob type.
     */
    private static ItemStack getUpgradedRangedWeapon(Mob mob) {
        ItemStack current = mob.getMainHandItem();

        // If they already hold a bow or crossbow, preserve that type
        if (current.is(Items.BOW)) {
            return new ItemStack(Items.BOW);
        }
        if (current.is(Items.CROSSBOW)) {
            return new ItemStack(Items.CROSSBOW);
        }

        // Fallbacks:
        // - Pillagers should use crossbows
        // - Others default to bows
        if (mob.getType() == EntityType.PILLAGER) {
            return new ItemStack(Items.CROSSBOW);
        }

        return new ItemStack(Items.BOW);
    }

    private static void applyMaxEnchantments(ItemStack stack, ServerLevel level, EquipmentSlot slot) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

        switch (slot) {
            case HEAD -> {
                addEnchantment(enchantments, level, "minecraft:protection", 4);
                addEnchantment(enchantments, level, "minecraft:unbreaking", 3);
                addEnchantment(enchantments, level, "minecraft:mending", 1);
                addEnchantment(enchantments, level, "minecraft:respiration", 3);
                addEnchantment(enchantments, level, "minecraft:aqua_affinity", 1);
            }
            case CHEST, LEGS -> {
                addEnchantment(enchantments, level, "minecraft:protection", 4);
                addEnchantment(enchantments, level, "minecraft:unbreaking", 3);
                addEnchantment(enchantments, level, "minecraft:mending", 1);
                addEnchantment(enchantments, level, "minecraft:thorns", 3);
            }
            case FEET -> {
                addEnchantment(enchantments, level, "minecraft:protection", 4);
                addEnchantment(enchantments, level, "minecraft:unbreaking", 3);
                addEnchantment(enchantments, level, "minecraft:mending", 1);
                addEnchantment(enchantments, level, "minecraft:feather_falling", 4);
                addEnchantment(enchantments, level, "minecraft:depth_strider", 3);
            }
            case MAINHAND -> {
                addEnchantment(enchantments, level, "minecraft:sharpness", 5);
                addEnchantment(enchantments, level, "minecraft:unbreaking", 3);
                addEnchantment(enchantments, level, "minecraft:mending", 1);
                addEnchantment(enchantments, level, "minecraft:fire_aspect", 2);
                addEnchantment(enchantments, level, "minecraft:looting", 3);
                addEnchantment(enchantments, level, "minecraft:sweeping_edge", 3);
            }
            default -> {
                // OFFHAND or anything else: no special enchants
            }
        }

        EnchantmentHelper.setEnchantments(stack, enchantments.toImmutable());
    }

    private static void addEnchantment(ItemEnchantments.Mutable enchantments,
                                       ServerLevel level,
                                       String enchantmentId,
                                       int lvl) {
        ResourceKey<Enchantment> key =
                ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(enchantmentId));

        level.registryAccess()
                .registry(Registries.ENCHANTMENT)
                .flatMap(reg -> reg.getHolder(key))
                .ifPresent(holder -> enchantments.set(holder, lvl));
    }
}
