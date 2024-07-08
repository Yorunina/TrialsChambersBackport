package net.salju.trialstowers.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

public class TuffLightBlock extends Block {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public TuffLightBlock(BlockBehaviour.Properties props) {
		super(props);
		this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(POWERED);
		builder.add(LIT);
	}

	@Override
	public void onPlace(BlockState state, Level world, BlockPos pos, BlockState newState, boolean check) {
		super.onPlace(state, world, pos, newState, check);
		if (!newState.is(state.getBlock())) {
			if (!world.isClientSide) {
				world.scheduleTick(pos, this, 1);
			}
		}
	}

	@Override
	public void neighborChanged(BlockState state, Level world, BlockPos pos, Block blok, BlockPos poz, boolean check) {
		super.neighborChanged(state, world, pos, blok, poz, check);
		if (!world.isClientSide()) {
			world.scheduleTick(pos, this, 1);
		}
	}

	@Override
	public void tick(BlockState state, ServerLevel lvl, BlockPos pos, RandomSource rng) {
		super.tick(state, lvl, pos, rng);
		if (state.getValue(POWERED) && !lvl.hasNeighborSignal(pos)) {
			lvl.setBlock(pos, state.setValue(POWERED, false), 2);
		} else if (lvl.hasNeighborSignal(pos) && !state.getValue(POWERED)) {
			if (state.getValue(LIT)) {
				lvl.setBlock(pos, state.setValue(LIT, false).setValue(POWERED, true), 2);
			} else {
				lvl.setBlock(pos, state.setValue(LIT, true).setValue(POWERED, true), 2);
			}
		}
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
		return state.getValue(LIT) ? 15 : 0;
	}
}