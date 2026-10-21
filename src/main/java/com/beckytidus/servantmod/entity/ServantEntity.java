package com.beckytidus.servantmod.entity;

import com.beckytidus.servantmod.registry.ServantNames;
import com.beckytidus.servantmod.registry.ServantSkins;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A servant: a hired worker, not a companion. It takes one order at a time
 * (its {@link Duty}) and carries it out for real - mining ore, felling trees,
 * harvesting crops, collecting drops and stowing everything in a chest.
 * It has a 9-slot pack, an energy reserve that drains as it works, and it
 * eats from its own pack to keep going.
 */
public class ServantEntity extends PathAwareEntity {
    public static final int PACK_SIZE = 9;

    private static final int REACH = 3;
    private static final int WORK_INTERVAL = 10;
    private static final int SCAN_COOLDOWN = 15;
    private static final int REPATH_INTERVAL = 8;
    private static final double TELEPORT_DISTANCE = 48.0;
    private static final double MAX_ENERGY = 100.0;

    private static final int MINE_RADIUS = 10;
    private static final int CHOP_RADIUS = 14;
    private static final int FARM_RADIUS = 10;
    private static final int HAUL_RADIUS = 14;
    private static final int DEPOSIT_RADIUS = 18;

    private static final TrackedData<String> DUTY = DataTracker.registerData(ServantEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> TEXTURE_VARIANT = DataTracker.registerData(ServantEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> ENERGY = DataTracker.registerData(ServantEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> WORK_DONE = DataTracker.registerData(ServantEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WORKING = DataTracker.registerData(ServantEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private Duty duty = Duty.FOLLOW;
    private int energy = (int) MAX_ENERGY;
    private int workDone = 0;
    private String servantName = "";
    private String textureVariant = "";

    @Nullable
    private LazyEntityReference<LivingEntity> ownerRef;
    @Nullable
    private BlockPos workTarget;
    @Nullable
    private BlockPos guardPos;

    private int workCooldown = 0;
    private int scanCooldown = 0;
    private int repathCooldown = 0;
    private long lastSpeakTick = 0;
    private boolean working = false;

    private final SimpleInventory pack = new SimpleInventory(PACK_SIZE);

    public ServantEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.setCanPickUpLoot(true);
    }

    public static DefaultAttributeContainer.Builder createServantAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 24.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(3, new LookAroundGoal(this));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.6));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, HostileEntity.class, true));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(DUTY, Duty.FOLLOW.name());
        builder.add(TEXTURE_VARIANT, "1");
        builder.add(ENERGY, (int) MAX_ENERGY);
        builder.add(WORK_DONE, 0);
        builder.add(WORKING, false);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        if (ownerRef != null) {
            view.put("Owner", Uuids.INT_STREAM_CODEC, ownerRef.getUuid());
        }
        view.putString("Duty", duty.name());
        view.putString("ServantName", servantName);
        view.putString("TextureVariant", textureVariant);
        view.putInt("Energy", energy);
        view.putInt("WorkDone", workDone);
        if (guardPos != null) {
            view.putInt("GuardX", guardPos.getX());
            view.putInt("GuardY", guardPos.getY());
            view.putInt("GuardZ", guardPos.getZ());
        }
        List<ItemStack> items = new ArrayList<>(PACK_SIZE);
        for (int i = 0; i < pack.size(); i++) items.add(pack.getStack(i));
        view.put("Pack", ItemStack.OPTIONAL_CODEC.listOf(), items);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        Optional<UUID> ownerUuid = view.read("Owner", Uuids.INT_STREAM_CODEC);
        ownerUuid.ifPresent(uuid -> ownerRef = LazyEntityReference.ofUUID(uuid));
        duty = Duty.byName(view.getString("Duty", Duty.FOLLOW.name()));
        servantName = view.getString("ServantName", "");
        textureVariant = view.getString("TextureVariant", "");
        energy = view.getInt("Energy", (int) MAX_ENERGY);
        workDone = view.getInt("WorkDone", 0);
        if (view.getInt("GuardX", Integer.MIN_VALUE) != Integer.MIN_VALUE) {
            guardPos = new BlockPos(view.getInt("GuardX", 0), view.getInt("GuardY", 0), view.getInt("GuardZ", 0));
        }
        Optional<List<ItemStack>> stored = view.read("Pack", ItemStack.OPTIONAL_CODEC.listOf());
        stored.ifPresent(list -> {
            for (int i = 0; i < pack.size() && i < list.size(); i++) {
                pack.setStack(i, list.get(i));
            }
        });
        if (!getEntityWorld().isClient()) {
            syncTracked();
        }
    }

    private void syncTracked() {
        getDataTracker().set(DUTY, duty.name());
        getDataTracker().set(TEXTURE_VARIANT, textureVariant);
        getDataTracker().set(ENERGY, energy);
        getDataTracker().set(WORK_DONE, workDone);
        getDataTracker().set(WORKING, working);
    }

    // ---------------------------------------------------------------- accessors

    @Nullable
    public PlayerEntity getOwner() {
        if (ownerRef == null) return null;
        LivingEntity e = ownerRef.getEntityByClass(this.getEntityWorld(), LivingEntity.class);
        return e instanceof PlayerEntity pe ? pe : null;
    }

    public void setOwner(PlayerEntity player) {
        this.ownerRef = player != null ? LazyEntityReference.of(player) : null;
        if (player != null) setPersistent();
    }

    public boolean isOwnedBy(PlayerEntity player) {
        return getOwner() == player;
    }

    public Duty getDuty() {
        return getEntityWorld().isClient() ? Duty.byName(getDataTracker().get(DUTY)) : duty;
    }

    public void setDuty(Duty next) {
        setDuty(next, true);
    }

    public void setDuty(Duty next, boolean announce) {
        this.duty = next;
        this.workTarget = null;
        this.workCooldown = 0;
        if (next == Duty.GUARD) this.guardPos = getBlockPos();
        if (getEntityWorld() != null && !getEntityWorld().isClient()) {
            getDataTracker().set(DUTY, next.name());
            if (announce) {
                PlayerEntity owner = getOwner();
                if (owner != null) {
                    owner.sendMessage(Text.literal(prefix() + " " + next.label() + " - " + ServantSpeech.acknowledge(getEntityWorld().getRandom())), false);
                }
            }
        }
    }

    public int getEnergy() {
        return getEntityWorld().isClient() ? getDataTracker().get(ENERGY) : energy;
    }

    public void setEnergy(int value) {
        this.energy = Math.max(0, Math.min((int) MAX_ENERGY, value));
        if (getEntityWorld() != null && !getEntityWorld().isClient()) getDataTracker().set(ENERGY, energy);
    }

    public int getWorkDone() {
        return getEntityWorld().isClient() ? getDataTracker().get(WORK_DONE) : workDone;
    }

    public boolean isWorking() {
        return getEntityWorld().isClient() ? getDataTracker().get(WORKING) : working;
    }

    private void setWorking(boolean value) {
        if (this.working == value) return;
        this.working = value;
        if (getEntityWorld() != null && !getEntityWorld().isClient()) getDataTracker().set(WORKING, value);
    }

    public String getServantName() {
        if (getEntityWorld().isClient()) {
            return getCustomName() != null ? getCustomName().getString() : "Servant";
        }
        return servantName != null && !servantName.isEmpty() ? servantName : "Servant";
    }

    public void setServantName(String name) {
        this.servantName = name != null ? name : "";
        updateNameTag();
    }

    public String getTextureVariant() {
        return getEntityWorld().isClient() ? getDataTracker().get(TEXTURE_VARIANT) : textureVariant;
    }

    public void setTextureVariant(String variant) {
        this.textureVariant = variant != null ? variant : "";
        if (getEntityWorld() != null && !getEntityWorld().isClient()) getDataTracker().set(TEXTURE_VARIANT, this.textureVariant);
    }

    public Inventory getPack() {
        return pack;
    }

    public boolean isPackFull() {
        for (int i = 0; i < pack.size(); i++) if (pack.getStack(i).isEmpty()) return false;
        return true;
    }

    public int countPackItems() {
        int n = 0;
        for (int i = 0; i < pack.size(); i++) if (!pack.getStack(i).isEmpty()) n += pack.getStack(i).getCount();
        return n;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        if (!getEntityWorld().isClient()) {
            PlayerEntity owner = getOwner();
            if (owner != null) setPersistent();
            if (owner != null && duty != Duty.STAY) {
                double distSq = owner.squaredDistanceTo(this);
                if (distSq > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
                    refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), getYaw(), getPitch());
                }
            }
        }
        super.tick();
        if (getEntityWorld().isClient()) return;

        if (servantName.isEmpty()) setServantName(ServantNames.pick(getEntityWorld().getRandom()));
        if (textureVariant.isEmpty()) setTextureVariant(ServantSkins.pick(getEntityWorld().getRandom()));

        if (workCooldown > 0) workCooldown--;
        if (scanCooldown > 0) scanCooldown--;
        if (repathCooldown > 0) repathCooldown--;

        if (energy < MAX_ENERGY && !duty.isWork() && getEntityWorld().getRandom().nextInt(40) == 0) {
            setEnergy(energy + 1);
        }

        tickDuty();
        updateNameTag();
    }

    private void tickDuty() {
        PlayerEntity owner = getOwner();
        if (owner == null) {
            getNavigation().stop();
            setWorking(false);
            return;
        }
        if (energy <= 0 && duty.isWork() && !tryEat()) {
            setWorking(false);
            getNavigation().stop();
            if (now() - lastSpeakTick > 300 && getEntityWorld().getRandom().nextFloat() < 0.05f) {
                speak(ServantSpeech.tired(getEntityWorld().getRandom()));
            }
            return;
        }

        switch (duty) {
            case FOLLOW -> { followOwner(owner); setWorking(false); }
            case STAY -> { getNavigation().stop(); setSprinting(false); setWorking(false); }
            case GUARD -> { guard(owner); }
            case MINE -> mine();
            case CHOP -> chop();
            case FARM -> farm();
            case HAUL -> haul();
            case DEPOSIT -> deposit();
        }
    }

    private void followOwner(PlayerEntity owner) {
        double distSq = squaredDistanceTo(owner);
        if (distSq > 4 * 4) {
            if (repathCooldown <= 0) {
                getNavigation().startMovingTo(owner, distSq > 12 * 12 ? 1.3 : 1.0);
                setSprinting(distSq > 12 * 12);
                repathCooldown = REPATH_INTERVAL;
            }
        } else {
            getNavigation().stop();
            setSprinting(false);
        }
        getLookControl().lookAt(owner, 10.0F, getMaxHeadRotation());
    }

    private void guard(PlayerEntity owner) {
        if (guardPos == null) guardPos = getBlockPos();
        if (getTarget() != null && getTarget().isAlive()) {
            setWorking(true);
            return;
        }
        setWorking(false);
        if (squaredDistanceTo(guardPos.getX() + 0.5, guardPos.getY(), guardPos.getZ() + 0.5) > 5 * 5) {
            if (repathCooldown <= 0) {
                getNavigation().startMovingTo(guardPos.getX() + 0.5, guardPos.getY(), guardPos.getZ() + 0.5, 1.0);
                repathCooldown = REPATH_INTERVAL;
            }
        } else {
            getNavigation().stop();
            getLookControl().lookAt(owner, 8.0F, getMaxHeadRotation());
        }
    }

    // ---------------------------------------------------------------- work duties

    private void mine() {
        if (isPackFull()) { warnFull(); return; }
        Predicate<BlockState> ores = state ->
                state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.IRON_ORES)
                || state.isIn(BlockTags.COPPER_ORES) || state.isIn(BlockTags.GOLD_ORES)
                || state.isIn(BlockTags.DIAMOND_ORES) || state.isIn(BlockTags.LAPIS_ORES)
                || state.isIn(BlockTags.REDSTONE_ORES) || state.isIn(BlockTags.EMERALD_ORES);
        Predicate<BlockState> stone = state -> state.isIn(BlockTags.BASE_STONE_OVERWORLD);
        workOn(ores, MINE_RADIUS, stone);
    }

    private void chop() {
        if (isPackFull()) { warnFull(); return; }
        workOn(state -> state.isIn(BlockTags.LOGS), CHOP_RADIUS, null);
    }

    private void farm() {
        if (isPackFull()) { warnFull(); return; }
        // 1. harvest a mature crop
        BlockPos crop = findBlock(state -> state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMature(state), FARM_RADIUS);
        if (crop != null) {
            moveToOrHarvest(crop, true);
            return;
        }
        // 2. otherwise sow a seed on bare farmland
        if (hasSeeds()) {
            BlockPos farmland = findBlock(state -> state.isOf(Blocks.FARMLAND), FARM_RADIUS, pos ->
                    getEntityWorld().getBlockState(pos.up()).isAir() && hasSeeds());
            if (farmland != null) {
                moveToOrPlant(farmland);
                return;
            }
        }
        setWorking(false);
        getNavigation().stop();
    }

    private void haul() {
        if (isPackFull()) { warnFull(); return; }
        setWorking(true);
        List<ItemEntity> items = getEntityWorld().getEntitiesByClass(ItemEntity.class,
                getBoundingBox().expand(HAUL_RADIUS), e -> e.isAlive() && !e.getStack().isEmpty());
        ItemEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            double d = squaredDistanceTo(item);
            if (d < best) { best = d; nearest = item; }
        }
        if (nearest == null) {
            setWorking(false);
            getNavigation().stop();
            return;
        }
        if (best <= REACH * REACH) {
            getNavigation().stop();
            if (workCooldown <= 0) {
                ItemStack leftover = addToPack(nearest.getStack().copy());
                if (leftover.isEmpty()) {
                    nearest.discard();
                    workDone++;
                    getDataTracker().set(WORK_DONE, workDone);
                } else {
                    nearest.setStack(leftover);
                }
                workCooldown = WORK_INTERVAL;
            }
        } else if (repathCooldown <= 0) {
            getNavigation().startMovingTo(nearest, 1.1);
            repathCooldown = REPATH_INTERVAL;
        }
    }

    private void deposit() {
        if (countPackItems() == 0) {
            setWorking(false);
            if (now() - lastSpeakTick > 400 && getEntityWorld().getRandom().nextFloat() < 0.05f) {
                speak(ServantSpeech.nothing(getEntityWorld().getRandom()));
            }
            getNavigation().stop();
            return;
        }
        BlockPos chest = findStorage(DEPOSIT_RADIUS);
        if (chest == null) {
            setWorking(false);
            getNavigation().stop();
            return;
        }
        setWorking(true);
        double distSq = squaredDistanceTo(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
        if (distSq <= REACH * REACH) {
            getNavigation().stop();
            if (workCooldown <= 0) {
                if (storeIntoChest(chest)) {
                    speak(ServantSpeech.depositDone(getEntityWorld().getRandom()));
                }
                workCooldown = WORK_INTERVAL * 2;
            }
        } else if (repathCooldown <= 0) {
            getNavigation().startMovingTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.1);
            repathCooldown = REPATH_INTERVAL;
        }
    }

    /** Generic "walk to the target block then break it" used by mine and chop. */
    private void workOn(Predicate<BlockState> primary, int radius, @Nullable Predicate<BlockState> fallback) {
        setWorking(true);
        World world = getEntityWorld();
        if (workTarget == null || !primary.test(world.getBlockState(workTarget)) && (fallback == null || !fallback.test(world.getBlockState(workTarget)))) {
            workTarget = null;
            if (scanCooldown <= 0) {
                BlockPos found = findBlock(primary, radius);
                if (found == null && fallback != null) found = findBlock(fallback, radius);
                workTarget = found;
                scanCooldown = SCAN_COOLDOWN;
            }
        }
        if (workTarget == null) {
            setWorking(false);
            getNavigation().stop();
            return;
        }
        double distSq = squaredDistanceTo(workTarget.getX() + 0.5, workTarget.getY() + 0.5, workTarget.getZ() + 0.5);
        if (distSq <= REACH * REACH) {
            getNavigation().stop();
            if (workCooldown <= 0) {
                breakBlockAt(workTarget);
                workTarget = null;
                workCooldown = WORK_INTERVAL;
            }
        } else if (repathCooldown <= 0) {
            getNavigation().startMovingTo(workTarget.getX() + 0.5, workTarget.getY(), workTarget.getZ() + 0.5, 1.1);
            repathCooldown = REPATH_INTERVAL;
        }
    }

    private void moveToOrHarvest(BlockPos pos, boolean replant) {
        setWorking(true);
        double distSq = squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distSq <= REACH * REACH) {
            getNavigation().stop();
            if (workCooldown <= 0) {
                harvestCrop(pos, replant);
                workCooldown = WORK_INTERVAL;
            }
        } else if (repathCooldown <= 0) {
            getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.1);
            repathCooldown = REPATH_INTERVAL;
        }
    }

    private void moveToOrPlant(BlockPos farmland) {
        setWorking(true);
        double distSq = squaredDistanceTo(farmland.getX() + 0.5, farmland.getY() + 0.5, farmland.getZ() + 0.5);
        if (distSq <= REACH * REACH) {
            getNavigation().stop();
            if (workCooldown <= 0) {
                plantSeed(farmland.up());
                workCooldown = WORK_INTERVAL;
            }
        } else if (repathCooldown <= 0) {
            getNavigation().startMovingTo(farmland.getX() + 0.5, farmland.getY() + 1, farmland.getZ() + 0.5, 1.1);
            repathCooldown = REPATH_INTERVAL;
        }
    }

    // ---------------------------------------------------------------- block actions

    private void breakBlockAt(BlockPos pos) {
        if (!(getEntityWorld() instanceof ServerWorld sw)) return;
        BlockState state = sw.getBlockState(pos);
        if (state.isAir()) return;
        BlockEntity be = sw.getBlockEntity(pos);
        ItemStack tool = getToolStack();
        List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, be, this, tool);
        var sound = state.getSoundGroup().getBreakSound();
        sw.breakBlock(pos, false, this);
        for (ItemStack drop : drops) {
            ItemStack leftover = addToPack(drop);
            if (!leftover.isEmpty()) dropStack(sw, leftover);
        }
        sw.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundCategory.BLOCKS, 0.7f, 0.9f + getEntityWorld().getRandom().nextFloat() * 0.2f);
        sw.spawnParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.0);
        useEnergy(3);
        bumpWorkDone();
    }

    private void harvestCrop(BlockPos pos, boolean replant) {
        if (!(getEntityWorld() instanceof ServerWorld sw)) return;
        BlockState state = sw.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) return;
        BlockEntity be = sw.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, be, this, getToolStack());
        for (ItemStack drop : drops) {
            ItemStack leftover = addToPack(drop);
            if (!leftover.isEmpty()) dropStack(sw, leftover);
        }
        if (replant) {
            sw.setBlockState(pos, crop.getDefaultState(), Block.NOTIFY_LISTENERS);
        } else {
            sw.removeBlock(pos, false);
        }
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
        useEnergy(2);
        bumpWorkDone();
    }

    private void plantSeed(BlockPos pos) {
        if (!(getEntityWorld() instanceof ServerWorld sw)) return;
        BlockState above = sw.getBlockState(pos);
        if (!above.isAir()) return;
        BlockState crop = takeSeedCrop();
        if (crop == null) return;
        sw.setBlockState(pos, crop, Block.NOTIFY_LISTENERS);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
        useEnergy(1);
        bumpWorkDone();
    }

    @Nullable
    private BlockState takeSeedCrop() {
        for (int i = 0; i < pack.size(); i++) {
            ItemStack stack = pack.getStack(i);
            if (stack.isEmpty()) continue;
            BlockState crop = null;
            if (stack.isOf(Items.WHEAT_SEEDS)) crop = Blocks.WHEAT.getDefaultState();
            else if (stack.isOf(Items.CARROT)) crop = Blocks.CARROTS.getDefaultState();
            else if (stack.isOf(Items.POTATO)) crop = Blocks.POTATOES.getDefaultState();
            else if (stack.isOf(Items.BEETROOT_SEEDS)) crop = Blocks.BEETROOTS.getDefaultState();
            if (crop != null) {
                stack.decrement(1);
                return crop;
            }
        }
        return null;
    }

    private boolean hasSeeds() {
        for (int i = 0; i < pack.size(); i++) {
            ItemStack stack = pack.getStack(i);
            if (stack.isOf(Items.WHEAT_SEEDS) || stack.isOf(Items.CARROT)
                    || stack.isOf(Items.POTATO) || stack.isOf(Items.BEETROOT_SEEDS)) return true;
        }
        return false;
    }

    private boolean isStorageBlock(BlockPos pos) {
        World world = getEntityWorld();
        BlockEntity be = world.getBlockEntity(pos);
        return be instanceof Inventory;
    }

    /** Nearest block with an inventory (chest, barrel, ...). */
    @Nullable
    private BlockPos findStorage(int radius) {
        World world = getEntityWorld();
        BlockPos center = getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int y = -4; y <= 4; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (world.isOutOfHeightLimit(mutable)) continue;
                    if (!isStorageBlock(mutable)) continue;
                    double d = squaredDistanceTo(mutable.getX() + 0.5, mutable.getY() + 0.5, mutable.getZ() + 0.5);
                    if (d < bestDist) {
                        bestDist = d;
                        best = mutable.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean storeIntoChest(BlockPos pos) {
        if (!(getEntityWorld() instanceof ServerWorld sw)) return false;
        BlockEntity be = sw.getBlockEntity(pos);
        if (!(be instanceof Inventory chest)) return false;
        boolean moved = false;
        for (int i = 0; i < pack.size(); i++) {
            ItemStack stack = pack.getStack(i);
            if (stack.isEmpty()) continue;
            ItemStack leftover = insertInto(chest, stack);
            if (leftover.getCount() != stack.getCount()) moved = true;
            pack.setStack(i, leftover);
        }
        if (moved) {
            chest.markDirty();
        }
        return moved;
    }

    private static ItemStack insertInto(Inventory target, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < target.size() && !remaining.isEmpty(); i++) {
            ItemStack slot = target.getStack(i);
            if (slot.isEmpty()) {
                target.setStack(i, remaining.copy());
                remaining.setCount(0);
            } else if (ItemStack.areItemsAndComponentsEqual(slot, remaining) && slot.getCount() < slot.getMaxCount()) {
                int move = Math.min(slot.getMaxCount() - slot.getCount(), remaining.getCount());
                slot.increment(move);
                remaining.decrement(move);
            }
        }
        return remaining;
    }

    // ---------------------------------------------------------------- pack helpers

    public ItemStack addToPack(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < pack.size() && !remaining.isEmpty(); i++) {
            ItemStack slot = pack.getStack(i);
            if (slot.isEmpty()) {
                pack.setStack(i, remaining.copy());
                remaining.setCount(0);
            } else if (ItemStack.areItemsAndComponentsEqual(slot, remaining) && slot.getCount() < slot.getMaxCount()) {
                int move = Math.min(slot.getMaxCount() - slot.getCount(), remaining.getCount());
                slot.increment(move);
                remaining.decrement(move);
            }
        }
        return remaining;
    }

    private boolean tryEat() {
        for (int i = 0; i < pack.size(); i++) {
            ItemStack stack = pack.getStack(i);
            if (stack.isEmpty() || !isFood(stack)) continue;
            stack.decrement(1);
            setEnergy(energy + 35);
            setHealth(Math.min(getHealth() + 4.0f, getMaxHealth()));
            getEntityWorld().playSound(null, getX(), getY(), getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.7f, 1.0f);
            speak("That hit the spot.");
            return true;
        }
        return false;
    }

    private static boolean isFood(ItemStack stack) {
        return stack.isOf(Items.BREAD) || stack.isOf(Items.APPLE) || stack.isOf(Items.GOLDEN_APPLE)
                || stack.isOf(Items.CARROT) || stack.isOf(Items.POTATO) || stack.isOf(Items.BAKED_POTATO)
                || stack.isOf(Items.COOKED_BEEF) || stack.isOf(Items.COOKED_PORKCHOP)
                || stack.isOf(Items.COOKED_CHICKEN) || stack.isOf(Items.COOKED_MUTTON)
                || stack.isOf(Items.MELON_SLICE) || stack.isOf(Items.SWEET_BERRIES)
                || stack.isOf(Items.COOKED_COD) || stack.isOf(Items.COOKED_SALMON)
                || stack.isOf(Items.PUMPKIN_PIE) || stack.isOf(Items.COOKIE);
    }

    public boolean isFoodItem(ItemStack stack) {
        return isFood(stack);
    }

    private ItemStack getToolStack() {
        ItemStack main = getMainHandStack();
        if (!main.isEmpty()) return main;
        for (int i = 0; i < pack.size(); i++) {
            ItemStack stack = pack.getStack(i);
            if (stack.isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)
                    || stack.isIn(net.minecraft.registry.tag.ItemTags.AXES)
                    || stack.isIn(net.minecraft.registry.tag.ItemTags.SHOVELS)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private void useEnergy(int amount) {
        setEnergy(energy - amount);
    }

    private void bumpWorkDone() {
        workDone++;
        getDataTracker().set(WORK_DONE, workDone);
    }

    private void warnFull() {
        setWorking(false);
        getNavigation().stop();
        if (now() - lastSpeakTick > 300 && getEntityWorld().getRandom().nextFloat() < 0.1f) {
            speak(ServantSpeech.full(getEntityWorld().getRandom()));
        }
    }

    // ---------------------------------------------------------------- scanning

    @Nullable
    private BlockPos findBlock(Predicate<BlockState> predicate, int radius) {
        return findBlock(predicate, radius, null);
    }

    @Nullable
    private BlockPos findBlock(Predicate<BlockState> predicate, int radius, @Nullable Predicate<BlockPos> extra) {
        World world = getEntityWorld();
        BlockPos center = getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int y = -5; y <= 5; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (world.isOutOfHeightLimit(mutable)) continue;
                    BlockState state = world.getBlockState(mutable);
                    if (!predicate.test(state)) continue;
                    if (extra != null && !extra.test(mutable)) continue;
                    double d = squaredDistanceTo(mutable.getX() + 0.5, mutable.getY() + 0.5, mutable.getZ() + 0.5);
                    if (d < bestDist) {
                        bestDist = d;
                        best = mutable.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- misc

    public void speak(String message) {
        PlayerEntity owner = getOwner();
        if (owner == null) return;
        lastSpeakTick = now();
        owner.sendMessage(Text.literal(prefix() + " " + message), false);
    }

    private String prefix() {
        return "\u2692 " + getServantName() + ":";
    }

    public Text getStatusText() {
        return Text.literal("\u2692 " + getServantName() + "  \u2022 " + getDuty().label()
                + "  \u2022 Energy " + getEnergy() + "/" + (int) MAX_ENERGY
                + "  \u2022 Pack " + countPackItems() + "  \u2022 Done " + getWorkDone());
    }

    /** Short label for the floating name tag. */
    public String getShortStatus() {
        return getDuty().label();
    }

    public String getStatusString() {
        return getStatusText().getString();
    }

    private void updateNameTag() {
        this.setCustomName(Text.literal(getServantName()));
    }

    private long now() {
        return getEntityWorld() instanceof ServerWorld sw ? sw.getLevelProperties().getTime() : 0L;
    }

    @Override
    public void onEquipStack(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack) {
        super.onEquipStack(slot, oldStack, newStack);
        if (getEntityWorld().isClient()) return;
        if (slot == EquipmentSlot.MAINHAND && oldStack.isEmpty() && !newStack.isEmpty()) {
            speak("I'll put this to good use.");
        }
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        PlayerEntity owner = getOwner();
        if (owner != null && source.getAttacker() == owner) {
            // staff do not fight their employer
            return false;
        }
        if (source.getAttacker() instanceof LivingEntity attacker && owner != null) {
            setTarget(attacker);
        }
        boolean result = super.damage(world, source, amount);
        if (this.getHealth() <= 0 && owner != null) {
            owner.sendMessage(Text.literal(prefix() + " has fallen in your service..."), false);
        }
        return result;
    }

    @Override
    protected void updatePostDeath() {
        super.updatePostDeath();
        if (this.deathTime >= 20 && !this.getEntityWorld().isClient() && !this.isRemoved()) {
            this.discard();
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return getOwner() == null && !isPersistent();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canPickupItem(ItemStack stack) {
        return false;
    }

    public Box workBox(double radius) {
        return getBoundingBox().expand(radius);
    }
}
