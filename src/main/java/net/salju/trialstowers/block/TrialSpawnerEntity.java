package net.salju.trialstowers.block;

import net.salju.trialstowers.init.TrialsModSounds;
import net.salju.trialstowers.init.TrialsItems;
import net.salju.trialstowers.init.TrialsEffects;
import net.salju.trialstowers.init.TrialsBlockEntities;
import net.salju.trialstowers.events.TrialsManager;
import net.salju.trialstowers.TrialsMod;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Containers;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.tags.ItemTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

import java.util.List;

import java.time.temporal.ChronoField;
import java.time.LocalDate;

import com.google.common.collect.Lists;

public class TrialSpawnerEntity extends BlockEntity {
	private ItemStack egg;
	private boolean isActive;
	private String spawnLootTable = "trials:gameplay/spawner_loot";
	private int d;
	private int cd;
	private int e;
	private int k;

	public TrialSpawnerEntity(BlockPos pos, BlockState state) {
		super(TrialsBlockEntities.SPAWNER.get(), pos, state);
	}

	@Override
	public void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (this.egg != null) {
			tag.put("SpawnEgg", this.egg.save(new CompoundTag()));
		}
		tag.putBoolean("isActive", this.isActive);
		tag.putString("SpawnLootTable", this.spawnLootTable);
		tag.putInt("Difficulty", this.d);
		tag.putInt("Cooldown", this.cd);
		tag.putInt("Enemies", this.e);
		tag.putInt("Killed", this.k);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (tag.contains("SpawnEgg")) {
			ItemStack stack = ItemStack.of(tag.getCompound("SpawnEgg"));
			this.egg = stack;
		}
		this.isActive = tag.getBoolean("isActive");
		this.spawnLootTable = tag.getString("SpawnLootTable");
		this.d = tag.getInt("Difficulty");
		this.cd = tag.getInt("Cooldown");
		this.e = tag.getInt("Enemies");
		this.k = tag.getInt("Killed");
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection queen, ClientboundBlockEntityDataPacket packet) {
		if (packet != null && packet.getTag() != null) {
			if (packet.getTag().contains("SpawnEgg")) {
				ItemStack stack = ItemStack.of(packet.getTag().getCompound("SpawnEgg"));
				this.egg = stack;
			}
			this.isActive = packet.getTag().getBoolean("isActive");
			this.spawnLootTable = packet.getTag().getString("SpawnLootTable");
			this.d = packet.getTag().getInt("Difficulty");
			this.cd = packet.getTag().getInt("Cooldown");
			this.e = packet.getTag().getInt("Enemies");
			this.k = packet.getTag().getInt("Killed");
		}
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		if (this.egg != null) {
			tag.put("SpawnEgg", this.egg.save(new CompoundTag()));
		}
		tag.putBoolean("isActive", this.isActive);
		tag.putString("SpawnLootTable", this.spawnLootTable);
		tag.putInt("Difficulty", this.d);
		tag.putInt("Cooldown", this.cd);
		tag.putInt("Enemies", this.e);
		tag.putInt("Killed", this.k);
		return tag;
	}

	public static void tick(Level world, BlockPos pos, BlockState state, TrialSpawnerEntity target) {
		if (state.getBlock() instanceof TrialSpawnerBlock block && target.getSpawnType() != null && world.getDifficulty() != Difficulty.PEACEFUL) {
			target.updateBlock();
			if (world instanceof ServerLevel lvl) {
				if (block.isActive(state)) {
					lvl.sendParticles(block.isCursed(state) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, (pos.getX() + 0.5), (pos.getY() + 0.5), (pos.getZ() + 0.5), 1, 0.12, 0.12, 0.12, 0);
					if (target.isActivelySpawning()) {
						if (target.getRemainingEnemies() != 0) {
							checkPlayer(world, pos, state, target);
							if (target.getTotalEnemies() > 0) {
								if (target.getCd() != 0) {
									target.setCd(target.getCd() - 1);
								} else if (target.getRemainingEnemies() == target.getTotalEnemies()) {
									target.setCd(80);
									int e = (target.getDifficulty() <= 1 ? 4 : 3);
									if (e > target.getTotalEnemies()) {
										e = target.getTotalEnemies();
									}
									target.setTotalEnemies(target.getTotalEnemies() - e);
									lvl.playSound(null, pos, TrialsModSounds.SPAWNER_SUMMON.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
									for (int i = 0; i != e; ++i) {
										setUpMob(target.getSpawnType().create(lvl), lvl, target.getDifficulty(), target.findSpawnPositionNear(target.getSpawnType(), lvl, pos.above(), 2, world.getRandom()));
									}
								}
							}
						} else if (block.isEjecting(state)) {
							if (target.getCd() != 0) {
								target.setCd(target.getCd() - 1);
							} else {
								target.setActivity(false);
								target.setCd(24000);
								target.setDifficulty(Mth.nextInt(world.getRandom(), 2, 100));
								lvl.playSound(null, pos, TrialsModSounds.SPAWNER_CLOSE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
								world.setBlock(pos, state.setValue(TrialSpawnerBlock.ACTIVE, Boolean.valueOf(false)).setValue(TrialSpawnerBlock.EJECT, Boolean.valueOf(false)).setValue(TrialSpawnerBlock.CURSED, Boolean.valueOf(false)), 3);
							}
						} else {
							target.setCd(40);
							TrialsMod.queueServerWork(20, () -> {
								lvl.playSound(null, pos, TrialsModSounds.SPAWNER_ITEM.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
								if (block.isCursed(state)) {
									Containers.dropItemStack(world, pos.getX(), (pos.getY() + 1.0), pos.getZ(), new ItemStack(TrialsItems.TRIAL_KEY.get()));
								}
								for (ItemStack stack : TrialsManager.getLoot(target, world, target.getSpawnLootTable())) {
									Containers.dropItemStack(world, pos.getX(), (pos.getY() + 1.0), pos.getZ(), stack);
								}

							});
							lvl.playSound(null, pos, TrialsModSounds.SPAWNER_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
							world.setBlock(pos, state.setValue(TrialSpawnerBlock.EJECT, Boolean.valueOf(true)), 3);
						}
					} else {
						Player player = lvl.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 5, false);
						if (player != null && !player.isCreative() && !player.isSpectator()) {
							int i = 0;
							if (player.hasEffect(MobEffects.BAD_OMEN)) {
								player.addEffect(new MobEffectInstance(TrialsEffects.CURSED.get(), 12000, player.getEffect(MobEffects.BAD_OMEN).getAmplifier()));
								player.removeEffect(MobEffects.BAD_OMEN);
							}
							for (Player players : lvl.getPlayers(LivingEntity::isAlive)) {
								if (players.isCloseEnough(player, 32)) {
									i++;
								}
							}
							lvl.playSound(null, pos, TrialsModSounds.SPAWNER_DETECT_PLAYER.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
							if (player.hasEffect(TrialsEffects.CURSED.get())) {
								target.setDifficulty(1);
								i = (i + (2 * (player.getEffect(TrialsEffects.CURSED.get()).getAmplifier() + 1)));
								world.setBlock(pos, state.setValue(TrialSpawnerBlock.CURSED, Boolean.valueOf(true)), 3);
								lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX(), (pos.getY() + 1.0), pos.getZ(), 8, 0.1, 0.1, 0.1, 0);
								lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, (pos.getX() + 1.0), (pos.getY() + 1.0), pos.getZ(), 8, 0.1, 0.1, 0.1, 0);
								lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, (pos.getX() + 1.0), (pos.getY() + 1.0), (pos.getZ() + 1.0), 8, 0.1, 0.1, 0.1, 0);
								lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX(), (pos.getY() + 1.0), (pos.getZ() + 1.0), 8, 0.1, 0.1, 0.1, 0);
							} else {
								lvl.sendParticles(ParticleTypes.FLAME, pos.getX(), (pos.getY() + 1.0), pos.getZ(), 8, 0.1, 0.1, 0.1, 0);
								lvl.sendParticles(ParticleTypes.FLAME, (pos.getX() + 1.0), (pos.getY() + 1.0), pos.getZ(), 8, 0.1, 0.1, 0.1, 0);
								lvl.sendParticles(ParticleTypes.FLAME, (pos.getX() + 1.0), (pos.getY() + 1.0), (pos.getZ() + 1.0), 8, 0.1, 0.1, 0.1, 0);
								lvl.sendParticles(ParticleTypes.FLAME, pos.getX(), (pos.getY() + 1.0), (pos.getZ() + 1.0), 8, 0.1, 0.1, 0.1, 0);
							}
							target.setActivity(true);
							target.setTotalEnemies(8 + (i * 2));
							target.setRemainingEnemies(8 + (i * 2));
							target.setCd(20);
						}
					}
				} else {
					lvl.sendParticles(ParticleTypes.SMOKE, (pos.getX() + 0.5), (pos.getY() + 0.95), (pos.getZ() + 0.5), 1, 0.12, 0.12, 0.12, 0);
					if (target.getCd() != 0) {
						target.setCd(target.getCd() - 1);
					} else {
						world.setBlock(pos, state.setValue(TrialSpawnerBlock.ACTIVE, Boolean.valueOf(true)), 3);
					}
				}
			}
		}
	}

	public static void checkPlayer(Level world, BlockPos pos, BlockState state, TrialSpawnerEntity target) {
		if (world instanceof ServerLevel lvl) {
			Player player = lvl.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 64, false);
			if (player == null) {
				target.setActivity(false);
				target.setRemainingEnemies(0);
				target.setTotalEnemies(0);
				target.setCd(12000);
				target.setDifficulty(Mth.nextInt(world.getRandom(), 2, 100));
				world.setBlock(pos, state.setValue(TrialSpawnerBlock.ACTIVE, Boolean.valueOf(false)).setValue(TrialSpawnerBlock.CURSED, Boolean.valueOf(false)), 3);
			}
		}
	}

	public static void setUpMob(Entity target, ServerLevel lvl, int i, BlockPos pos) {
		lvl.sendParticles(i <= 1 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, pos.getX(), (pos.getY() + 1.05), pos.getZ(), 12, 0.45, 0.25, 0.45, 0);
		target.moveTo(Vec3.atBottomCenterOf(pos));
		if (target instanceof Mob mobster) {
			mobster.setCanPickUpLoot(false);
			mobster.setPersistenceRequired();
			mobster.getPersistentData().putInt("TrialSpawned", i);
			mobster.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			mobster.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
			mobster.setDropChance(EquipmentSlot.HEAD, 0.0F);
			mobster.setDropChance(EquipmentSlot.CHEST, 0.0F);
			mobster.setDropChance(EquipmentSlot.LEGS, 0.0F);
			mobster.setDropChance(EquipmentSlot.FEET, 0.0F);
			if (canWearArmor(mobster) && i < 45) {
				if (LocalDate.now().get(ChronoField.DAY_OF_MONTH) == 31 && LocalDate.now().get(ChronoField.MONTH_OF_YEAR) == 10) {
					mobster.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Math.random() <= 0.1 ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
				} else if (Math.random() >= 0.25) {
					mobster.setItemSlot(EquipmentSlot.HEAD, new ItemStack(i <= 1 ? Items.DIAMOND_HELMET : Items.IRON_HELMET));
				}
				if (Math.random() >= 0.65) {
					mobster.setItemSlot(EquipmentSlot.CHEST, new ItemStack(i <= 1 ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE));
				}
				if (Math.random() >= 0.54) {
					mobster.setItemSlot(EquipmentSlot.LEGS, new ItemStack(i <= 1 ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS));
				}
				if (Math.random() >= 0.45) {
					mobster.setItemSlot(EquipmentSlot.FEET, new ItemStack(i <= 1 ? Items.DIAMOND_BOOTS : Items.IRON_BOOTS));
				}
				Holder.Reference<TrimMaterial> m = getTrim(lvl);
				for (ItemStack armor : mobster.getArmorSlots()) {
					if (armor.is(ItemTags.TRIMMABLE_ARMOR)) {
						ArmorTrim.setTrim(lvl.registryAccess(), armor, new ArmorTrim(m, TrimPatterns.getFromTemplate(lvl.registryAccess(), new ItemStack(i <= 1 ? TrialsItems.FLOW_TEMPLATE.get() : TrialsItems.BOLT_TEMPLATE.get())).get()));
					}
				}
			}
		}
		if (target instanceof Zombie billy) {
			if ((i > 76 || i < 21) && Math.random() >= 0.45) {
				billy.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			}
			if ((i > 96 || i < 6) && Math.random() >= 0.65) {
				billy.setBaby(true);
			}
			if (i <= 1) {
				if (billy.getType() == EntityType.DROWNED && Math.random() >= 0.45) {
					billy.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TRIDENT));
				} else if (Math.random() >= 0.65) {
					billy.addEffect(new MobEffectInstance(TrialsEffects.OOZE.get(), 12000, 0));
				}
			}
		} else if (target instanceof AbstractSkeleton kevin) {
			kevin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
			if (i <= 1) {
				if (Math.random() >= 0.65) {
					kevin.addEffect(new MobEffectInstance(TrialsEffects.INFESTED.get(), 12000, 0));
				}
			}
		} else if (target instanceof Spider spy) {
			if (i <= 1) {
				spy.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 12000, 0));
				spy.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 12000, 0));
				spy.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 12000, 0));
				spy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12000, 0));
			} else if (i > 90) {
				spy.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 12000, 0));
			} else if (i > 75) {
				spy.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 12000, 0));
			} else if (i < 10) {
				spy.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 12000, 0));
			} else if (i < 25) {
				spy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12000, 0));
			}
		} else if (target instanceof Pillager crossbow) {
			((GroundPathNavigation) crossbow.getNavigation()).setCanOpenDoors(true);
			if (i <= 1) {
				crossbow.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 12000, 0));
				crossbow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12000, 0));
				if (Math.random() >= 0.65) {
					crossbow.addEffect(new MobEffectInstance(TrialsEffects.WEAVE.get(), 12000, 0));
				}
			} else if (i > 75) {
				crossbow.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 12000, 0));
			} else if (i < 45) {
				crossbow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12000, 0));
			}
			crossbow.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
		} else if (target instanceof Mob mobster) {
			mobster.finalizeSpawn(lvl, new DifficultyInstance(lvl.getDifficulty(), 5L, 5L, 1.0F), MobSpawnType.SPAWNER, null, null);
		}
		lvl.addFreshEntity(target);
	}

	public static boolean canWearArmor(Entity target) {
		return (target instanceof Zombie || target instanceof AbstractSkeleton);
	}

	public static Holder.Reference<TrimMaterial> getTrim(ServerLevel lvl) {
		int i = Mth.nextInt(lvl.getRandom(), 0, 7);
		List<Item> list = getMats();
		return TrimMaterials.getFromIngredient(lvl.registryAccess(), new ItemStack(list.get(i))).get();
	}

	private static List<Item> getMats() {
		List<Item> list = Lists.newArrayList();
		list.add(Items.COPPER_INGOT);
		list.add(Items.IRON_INGOT);
		list.add(Items.GOLD_INGOT);
		list.add(Items.LAPIS_LAZULI);
		list.add(Items.REDSTONE);
		list.add(Items.AMETHYST_SHARD);
		list.add(Items.EMERALD);
		list.add(Items.DIAMOND);
		return list;
	}

	@Nullable
	public EntityType<?> getSpawnType() {
		if (this.egg != null && this.egg.getItem() instanceof SpawnEggItem item) {
			return item.getType(this.egg.getTag());
		}
		return null;
	}

	public BlockPos findSpawnPositionNear(EntityType<?> type, ServerLevel lvl, BlockPos old, int range, RandomSource random) {
		BlockPos pos = old;
		for (int i = 0; i < 25; ++i) {
			int x = old.getX() + random.nextInt(range * 2) - range;
			int y = old.getY() + random.nextInt(range * 2) - range;
			int z = old.getZ() + random.nextInt(range * 2) - range;
			BlockPos test = new BlockPos(x, y, z);
			if (lvl.noCollision(type.getAABB(test.getX(), test.getY(), test.getZ()))) {
				pos = test;
				break;
			}
		}
		return pos;
	}

	public void setEgg(ItemStack stack) {
		this.egg = stack;
	}

	public boolean isActivelySpawning() {
		return this.isActive;
	}

	public int getDifficulty() {
		return this.d;
	}

	public int getCd() {
		return this.cd;
	}

	public int getTotalEnemies() {
		return this.e;
	}

	public int getRemainingEnemies() {
		return this.k;
	}
	public String getSpawnLootTable() {
		return this.spawnLootTable;
	}

	public void setActivity(boolean check) {
		this.isActive = check;
	}

	public void setDifficulty(int i) {
		this.d = i;
	}

	public void setCd(int i) {
		this.cd = i;
	}

	public void setTotalEnemies(int i) {
		this.e = i;
	}

	public void setRemainingEnemies(int i) {
		this.k = i;
	}
	public void setSpawnLootTable(String i) {
		this.spawnLootTable = i;
	}
	public void updateBlock() {
		this.setChanged();
		this.getLevel().updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
		this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
	}
}
