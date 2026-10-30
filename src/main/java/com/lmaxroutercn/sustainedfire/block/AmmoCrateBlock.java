package com.lmaxroutercn.sustainedfire.block;

import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
import com.lmaxroutercn.sustainedfire.item.AmmoCrateBlockItem;
import com.lmaxroutercn.sustainedfire.menu.AmmoCrateMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药箱方块（里程碑 6）：放置式供弹容器。
 *
 * 交互设计（max 定稿）：
 *   shift+右键 —— 纯开关盖（不弹 GUI）
 *   关盖右键   —— 开盖（防呆：先开盖再谈取弹）
 *   开盖右键   —— 打开 36 格存储 GUI
 *   漏斗       —— 不受盖子限制（Forge capability 自动互通）
 *
 * 盖子开合是 blockstate 布尔属性（随区块持久化），不需要 BE 字段。
 * 静态双模型（closed/open）。2026-10-28 建模冻结（max 拍板）：手拼 JSON 即最终版——
 * 箱体挖空留壁（壁 2px / 底 2px，内腔 12x10x12），后续美术仅换贴图（同名 PNG 覆盖）。
 */
public class AmmoCrateBlock extends Block implements EntityBlock {
    /** 盖子开合状态（复用原版 OPEN 属性，blockstate json 键为 open=true/false） */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** [M9] 水平朝向：放置时取玩家视线方向，盖子立起的一边在玩家对侧（blockstate y 旋转，模型文件不动） */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** [M9] 内容物等级（堆肥桶粒度）：0-8 = 内腔填充从无到与开口齐平，由 BE 占用槽数映射（AmmoCrateBlockEntity.contentLevel） */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 8);

    public AmmoCrateBlock(Properties properties) {
        super(properties);
        // 默认关盖放置
        this.registerDefaultState(this.stateDefinition.any().setValue(OPEN, Boolean.FALSE).setValue(FACING, Direction.NORTH).setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, FACING, LEVEL);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmmoCrateBlockEntity(pos, state);
    }

    /** [M9] 朝向随玩家视线：盖子立边落在玩家对面（玩家看北 → FACING=NORTH → 盖立北缘，开口正对玩家）。
     * 取 Entity.getDirection（玩家眼看方向，语义无歧义）；无玩家上下文（发射器放置等）退 north。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getPlayer() != null ? context.getPlayer().getDirection() : Direction.NORTH;
        return this.defaultBlockState().setValue(FACING, look);
    }

    // ---- 碰撞/选择形状（M8 挖空箱体）：底 + 四壁为共通箱体，按开合盖分派 ----
    /** 箱体（底板 + 四壁）：closed/open 共用，与模型 element 几何一一对应 */
    private static final VoxelShape SHAPE_BODY = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),    // 底板
            Block.box(0, 2, 0, 2, 12, 16),   // 西壁
            Block.box(14, 2, 0, 16, 12, 16), // 东壁
            Block.box(2, 2, 0, 14, 12, 2),   // 北壁
            Block.box(2, 2, 14, 14, 12, 16)  // 南壁
    );
    /** 关盖：箱体 + 平盖 */
    private static final VoxelShape SHAPE_CLOSED = Shapes.or(SHAPE_BODY, Block.box(1, 12, 1, 15, 14, 15));
    /** 开盖：箱体 + 立起的翻盖（M9 随朝向旋转，与 blockstate y 旋转一一对应——盖立边在 FACING 侧） */
    private static final VoxelShape SHAPE_OPEN_N = Shapes.or(SHAPE_BODY, Block.box(1, 12, 1, 15, 15, 3));
    private static final VoxelShape SHAPE_OPEN_E = Shapes.or(SHAPE_BODY, Block.box(13, 12, 1, 15, 15, 15));
    private static final VoxelShape SHAPE_OPEN_S = Shapes.or(SHAPE_BODY, Block.box(1, 12, 13, 15, 15, 15));
    private static final VoxelShape SHAPE_OPEN_W = Shapes.or(SHAPE_BODY, Block.box(1, 12, 1, 3, 15, 15));

    /** [M9] 开盖碰撞全矩阵 [dirIdx][level]：4 朝向 × 9 级（0=空，1-8 含内容物），类加载预烘焙，getShape 查表 O(1) 零运行时分配 */
    private static final VoxelShape[][] SHAPE_OPEN_ALL = new VoxelShape[4][9];
    static {
        VoxelShape[] bases = {SHAPE_OPEN_N, SHAPE_OPEN_E, SHAPE_OPEN_S, SHAPE_OPEN_W};
        for (int d = 0; d < 4; d++) {
            SHAPE_OPEN_ALL[d][0] = bases[d];
            for (int lv = 1; lv <= 8; lv++) {
                SHAPE_OPEN_ALL[d][lv] = Shapes.or(bases[d], contentShape(lv));
            }
        }
    }

    /** [M9 挡] 内容物碰撞盒（四向对称）：与视觉模型同几何 [2,2,2]-[14, 2+lv×1.25, 14]，
     *  小数高度用 Shapes.box 的 double 重载（Block.box 是其 int 包装） */
    private static VoxelShape contentShape(int lv) {
        return Shapes.box(2 / 16.0, 2 / 16.0, 2 / 16.0, 14 / 16.0, (2 + lv * 1.25) / 16.0, 14 / 16.0);
    }

    /** [M9] 朝向 → 矩阵行索引（N=0/E=1/S=2/W=3，与 bases 数组同序） */
    private static int dirIndex(Direction dir) {
        return switch (dir) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0; // NORTH
        };
    }

    /** 挖空箱体不满格：选择/碰撞/导航均按实际几何，开放腔口可被准确选中 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(OPEN)) {
            return SHAPE_CLOSED; // 平盖四向对称，无需旋转
        }
        // 翻盖立边在 FACING 侧（与 blockstate y 旋转同构：N=0/E=90/S=180/W=270）；内容物四向对称，按级查表
        return SHAPE_OPEN_ALL[dirIndex(state.getValue(FACING))][state.getValue(LEVEL)];
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
        // 开盖右键：打开 36 格存储 GUI
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

    // ---- 内容随物品（潜影盒语义）→ 走数据包 loot 表 [M12.3，max 2026-10-30 拍板] ----
    // 删除 getDrops 代码覆盖：生存挖掘/爆炸/活塞推毁统一走 loot_tables/blocks/ammo_crate.json
    // （copy_nbt：Items→BlockEntityTag.Items、Fill→sf:fill；放置 vanilla 按 BlockEntityTag 自动恢复）。
    // 表可被任意 datapack 整表 override——掉落规则从此玩家可配置，代码不再垄断。
    // 行为差异：爆炸从[代码版必掉]变为[vanilla 概率幸存]（barrel 同款语义）。
    /** 创造中键取箱带内容（与 loot 表同语义；中键克隆是 vanilla clone 语义不走 loot 表，保留代码实现） */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        // 客户端 BE 的 Items 经 getUpdateTag 同步过，此处读取即同步视图
        if (level.getBlockEntity(pos) instanceof AmmoCrateBlockEntity crate) {
            CompoundTag beTag = crate.saveWithFullMetadata();
            beTag.remove("id");
            stack.getOrCreateTag().put("BlockEntityTag", beTag);
            stack.getOrCreateTag().putInt(AmmoCrateBlockItem.TAG_FILL, crate.getOccupiedSlots());
        }
        return stack;
    }
}
