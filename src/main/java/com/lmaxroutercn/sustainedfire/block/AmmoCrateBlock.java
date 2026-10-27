package com.lmaxroutercn.sustainedfire.block;

import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
import com.lmaxroutercn.sustainedfire.item.AmmoCrateBlockItem;
import com.lmaxroutercn.sustainedfire.menu.AmmoCrateMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药箱方块（里程碑 6）：放置式供弹容器。
 *
 * 交互设计（max 定稿）：
 *   shift+右键 —— 纯开关盖（不弹 GUI）
 *   关盖右键   —— 开盖（防呆：先开盖再谈取弹）
 *   开盖右键   —— 打开 27 格存储 GUI
 *   漏斗       —— 不受盖子限制（Forge capability 自动互通）
 *
 * 盖子开合是 blockstate 布尔属性（随区块持久化），不需要 BE 字段。
 * 静态双模型（closed/open），max 的 Blockbench 成品覆盖同名 json 即生效。
 */
public class AmmoCrateBlock extends Block implements EntityBlock {
    /** 盖子开合状态（复用原版 OPEN 属性，blockstate json 键为 open=true/false） */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public AmmoCrateBlock(Properties properties) {
        super(properties);
        // 默认关盖放置
        this.registerDefaultState(this.stateDefinition.any().setValue(OPEN, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmmoCrateBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 客户端仅反馈"已处理"让手臂挥动，逻辑全部在服务端
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            // shift+右键：纯开关盖
            setLid(level, pos, state, !state.getValue(OPEN));
            return InteractionResult.CONSUME;
        }
        if (!state.getValue(OPEN)) {
            // 关盖右键：开盖（防呆），不弹 GUI
            setLid(level, pos, state, true);
            return InteractionResult.CONSUME;
        }
        // 开盖右键：打开 27 格存储 GUI
        // 注：Player.openMenu(MenuProvider, BlockPos) 两参重载在 1.20.1 Forge 解析失败（踩坑实录），
        // 改用 NetworkHooks.openScreen —— pos 自动写入同步缓冲，客户端工厂 readBlockPos 对上
        if (level.getBlockEntity(pos) instanceof AmmoCrateBlockEntity be && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inv, p) -> new AmmoCrateMenu(id, inv, be),
                    Component.translatable("container.sustainedfire.ammo_crate")), pos);
        }
        return InteractionResult.CONSUME;
    }

    /** 切换盖子开合：改 blockstate + 桶音效占位（专属音效后续可换） */
    public static void setLid(Level level, BlockPos pos, BlockState state, boolean open) {
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, pos, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    // ---- 内容随物品（潜影盒语义，max 2026-10-28 拍板） ----
    // 覆盖 loot table 路径：生存挖掘/爆炸/活塞推毁统一掉落携带 BlockEntityTag 的物品，
    // 放置时 vanilla 按 "BlockEntityTag" 标准机制自动恢复内容。
    // 创造破坏镜像原版潜影盒：不掉物品。本覆盖不查 loot table，故无需 loot json。
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack stack = new ItemStack(this);
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof AmmoCrateBlockEntity crate) {
            // 与 vanilla ShulkerBoxBlock.getDrops 同构：全量存档去 id 后随物品
            CompoundTag beTag = crate.saveWithFullMetadata();
            beTag.remove("id");
            stack.getOrCreateTag().put("BlockEntityTag", beTag);
            // 占用槽数缓存：耐久条与物品模型谓词的 O(1) 数据源
            stack.getOrCreateTag().putInt(AmmoCrateBlockItem.TAG_FILL, crate.getOccupiedSlots());
        }
        return List.of(stack);
    }
}