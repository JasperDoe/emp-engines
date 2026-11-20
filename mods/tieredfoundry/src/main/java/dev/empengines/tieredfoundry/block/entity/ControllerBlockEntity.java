package dev.empengines.tieredfoundry.block.entity;

import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import dev.empengines.tieredfoundry.Config;
import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.block.ControllerBlock;
import dev.empengines.tieredfoundry.block.FoundryProps;
import dev.empengines.tieredfoundry.block.PartBlock;
import dev.empengines.tieredfoundry.menu.ControllerMenu;
import dev.empengines.tieredfoundry.recipe.AlloyRecipe;
import dev.empengines.tieredfoundry.recipe.MeltingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The foundry's brain: checks the structure, burns fuel, keeps the heat, melts items and mixes alloys.
 *
 * <p>Structure: the controller sits in a wall facing out. Behind it is an interior of air, 1-7 by 1-7 and up to 8 tall.
 * The floor under the interior is bricks, heaters and air ducts; every wall ring (corners included) is bricks, drains
 * or this controller. All bricks must be the same kind: vanilla bricks make tier 1, fire bricks tier 2. A running
 * Create fan blowing into an air duct lifts a tier 1 foundry to 1.5 and allows alloying at either tier.
 */
public class ControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_SLOTS = 63;
    public static final int FUEL_SLOTS = 4;
    public static final int MAX_SIDE = 7;
    public static final int MAX_HEIGHT = 8;
    public static final int AMBIENT = 20;
    private static final int LAVA_PER_HEATER = 4000;

    // ---- inventories ---------------------------------------------------------------------------------------------
    private final ItemStackHandler input = new ItemStackHandler(MAX_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return formed && slot < slotCount && melting(stack) != null; }
        @Override protected void onContentsChanged(int slot) { progress[slot] = 0; cached[slot] = null; markDirty(); }
    };
    private final ItemStackHandler fuel = new ItemStackHandler(FUEL_SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.getBurnTime(RecipeType.SMELTING) > 0; }
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };
    private final FluidTank lava = new FluidTank(LAVA_PER_HEATER, fs -> fs.is(FluidTags.LAVA)) {
        @Override protected void onContentsChanged() { markDirty(); }
    };
    private final List<FluidStack> molten = new ArrayList<>();
    private final IFluidHandler moltenHandler = new MoltenHandler();

    // ---- melting state ---------------------------------------------------------------------------------------------
    private final float[] progress = new float[MAX_SLOTS];
    private final MeltingRecipe[] cached = new MeltingRecipe[MAX_SLOTS];

    // ---- structure (server truth, synced for the screen) ------------------------------------------------------------
    private boolean formed;
    private int tier = 1;
    private int slotCount;
    private int capacity;
    private int floorArea;
    private BlockPos min = BlockPos.ZERO, max = BlockPos.ZERO;   // interior, inclusive
    private final List<BlockPos> solidHeaters = new ArrayList<>();
    private final List<BlockPos> lavaHeaters = new ArrayList<>();
    private final List<BlockPos> ducts = new ArrayList<>();
    private final List<BlockPos> drains = new ArrayList<>();
    private final List<BlockPos> linked = new ArrayList<>();
    private String statusKey = "unchecked";
    private String statusArg = "";

    // ---- heat ---------------------------------------------------------------------------------------------------------
    private float heat = AMBIENT;
    private int target = AMBIENT;
    private int burnTime, burnTotal, fuelTemp;
    private int fanRpm;
    private int fanCount;
    private boolean solidLit, lavaLit;
    private float lavaDebt;

    private int ticks;
    private int recheckIn = 1;
    private boolean dirty;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(Registry.CONTROLLER_BE.get(), pos, state);
    }

    // ================================================================================================================
    // accessors (both sides)

    public ItemStackHandler input() { return input; }
    public ItemStackHandler fuel() { return fuel; }
    public FluidTank lava() { return lava; }
    public IFluidHandler moltenHandler() { return moltenHandler; }
    public List<FluidStack> molten() { return molten; }
    public boolean isFormed() { return formed; }
    public int tier() { return tier; }
    public boolean fanRunning() { return fanRpm > 0; }
    public int fanRpm() { return fanRpm; }
    public int fanCount() { return fanCount; }

    /**
     * Client: smoke blowing up out of the open top while fans run, more and faster with more fans and higher RPM;
     * sparks and flame wisps too while the foundry burns.
     */
    /** The interior is next to the controller and no bigger than a foundry can be. */
    public boolean sane() {
        int w = max.getX() - min.getX() + 1, d = max.getZ() - min.getZ() + 1, h = max.getY() - min.getY() + 1;
        return w > 0 && d > 0 && h > 0 && w <= MAX_SIDE && d <= MAX_SIDE && h <= MAX_HEIGHT
                && min.distManhattan(worldPosition) <= 3 * MAX_SIDE + MAX_HEIGHT;
    }

    public void clientTick() {
        if (level == null || !formed || fanRpm <= 0 || fanCount <= 0 || !sane()) return;
        net.minecraft.util.RandomSource r = level.random;
        float speed = Math.min(1F, fanRpm / 256F);
        float rate = fanCount * (0.25F + 1.75F * speed);           // particles per tick
        int n = (int) rate + (r.nextFloat() < rate - (int) rate ? 1 : 0);
        double w = max.getX() - min.getX() + 1, d = max.getZ() - min.getZ() + 1;
        double top = max.getY() + 1.05;
        for (int i = 0; i < n; i++) {
            double x = min.getX() + r.nextDouble() * w, z = min.getZ() + r.nextDouble() * d;
            double up = 0.04 + 0.16 * speed + r.nextDouble() * 0.04;
            // on a Sable ship the blocks live in a far-off plot: put the particles where the ship really is
            net.minecraft.world.phys.Vec3 at = dev.empengines.tieredfoundry.compat.sable.SableCompat.toWorld(level, new net.minecraft.world.phys.Vec3(x, top, z));
            level.addParticle(burning() ? net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE
                            : net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y, at.z,
                    (r.nextDouble() - 0.5) * 0.02, up, (r.nextDouble() - 0.5) * 0.02);
            if (r.nextFloat() < 0.15F * speed) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 0, up * 0.6, 0);
            }
            if (burning() && r.nextFloat() < 0.2F * speed) {
                level.addParticle(r.nextBoolean() ? net.minecraft.core.particles.ParticleTypes.LAVA
                        : net.minecraft.core.particles.ParticleTypes.FLAME, at.x, at.y - 0.3, at.z, 0, up * 0.5, 0);
            }
        }
    }
    /** 1, 1.5 or 2. */
    public float effectiveTier() { return tier >= 2 ? 2 : fanRunning() ? 1.5F : 1; }
    public double yield() { return Config.yield(effectiveTier()); }
    public int slotCount() { return slotCount; }
    public int capacity() { return capacity; }
    public int stored() { int s = 0; for (FluidStack f : molten) s += f.getAmount(); return s; }
    public float heat() { return heat; }
    public int target() { return target; }
    public int maxHeat() { return tier >= 2 ? Config.CAP_T2.get() : Config.CAP_T1.get(); }
    public float progress(int slot) { return progress[slot]; }
    public boolean burning() { return solidLit || lavaLit; }
    public float burnFraction() { return burnTotal <= 0 ? 0 : (float) burnTime / burnTotal; }
    public boolean hasLavaHeaters() { return !lavaHeaters.isEmpty(); }
    public boolean hasSolidHeaters() { return !solidHeaters.isEmpty(); }
    public Component status() {
        return statusArg.isEmpty() ? Component.translatable("status.tieredfoundry." + statusKey)
                : Component.translatable("status.tieredfoundry." + statusKey, statusArg);
    }
    public boolean isPart(BlockPos p) { return linked.contains(p); }
    /** Inside of the foundry, inclusive corners (synced to clients for the renderer). */
    public BlockPos interiorMin() { return min; }
    public BlockPos interiorMax() { return max; }

    public MeltingRecipe melting(ItemStack stack) {
        if (level == null || stack.isEmpty()) return null;
        return level.getRecipeManager().getRecipeFor(Registry.MELTING.get(), new SingleRecipeInput(stack), level)
                .map(RecipeHolder::value).orElse(null);
    }

    private MeltingRecipe recipeIn(int slot) {
        if (cached[slot] == null) cached[slot] = melting(input.getStackInSlot(slot));
        return cached[slot];
    }

    public void recheckSoon() {
        if (recheckIn <= 0 || recheckIn > 2) recheckIn = 2;
    }

    private void markDirty() {
        dirty = true;
        setChanged();
    }

    // ================================================================================================================
    // ticking

    public void serverTick() {
        ticks++;
        if (recheckIn > 0 && --recheckIn == 0) checkStructure();
        else if (ticks % (formed ? 100 : 40) == 0) checkStructure();

        if (!formed) {
            solidLit = lavaLit = false;
            target = AMBIENT;
            approachTarget(0);
            showLit();
            sync();
            return;
        }
        if (ticks % 10 == 0) checkFans();

        boolean work = hasWork();
        burnSolid(work);
        burnLava(work);
        int base = Math.max(solidLit ? fuelTemp : 0, lavaLit ? Config.LAVA_HEAT.get() : 0);
        target = base > 0 ? Math.min(maxHeat(), base + fanBonus()) : AMBIENT;
        int lit = (solidLit ? solidHeaters.size() : 0) + (lavaLit ? lavaHeaters.size() : 0);
        approachTarget(Math.min(1F, lit / (float) Math.max(1, floorArea)));

        melt();
        if (ticks % 10 == 0 && fanRunning()) alloy();
        if (ticks % 20 == 0) updateStatus(work, lit);
        showLit();
        sync();
    }

    private boolean hasWork() {
        for (int i = 0; i < MAX_SLOTS; i++) if (!input.getStackInSlot(i).isEmpty()) return true;
        return fanRunning() && molten.size() >= 2;
    }

    private void burnSolid(boolean work) {
        solidLit = false;
        if (solidHeaters.isEmpty() || !work) return;
        if (burnTime <= 0) {
            for (int i = 0; i < FUEL_SLOTS; i++) {
                ItemStack s = fuel.getStackInSlot(i);
                int bt = s.getBurnTime(RecipeType.SMELTING);
                if (bt <= 0) continue;
                fuelTemp = fuelTemperature(s);
                burnTime = burnTotal = bt;
                ItemStack remainder = s.getCraftingRemainingItem();
                fuel.extractItem(i, 1, false);
                if (!remainder.isEmpty() && fuel.getStackInSlot(i).isEmpty()) fuel.setStackInSlot(i, remainder);
                break;
            }
        }
        if (burnTime > 0) {
            burnTime = Math.max(0, burnTime - burnSteps(solidHeaters.size()));
            solidLit = true;
            markDirty();
        }
    }

    /** Fuel and lava use: one step per {@code floorPerHeater} heaters (a 3x3 floor burns like one heater did). */
    private static int burnSteps(int heaters) {
        return Math.max(1, (int) Math.ceil(heaters / (double) Config.FLOOR_PER_HEATER.get()));
    }

    public static int fuelTemperature(ItemStack s) {
        if (s.is(Items.COAL) || s.is(Items.COAL_BLOCK)) return 1000;
        if (s.is(Items.CHARCOAL)) return 900;
        if (s.is(Items.BLAZE_ROD)) return 1100;
        return 700;
    }

    private void burnLava(boolean work) {
        lavaLit = false;
        if (lavaHeaters.isEmpty() || tier < 2 || !work || lava.getFluidAmount() <= 0) return;
        lavaDebt += Config.LAVA_USE.get() * burnSteps(lavaHeaters.size()) / 20F;
        int take = (int) lavaDebt;
        if (take > 0) {
            lavaDebt -= take;
            lava.drain(take, IFluidHandler.FluidAction.EXECUTE);
        }
        lavaLit = true;
    }

    private int fanBonus() {
        return (int) Math.min(Config.FAN_MAX.get(), fanRpm / 256.0 * Config.FAN_MAX.get());
    }

    private void checkFans() {
        int best = 0, count = 0;
        for (BlockPos d : ducts) {
            for (Direction dir : Direction.values()) {
                if (dir == Direction.UP) continue;
                if (level.getBlockEntity(d.relative(dir)) instanceof EncasedFanBlockEntity fan
                        && fan.getAirFlowDirection() == dir.getOpposite() && fan.getSpeed() != 0) {
                    best = Math.max(best, (int) Math.abs(fan.getSpeed()));
                    count++;
                }
            }
        }
        if (best != fanRpm || count != fanCount) { fanRpm = best; fanCount = count; markDirty(); }
    }

    /**
     * Degrees lost per second while the heaters aren't keeping up. Fire bricks insulate better than clay bricks, and
     * every fan blowing in keeps the air moving through the hot floor, so it holds its heat longer.
     */
    public float coolPerSecond() {
        double bricks = tier >= 2 ? Config.FIRE_BRICK_LOSS.get() : 1.0;
        return (float) (Config.COOL_RATE.get() * bricks / (1 + Config.FAN_LOSS.get() * fanCount));
    }

    private void approachTarget(float warmFactor) {
        float before = heat;
        if (heat < target) heat = Math.min(target, heat + (float) (Config.WARM_RATE.get() * warmFactor / 20.0));
        else if (heat > target) heat = Math.max(target, heat - coolPerSecond() / 20F);
        if ((int) before != (int) heat) dirty = true;
    }

    private void melt() {
        double yield = this.yield();
        double slow = Config.MELT_SLOW.get() * 20, fast = Config.MELT_FAST.get() * 20;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (input.getStackInSlot(i).isEmpty()) continue;
            MeltingRecipe r = recipeIn(i);
            if (r == null || heat < r.temperature()) continue;
            if (progress[i] < 1) {
                double over = Math.min(1, Math.max(0, (heat - r.temperature()) / 600.0));
                progress[i] = Math.min(1, progress[i] + (float) (1 / (slow + (fast - slow) * over)));
                dirty = true;
            }
            if (progress[i] >= 1) {
                FluidStack out = r.result().copyWithAmount(r.amount(yield));
                if (fill(out, false) == out.getAmount()) {
                    fill(out, true);
                    input.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }

    private void alloy() {
        for (RecipeHolder<AlloyRecipe> h : level.getRecipeManager().getAllRecipesFor(Registry.ALLOYING.get())) {
            AlloyRecipe r = h.value();
            if (heat < r.temperature()) continue;
            int times = 10;
            List<FluidStack> using = new ArrayList<>();
            int consumed = 0;
            for (SizedFluidIngredient in : r.inputs()) {
                FluidStack match = null;
                for (FluidStack f : molten) if (in.ingredient().test(f) && !using.contains(f)) { match = f; break; }
                if (match == null) { times = 0; break; }
                using.add(match);
                times = Math.min(times, match.getAmount() / in.amount());
                consumed += in.amount();
            }
            if (times <= 0) continue;
            int space = capacity - stored();
            int perRun = r.result().getAmount() - consumed;
            if (perRun > 0) times = Math.min(times, space / perRun);
            if (times <= 0) continue;
            for (int k = 0; k < using.size(); k++) using.get(k).shrink(r.inputs().get(k).amount() * times);
            molten.removeIf(FluidStack::isEmpty);
            fill(r.result().copyWithAmount(r.result().getAmount() * times), true);
        }
    }

    /** Adds molten fluid; returns how much fits. */
    public int fill(FluidStack fs, boolean execute) {
        if (fs.isEmpty()) return 0;
        int amount = Math.min(fs.getAmount(), capacity - stored());
        if (amount <= 0 || !execute) return Math.max(0, amount);
        for (FluidStack f : molten) {
            if (FluidStack.isSameFluidSameComponents(f, fs)) { f.grow(amount); markDirty(); return amount; }
        }
        molten.add(fs.copyWithAmount(amount));
        markDirty();
        return amount;
    }

    /** Moves the fluid at this index to the bottom, so drains pour it first. */
    public void moveToBottom(int index) {
        if (index <= 0 || index >= molten.size()) return;
        molten.add(0, molten.remove(index));
        markDirty();
    }

    private void updateStatus(boolean work, int lit) {
        String key = "ok", arg = "";
        if (solidHeaters.isEmpty() && lavaHeaters.isEmpty()) key = "no_heaters";
        else if (!lavaHeaters.isEmpty() && tier < 2 && solidHeaters.isEmpty()) key = "lava_needs_fire_bricks";
        else if (work && lit == 0) key = lavaHeaters.isEmpty() ? "no_fuel" : solidHeaters.isEmpty() ? "no_lava" : "no_fuel_or_lava";
        else if (stored() >= capacity && capacity > 0) key = "full";
        else {
            for (int i = 0; i < MAX_SLOTS; i++) {
                MeltingRecipe r = input.getStackInSlot(i).isEmpty() ? null : recipeIn(i);
                if (r != null && r.temperature() > maxHeat()) { key = "too_hot_for_tier"; arg = input.getStackInSlot(i).getHoverName().getString(); break; }
                if (r != null && r.temperature() > target && target > AMBIENT) { key = "needs_more_heat"; arg = input.getStackInSlot(i).getHoverName().getString(); break; }
            }
        }
        setStatus(key, arg);
    }

    private void setStatus(String key, String arg) {
        if (!key.equals(statusKey) || !arg.equals(statusArg)) {
            statusKey = key;
            statusArg = arg;
            markDirty();
        }
    }

    private boolean hasMaterial() {
        if (!molten.isEmpty()) return true;
        for (int i = 0; i < slotCount; i++) if (!input.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    private void showLit() {
        boolean material = formed && hasMaterial();
        boolean any = formed && (solidLit || lavaLit || heat > 200 || material);
        BlockState s = getBlockState();
        if (s.getValue(FoundryProps.LIT) != any) level.setBlock(worldPosition, s.setValue(FoundryProps.LIT, any), 3);
        for (BlockPos p : solidHeaters) setPartLit(p, solidLit);
        for (BlockPos p : lavaHeaters) setPartLit(p, lavaLit);
        for (BlockPos p : ducts) setPartLit(p, fanRunning());
        for (BlockPos p : drains) setPartLit(p, formed && !molten.isEmpty());
    }

    private void setPartLit(BlockPos p, boolean lit) {
        BlockState s = level.getBlockState(p);
        if (s.hasProperty(FoundryProps.LIT) && s.getValue(FoundryProps.LIT) != lit) level.setBlock(p, s.setValue(FoundryProps.LIT, lit), 3);
    }

    private void sync() {
        if (dirty && ticks % 10 == 0) {
            dirty = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // ================================================================================================================
    // structure

    private static final class Fail extends Exception {
        final String key; final BlockPos at;
        Fail(String key, BlockPos at) { super(key, null, false, false); this.key = key; this.at = at; }
    }

    private Block brick;

    private void checkStructure() {
        if (level == null || level.isClientSide) return;
        List<BlockPos> solid = new ArrayList<>(), lavaH = new ArrayList<>(), duct = new ArrayList<>(), drain = new ArrayList<>();
        brick = null;
        try {
            Direction facing = getBlockState().getValue(ControllerBlock.FACING);
            BlockPos start = worldPosition.relative(facing.getOpposite());
            if (!level.isEmptyBlock(start)) throw new Fail("no_interior", start);
            int xn = run(start, Direction.WEST), xp = run(start, Direction.EAST);
            int zn = run(start, Direction.NORTH), zp = run(start, Direction.SOUTH);
            if (xn + xp + 1 > MAX_SIDE || zn + zp + 1 > MAX_SIDE) throw new Fail("too_wide", start);
            int x1 = start.getX() - xn, x2 = start.getX() + xp, z1 = start.getZ() - zn, z2 = start.getZ() + zp;
            int y0 = start.getY();
            int bottom = y0;
            while (y0 - bottom < MAX_HEIGHT - 1 && layerAir(x1, x2, z1, z2, bottom - 1)) bottom--;
            // floor: every block under the inside is a heater
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                BlockPos p = new BlockPos(x, bottom - 1, z);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof PartBlock part && part.kind.heater()) {
                    checkFree(p);
                    (part.kind == PartBlock.Kind.SOLID_HEATER ? solid : lavaH).add(p);
                } else throw new Fail("bad_floor", p);
            }
            if (!solid.isEmpty() && !lavaH.isEmpty()) throw new Fail("mixed_heaters", lavaH.get(0));
            // the ring around the heaters: bricks, with air intakes along its sides (never at the corners)
            for (int x = x1 - 1; x <= x2 + 1; x++) for (int z = z1 - 1; z <= z2 + 1; z++) {
                if (x >= x1 && x <= x2 && z >= z1 && z <= z2) continue;
                BlockPos p = new BlockPos(x, bottom - 1, z);
                BlockState s = level.getBlockState(p);
                boolean corner = (x == x1 - 1 || x == x2 + 1) && (z == z1 - 1 || z == z2 + 1);
                if (corner) { if (!isCornerWall(s, p)) throw new Fail("bad_corner", p); }
                else if (s.getBlock() instanceof PartBlock part && part.kind == PartBlock.Kind.AIR_DUCT) { checkFree(p); duct.add(p); }
                else if (!isBrick(s, p)) throw new Fail("bad_ring", p);
            }
            int top = bottom - 1;
            for (int y = bottom; y < bottom + MAX_HEIGHT; y++) {
                if (!layerAir(x1, x2, z1, z2, y)) {
                    if (y <= y0) throw new Fail("blocked_interior", null);
                    break;
                }
                try {
                    ring(x1, x2, z1, z2, y, drain, y > y0);
                } catch (Fail f) {
                    if (y <= y0) throw f;
                    break;
                }
                top = y;
            }
            if (top < y0) throw new Fail("bad_wall", worldPosition);
            int height = top - bottom + 1;
            int volume = (x2 - x1 + 1) * (z2 - z1 + 1) * height;
            form(new BlockPos(x1, bottom, z1), new BlockPos(x2, top, z2), volume, solid, lavaH, duct, drain);
        } catch (Fail f) {
            unform(f.key, f.at);
        }
    }

    private int run(BlockPos start, Direction d) {
        int n = 0;
        BlockPos p = start.relative(d);
        while (n < MAX_SIDE && level.isEmptyBlock(p)) { n++; p = p.relative(d); }
        return n;
    }

    private boolean layerAir(int x1, int x2, int z1, int z2, int y) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) if (!level.isEmptyBlock(new BlockPos(x, y, z))) return false;
        return true;
    }

    /** Checks one wall ring. Drains found are only kept once the layer passes. */
    private void ring(int x1, int x2, int z1, int z2, int y, List<BlockPos> drains, boolean above) throws Fail {
        List<BlockPos> found = new ArrayList<>();
        Block before = brick;
        try {
            for (int x = x1 - 1; x <= x2 + 1; x++) for (int z = z1 - 1; z <= z2 + 1; z++) {
                if (x >= x1 && x <= x2 && z >= z1 && z <= z2) continue;
                BlockPos p = new BlockPos(x, y, z);
                if (p.equals(worldPosition)) continue;
                BlockState s = level.getBlockState(p);
                boolean corner = (x == x1 - 1 || x == x2 + 1) && (z == z1 - 1 || z == z2 + 1);
                if (corner) { if (!isCornerWall(s, p)) throw new Fail("bad_corner", p); }
                else if (s.getBlock() instanceof PartBlock part && part.kind == PartBlock.Kind.DRAIN) { checkFree(p); found.add(p); }
                else if (!isBrick(s, p)) throw new Fail(s.getBlock() instanceof ControllerBlock ? "two_controllers" : "bad_wall", p);
            }
        } catch (Fail f) {
            if (above) brick = before;   // an open top doesn't count towards mixing
            throw f;
        }
        drains.addAll(found);
    }

    /** Corners, top to bottom, are walls of the foundry's bricks: Brick Wall for bricks, Fire Brick Wall for fire bricks. */
    private boolean isCornerWall(BlockState s, BlockPos p) throws Fail {
        Block w = s.getBlock();
        Block b = w == Blocks.BRICK_WALL ? Blocks.BRICKS : w == Registry.FIRE_BRICK_WALL.get() ? Registry.FIRE_BRICKS.get() : null;
        return b != null && sameBricks(b, p);
    }

    private boolean isBrick(BlockState s, BlockPos p) throws Fail {
        Block b = s.getBlock();
        if (b != Blocks.BRICKS && b != Registry.FIRE_BRICKS.get()) return false;
        return sameBricks(b, p);
    }

    private boolean sameBricks(Block b, BlockPos p) throws Fail {
        if (brick == null) brick = b;
        else if (brick != b) throw new Fail("mixed_bricks", p);
        return true;
    }

    private void checkFree(BlockPos p) throws Fail {
        if (level.getBlockEntity(p) instanceof PartBlockEntity part && part.controllerPos() != null
                && !part.controllerPos().equals(worldPosition)
                && level.getBlockEntity(part.controllerPos()) instanceof ControllerBlockEntity other && other.isFormed()
                && other.isPart(p)) {
            throw new Fail("shared_part", p);
        }
    }

    private void form(BlockPos lo, BlockPos hi, int volume, List<BlockPos> solid, List<BlockPos> lavaH, List<BlockPos> duct, List<BlockPos> drain) {
        int newTier = brick == Registry.FIRE_BRICKS.get() ? 2 : 1;
        boolean changed = !formed || newTier != tier || !lo.equals(min) || !hi.equals(max)
                || !solid.equals(solidHeaters) || !lavaH.equals(lavaHeaters) || !duct.equals(ducts) || !drain.equals(drains);
        formed = true;
        tier = newTier;
        min = lo;
        max = hi;
        floorArea = (hi.getX() - lo.getX() + 1) * (hi.getZ() - lo.getZ() + 1);
        slotCount = Math.min(Config.SLOTS.get(), volume);
        capacity = volume * Config.MB_PER_BLOCK.get();
        lava.setCapacity(Math.max(1, lavaH.size()) * LAVA_PER_HEATER);
        if (!changed) return;
        List<BlockPos> all = new ArrayList<>();
        all.addAll(solid); all.addAll(lavaH); all.addAll(duct); all.addAll(drain);
        for (BlockPos p : linked) if (!all.contains(p)) unlink(p);
        solidHeaters.clear(); solidHeaters.addAll(solid);
        lavaHeaters.clear(); lavaHeaters.addAll(lavaH);
        ducts.clear(); ducts.addAll(duct);
        drains.clear(); drains.addAll(drain);
        linked.clear(); linked.addAll(all);
        for (BlockPos p : all) {
            if (level.getBlockEntity(p) instanceof PartBlockEntity part) part.link(worldPosition);
            setTier(p, tier);
        }
        setTier(worldPosition, tier);
        checkFans();
        setStatus("ok", "");
        markDirty();
        level.invalidateCapabilities(worldPosition);
    }

    private void unform(String key, BlockPos at) {
        boolean was = formed;
        formed = false;
        for (BlockPos p : linked) unlink(p);
        linked.clear();
        solidHeaters.clear(); lavaHeaters.clear(); ducts.clear(); drains.clear();
        slotCount = 0;
        fanRpm = 0;
        fanCount = 0;
        setStatus(key, at == null ? "" : at.getX() + " " + at.getY() + " " + at.getZ());
        if (was) {
            markDirty();
            level.invalidateCapabilities(worldPosition);
        }
    }

    private void unlink(BlockPos p) {
        if (level.getBlockEntity(p) instanceof PartBlockEntity part && worldPosition.equals(part.controllerPos())) part.link(null);
        setPartLit(p, false);
    }

    private void setTier(BlockPos p, int t) {
        BlockState s = level.getBlockState(p);
        if (s.hasProperty(FoundryProps.TIER) && s.getValue(FoundryProps.TIER) != t) level.setBlock(p, s.setValue(FoundryProps.TIER, t), 3);
    }

    /** Whether a block change at this position could change the structure. */
    boolean watches(BlockPos p) {
        if (!formed) return p.closerThan(worldPosition, MAX_SIDE + 2);
        return p.getX() >= min.getX() - 1 && p.getX() <= max.getX() + 1 && p.getZ() >= min.getZ() - 1 && p.getZ() <= max.getZ() + 1
                && p.getY() >= min.getY() - 1 && p.getY() <= max.getY() + 1;
    }

    // ================================================================================================================
    // lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) { Watch.ALL.add(this); recheckIn = 1; }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Watch.ALL.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        Watch.ALL.remove(this);
    }

    /** Called when the controller is broken: frees the parts. */
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (BlockPos p : linked) unlink(p);
        linked.clear();
        formed = false;
    }

    public NonNullList<ItemStack> dropList() {
        NonNullList<ItemStack> out = NonNullList.create();
        for (int i = 0; i < MAX_SLOTS; i++) if (!input.getStackInSlot(i).isEmpty()) out.add(input.getStackInSlot(i));
        for (int i = 0; i < FUEL_SLOTS; i++) if (!fuel.getStackInSlot(i).isEmpty()) out.add(fuel.getStackInSlot(i));
        return out;
    }

    // ================================================================================================================
    // saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.put("Input", input.serializeNBT(reg));
        tag.put("Fuel", fuel.serializeNBT(reg));
        tag.put("Lava", lava.writeToNBT(reg, new CompoundTag()));
        tag.putInt("LavaCap", lava.getCapacity());   // set by the structure check; the client needs it for the gauge
        ListTag fl = new ListTag();
        for (FluidStack f : molten) fl.add(f.save(reg));
        tag.put("Molten", fl);
        int[] prog = new int[MAX_SLOTS];
        for (int i = 0; i < MAX_SLOTS; i++) prog[i] = Math.round(progress[i] * 10000);
        tag.putIntArray("Progress", prog);
        tag.putFloat("Heat", heat);
        tag.putInt("Target", target);
        tag.putInt("Burn", burnTime);
        tag.putInt("BurnTotal", burnTotal);
        tag.putInt("FuelTemp", fuelTemp);
        writeSync(tag);
    }

    private void writeSync(CompoundTag tag) {
        tag.putBoolean("Formed", formed);
        tag.putInt("Tier", tier);
        tag.putInt("Slots", slotCount);
        tag.putInt("Capacity", capacity);
        tag.putInt("Fan", fanRpm);
        tag.putInt("Fans", fanCount);
        tag.putBoolean("SolidLit", solidLit);
        tag.putBoolean("LavaLit", lavaLit);
        tag.putBoolean("HasLava", !lavaHeaters.isEmpty());
        tag.putBoolean("HasSolid", !solidHeaters.isEmpty());
        tag.putString("Status", statusKey);
        tag.putString("StatusArg", statusArg);
        // relative to the controller, so the foundry survives being moved (Sable ships, Create contraptions)
        tag.putLong("MinOff", min.subtract(worldPosition).asLong());
        tag.putLong("MaxOff", max.subtract(worldPosition).asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        input.deserializeNBT(reg, tag.getCompound("Input"));
        fuel.deserializeNBT(reg, tag.getCompound("Fuel"));
        if (tag.contains("LavaCap")) lava.setCapacity(Math.max(1, tag.getInt("LavaCap")));
        lava.readFromNBT(reg, tag.getCompound("Lava"));
        molten.clear();
        for (Tag t : tag.getList("Molten", Tag.TAG_COMPOUND)) {
            FluidStack f = FluidStack.parseOptional(reg, (CompoundTag) t);
            if (!f.isEmpty()) molten.add(f);
        }
        int[] prog = tag.getIntArray("Progress");
        for (int i = 0; i < MAX_SLOTS; i++) progress[i] = i < prog.length ? prog[i] / 10000F : 0;
        java.util.Arrays.fill(cached, null);
        heat = tag.getFloat("Heat");
        target = tag.getInt("Target");
        burnTime = tag.getInt("Burn");
        burnTotal = tag.getInt("BurnTotal");
        fuelTemp = tag.getInt("FuelTemp");
        // structure flags: on the server the structure is checked again on the first tick; the client just shows them
        formed = tag.getBoolean("Formed");
        tier = Math.max(1, tag.getInt("Tier"));
        slotCount = tag.getInt("Slots");
        capacity = tag.getInt("Capacity");
        fanRpm = tag.getInt("Fan");
        fanCount = tag.getInt("Fans");
        solidLit = tag.getBoolean("SolidLit");
        lavaLit = tag.getBoolean("LavaLit");
        statusKey = tag.getString("Status").isEmpty() ? "unchecked" : tag.getString("Status");
        statusArg = tag.getString("StatusArg");
        if (tag.contains("MinOff")) {
            min = worldPosition.offset(BlockPos.of(tag.getLong("MinOff")));
            max = worldPosition.offset(BlockPos.of(tag.getLong("MaxOff")));
        } else if (tag.contains("Min")) {   // older saves: absolute, only trusted if still next to the controller
            BlockPos a = BlockPos.of(tag.getLong("Min")), b = BlockPos.of(tag.getLong("Max"));
            boolean near = a.distManhattan(worldPosition) <= 3 * MAX_SIDE + MAX_HEIGHT && b.distManhattan(worldPosition) <= 3 * MAX_SIDE + MAX_HEIGHT;
            min = near ? a : worldPosition;
            max = near ? b : worldPosition;
            if (!near) formed = false;
        }
        if (!sane()) { min = max = worldPosition; formed = false; }
        if (level != null && level.isClientSide) {
            clientHasLava = tag.getBoolean("HasLava");
            clientHasSolid = tag.getBoolean("HasSolid");
        }
    }

    private boolean clientHasLava, clientHasSolid;
    public boolean showsLava() { return level != null && level.isClientSide ? clientHasLava : hasLavaHeaters(); }
    public boolean showsSolid() { return level != null && level.isClientSide ? clientHasSolid : hasSolidHeaters(); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        return saveWithoutMetadata(reg);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ================================================================================================================
    // menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tieredfoundry.foundry_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ControllerMenu(id, inv, this);
    }

    // ================================================================================================================

    /** What drains expose: the molten fluids, bottom first. Drains only pour out; the foundry fills by melting. */
    private final class MoltenHandler implements IFluidHandler {
        @Override public int getTanks() { return Math.max(1, molten.size()); }
        @Override public FluidStack getFluidInTank(int tank) { return tank < molten.size() ? molten.get(tank) : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return capacity; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (FluidStack f : molten) if (FluidStack.isSameFluidSameComponents(f, resource)) return take(f, resource.getAmount(), action);
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return molten.isEmpty() ? FluidStack.EMPTY : take(molten.get(0), maxDrain, action);
        }

        private FluidStack take(FluidStack f, int amount, FluidAction action) {
            int n = Math.min(amount, f.getAmount());
            if (n <= 0) return FluidStack.EMPTY;
            FluidStack out = f.copyWithAmount(n);
            if (action.execute()) {
                f.shrink(n);
                if (f.isEmpty()) molten.remove(f);
                markDirty();
            }
            return out;
        }
    }

    /** Rechecks a foundry whenever a block in or around it is placed or broken. Server side only. */
    public static final class Watch {
        static final Set<ControllerBlockEntity> ALL = Collections.newSetFromMap(new WeakHashMap<>());

        public static void changed(Level level, BlockPos pos) {
            for (ControllerBlockEntity be : new ArrayList<>(ALL)) {
                if (be.level == level && !be.isRemoved() && be.watches(pos)) be.recheckSoon();
            }
        }

        @SubscribeEvent
        public static void onBreak(BlockEvent.BreakEvent e) {
            if (e.getLevel() instanceof Level l && !l.isClientSide) changed(l, e.getPos());
        }

        @SubscribeEvent
        public static void onPlace(BlockEvent.EntityPlaceEvent e) {
            if (e.getLevel() instanceof Level l && !l.isClientSide) changed(l, e.getPos());
        }

        private Watch() {}
    }
}
