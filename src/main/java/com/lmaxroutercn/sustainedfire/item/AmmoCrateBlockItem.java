package com.lmaxroutercn.sustainedfire.item;

import com.lmaxroutercn.sustainedfire.block.AmmoCrateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
import com.lmaxroutercn.sustainedfire.util.FixedCapacityItemStackHandler;
import com.tacz.guns.api.item.IAmmo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹药箱方块物品（里程碑 6 物品侧，max 2026-10-28 拍板）：
 *
 * 1. 内容随物品：方块侧 AmmoCrateBlock.getDrops 已覆盖 loot 路径，
 *    掉落物品携带 BlockEntityTag，放置时 vanilla 自动恢复（潜影盒语义）。
 * 2. 背包光标交互（对齐 tacz 弹药盒习惯）：手持弹药箱右键弹药格整组收入，
 *    右键空格取出箱内第一组合法弹药。
 * 3. 耐久条 = 已占用槽位 / 36（空箱隐藏，绿色，对齐 tacz 弹药盒观感）。
 * 4. "sf:fill" 整数 NBT：挖掘捕获与光标交互时写入的占用槽数缓存，
 *    供耐久条与物品模型谓词 sustainedfire:open 做 O(1) 读取，避免每帧反序列化 36 格。
 */
public class AmmoCrateBlockItem extends BlockItem {
    /** 占用槽数缓存键（挖掘捕获与光标交互时写入） */
    public static final String TAG_FILL = "sf:fill";

    public AmmoCrateBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    // ---------- 耐久条：已占槽位 / 27 ----------

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // 空箱不显示条（对齐 tacz 弹药盒：无弹药时无条）
        return getFill(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        // 13 = vanilla 耐久条满宽像素
        return Math.round(13f * getFill(stack) / (float) AmmoCrateBlockEntity.CONTAINER_SIZE);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // 绿色（对齐 tacz 弹药盒条色）
        return 0x55FF55;
    }

    /** O(1) 读取占用槽数（无 NBT 视为空箱） */
    public static int getFill(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getInt(TAG_FILL) : 0;
    }

    // ---------- 背包光标交互（对齐 tacz 弹药盒） ----------

    @Override
    public boolean overrideStackedOnOther(ItemStack crateStack, Slot slot, ClickAction action, Player player) {
        // 只处理右键
        if (action != ClickAction.SECONDARY) {
            return false;
        }
        ItemStack slotItem = slot.getItem();
        if (slotItem.isEmpty()) {
            // 空格：从箱内取出第一组合法弹药（顶向下）
            return takeAmmoFromCrate(crateStack, slot, player);
        }
        if (isDepositable(slotItem)) {
            // 弹药格：整组收入（合堆优先，装不下留余在原格）
            return depositIntoCrate(crateStack, slot, slotItem, player);
        }
        return false;
    }

    /**
     * 供弹对象谓词：光标交互只认 TACZ 弹药（IAmmo）。
     * [max 拍板] 箱体 GUI/漏斗仍为通用容器；未来扩展收纳范围只改此方法。
     */
    private static boolean isDepositable(ItemStack stack) {
        return stack.getItem() instanceof IAmmo;
    }

    /** 取出：顶向下第一组合法弹药，整组（上限一堆）放入被点空格 */
    private static boolean takeAmmoFromCrate(ItemStack crateStack, Slot slot, Player player) {
        ItemStackHandler handler = loadHandler(crateStack);
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack ammo = handler.getStackInSlot(i);
            if (ammo.isEmpty() || !isDepositable(ammo)) {
                continue;
            }
            ItemStack taken = handler.extractItem(i, ammo.getMaxStackSize(), false);
            // 被点格是空的，safeInsert 必然成功；leftover 兜底塞回防丢失
            ItemStack leftover = slot.safeInsert(taken);
            if (!leftover.isEmpty()) {
                insertAll(handler, leftover);
            }
            playRemoveOneSound(player);
            saveHandler(crateStack, handler);
            return true;
        }
        return false;
    }

    /** 存入：整组合堆收入，剩余（箱满）留在原格 */
    private static boolean depositIntoCrate(ItemStack crateStack, Slot slot, ItemStack slotItem, Player player) {
        ItemStackHandler handler = loadHandler(crateStack);
        ItemStack remainder = insertAll(handler, slotItem.copy());
        if (remainder.getCount() == slotItem.getCount()) {
            // 一发都没装进去：箱满/无堆位，不消费
            return false;
        }
        slot.set(remainder);
        playInsertSound(player);
        saveHandler(crateStack, handler);
        return true;
    }

    // ---------- 物品 NBT <-> 36 格容器 ----------

    /** 从物品 BlockEntityTag.Items 恢复一个 36 格容器视图 */
    private static ItemStackHandler loadHandler(ItemStack stack) {
        ItemStackHandler handler = new FixedCapacityItemStackHandler(AmmoCrateBlockEntity.CONTAINER_SIZE);
        CompoundTag beTag = stack.getTagElement("BlockEntityTag");
        if (beTag != null) {
            // 与 AmmoCrateBlockEntity.saveAdditional 写入的 Items 键同构
            handler.deserializeNBT(beTag.getCompound("Items"));
        }
        return handler;
    }

    /** 写回物品 NBT 并同步 sf:fill 缓存 */
    private static void saveHandler(ItemStack stack, ItemStackHandler handler) {
        stack.getOrCreateTagElement("BlockEntityTag").put("Items", handler.serializeNBT());
        stack.getOrCreateTag().putInt(TAG_FILL, countOccupied(handler));
    }

    /** 统计占用槽数（耐久条/模型谓词的分子） */
    private static int countOccupied(ItemStackHandler handler) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** 通用合堆插入：先同 ID 同 NBT 合堆，再进空位 */
    private static ItemStack insertAll(ItemStackHandler handler, ItemStack stack) {
        for (int i = 0; i < handler.getSlots() && !stack.isEmpty(); i++) {
            stack = handler.insertItem(i, stack, false);
        }
        return stack;
    }

    private static void playInsertSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playRemoveOneSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }
    /** tooltip 内容行上限：超出聚合为"还有 N 种"，防 36 格全异类撑爆悬浮框 */
    private static final int TOOLTIP_MAX_LINES = 8;

    // ---------- tooltip：内容物聚合（max 需求：仿 tacz 弹药盒显示内容；我们是混合容器，扩展为按物品聚合的文本行） ----------
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return; // 空箱：不追加任何行
        }
        // [M7e] "Items" 在 BlockEntityTag 下是 ItemStackHandler.serializeNBT() 的包装复合标签
        // {Items:[...], Size:N}，并非裸物品列表——直接 getList 对复合标签恒返回空表，
        // 内容聚合行自 M7a 起从未显示过（写侧/读侧层级假设不一致，静默失效不报错）。
        // 修法与 loadHandler 对齐：先取包装层 getCompound("Items")，再取内层真正的列表。
        ListTag items = tag.getCompound("BlockEntityTag").getCompound("Items").getList("Items", Tag.TAG_COMPOUND);
        if (items.isEmpty()) {
            return;
        }
        // 按 item 注册名聚合数量（LinkedHashMap 保序，显示稳定不闪烁）
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        Map<ResourceLocation, Component> names = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = ItemStack.of(items.getCompound(i));
            if (s.isEmpty()) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
            counts.merge(id, s.getCount(), Integer::sum);
            names.putIfAbsent(id, s.getHoverName());
        }
        int shown = 0;
        for (Map.Entry<ResourceLocation, Integer> e : counts.entrySet()) {
            if (shown++ >= TOOLTIP_MAX_LINES) {
                components.add(Component.translatable("tooltip.sustainedfire.ammo_crate.more", counts.size() - TOOLTIP_MAX_LINES).withStyle(ChatFormatting.GRAY));
                return;
            }
            components.add(Component.literal("  ").append(names.get(e.getKey())).append(" ×" + e.getValue()).withStyle(ChatFormatting.GRAY));
        }
    }
    // [M9] 放置兜底：BlockEntityTag 恢复内容不经 onContentsChanged（be.load 直写槽位），
    // level blockstate 不跟随——place 返回时内容已就绪，读 BE 实时统计幂等校正（已对齐则零开销）。
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (result.consumesAction()) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            if (level.getBlockEntity(pos) instanceof AmmoCrateBlockEntity crate) {
                BlockState state = level.getBlockState(pos);
                int newLevel = crate.contentLevel();
                if (state.getValue(AmmoCrateBlock.LEVEL) != newLevel) {
                    level.setBlock(pos, state.setValue(AmmoCrateBlock.LEVEL, newLevel), Block.UPDATE_CLIENTS);
                }
            }
        }
        return result;
    }
}
